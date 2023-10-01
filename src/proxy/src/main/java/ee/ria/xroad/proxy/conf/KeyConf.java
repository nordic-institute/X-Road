/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.proxy.conf;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.conf.globalconf.AuthKey;
import ee.ria.xroad.common.identifier.ClientId;

import org.bouncycastle.cert.ocsp.OCSPResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import static ee.ria.xroad.common.ErrorCodes.X_CANNOT_CREATE_SIGNATURE;
import static ee.ria.xroad.common.ErrorCodes.X_MALFORMED_KEYCONF;
import static ee.ria.xroad.common.ErrorCodes.translateWithPrefix;
import static ee.ria.xroad.common.util.CryptoUtils.calculateCertHexHash;

/**
 * Static class for accessing Key Configuration.
 */
public final class KeyConf {

    private static final Logger LOG = LoggerFactory.getLogger(KeyConf.class);

    private static volatile KeyConfProvider instance = null;

    // Holds the potential initialization error that might occur when
    // (re)loading a configuration.
    // If this error is not null, getInstance will throw it.
    private static volatile CodedException initializationError = null;

    private KeyConf() {
    }

    /**
     * Returns the singleton instance of the configuration.
     */
    static KeyConfProvider getInstance() {
        if (initializationError != null) {
            throw initializationError;
        }

        if (instance == null) {
            synchronized (KeyConf.class) {
                if (instance == null) {
                    initInstance();
                }
            }
        }

        return instance;
    }

    /**
     * Reloads the configuration.
     */
    public static synchronized void reload() {
        LOG.trace("reload()");
        if (instance != null) {
            instance.destroy();
        }
        initInstance();
    }

    /**
     * Reloads the configuration with given configuration instance.
     * @param conf the new key configuration provider
     */
    public static synchronized void reload(KeyConfProvider conf) {
        LOG.trace("reload({})", conf.getClass());
        if (instance != null) {
            instance.destroy();
        }
        instance = conf;
    }

    /**
     * @param memberId the member client ID
     * @return signing context for given member
     */
    public static SigningCtx getSigningCtx(ClientId memberId) {
        if (LOG.isTraceEnabled()) LOG.trace("getSigningCtx({})", memberId);
        return getInstance().getSigningCtx(memberId);
    }

    /**
     * @return the current key and certificate for SSL authentication
     */
    public static AuthKey getAuthKey() {
        LOG.trace("getAuthKey()");
        return getInstance().getAuthKey();
    }

    /**
     * @param certHash hash of the certificate
     * @return the OCSP server response for the given certificate,
     *         or null, if no response is available for that certificate
     * @throws Exception in case of any errors
     */
    public static OCSPResp getOcspResponse(String certHash)
            throws Exception {
        if (LOG.isTraceEnabled()) LOG.trace("getOcspResponse({})", certHash);
        return getInstance().getOcspResponse(certHash);
    }

    /**
     * @param cert the certificate
     * @return the OCSP server response for the given certificate,
     *         or null, if no response is available for that certificate
     * @throws Exception in case of any errors
     */
    public static OCSPResp getOcspResponse(X509Certificate cert)
            throws Exception {
        if (LOG.isTraceEnabled()) LOG.trace("getOcspResponse({})", cert.getSubjectX500Principal().getName());
        return getInstance().getOcspResponse(cert);
    }

    /**
     * @param certs list of certificates
     * @return OCSP responses for all given certificates.
     * @throws Exception if OCSP response could not be found for at least one certificate
     */
    public static List<OCSPResp> getAllOcspResponses(List<X509Certificate> certs) throws Exception {
        List<String> missingResponses = new ArrayList<>();
        List<OCSPResp> responses = getInstance().getOcspResponses(certs);
        for (int i = 0; i < certs.size(); i++) {
            if (responses.get(i) == null) {
                missingResponses.add(calculateCertHexHash(certs.get(i)));
            }
        }

        if (!missingResponses.isEmpty()) {
            throw new CodedException(X_CANNOT_CREATE_SIGNATURE,
                    "Could not get OCSP responses for certificates (%s)",
                    missingResponses);
        }

        return responses;
    }

    /**
     * @param certs list of certificates
     * @return OCSP responses for given certificates. For OCSP responses that
     *         could not be found, the list contains null values
     * @throws Exception in case of any errors
     */
    public static List<OCSPResp> getOcspResponses(List<X509Certificate> certs) throws Exception {
        return getInstance().getOcspResponses(certs);
    }

    /**
     * Updates the existing OCSP response or stores the OCSP response,
     * if it does not exist for the given certificate.
     * @param certs list of certificates
     * @param responses list of OCSP responses
     * @throws Exception in case of any errors
     */
    public static void setOcspResponses(List<X509Certificate> certs, List<OCSPResp> responses) throws Exception {
        getInstance().setOcspResponses(certs, responses);
    }

    // ------------------------------------------------------------------------

    private static void initInstance() {
        instance = null;
        try {
            instance = CachingKeyConfImpl.newInstance();
            initializationError = null;
        } catch (Exception ex) {
            initializationError = translateWithPrefix(X_MALFORMED_KEYCONF, ex);
            throw initializationError;
        }
    }
}
