/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import org.bouncycastle.cert.ocsp.OCSPResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.conf.globalconf.AuthKey;
import ee.ria.xroad.common.identifier.ClientId;

import static ee.ria.xroad.common.ErrorCodes.*;
import static ee.ria.xroad.common.util.CryptoUtils.calculateCertHexHash;

/**
 * Static class for accessing Key Configuration.
 */
public final class KeyConf {

    private static final Logger LOG = LoggerFactory.getLogger(KeyConf.class);

    private static final ThreadLocal<KeyConfProvider> THREAD_LOCAL =
            new InheritableThreadLocal<>();

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
        if (THREAD_LOCAL.get() != null) {
            return THREAD_LOCAL.get();
        }

        if (initializationError != null) {
            throw initializationError;
        }

        if (instance == null) {
            initInstance();
        }

        return instance;
    }

    /**
     * Initializes current instance of configuration for the calling thread.
     * Example usage: calling this method in RequestProcessor to have
     * a copy of current configuration for the current message.
     */
    public static void initForCurrentThread() {
        LOG.trace("initForCurrentThread()");

        if (instance == null) {
            initInstance();
        }

        THREAD_LOCAL.set(instance);
    }

    /**
     * Reloads the configuration.
     */
    public static void reload() {
        LOG.trace("reload()");

        initInstance();
    }

    /**
     * Reloads the configuration with given configuration instance.
     * @param conf the new key configuration provider
     */
    public static void reload(KeyConfProvider conf) {
        LOG.trace("reload({})", conf.getClass());

        instance = conf;
    }

    /**
     * @return signing context for given member
     * @param memberId the member client ID
     */
    public static SigningCtx getSigningCtx(ClientId memberId) {
        LOG.trace("getSigningCtx({})", memberId);

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
     * @return the OCSP server response for the given certificate,
     * or null, if no response is available for that certificate
     * @param certHash hash of the certificate
     * @throws Exception in case of any errors
     */
    public static OCSPResp getOcspResponse(String certHash)
            throws Exception {
        LOG.trace("getOcspResponse({})", certHash);

        return getInstance().getOcspResponse(certHash);
    }

    /**
     * @return the OCSP server response for the given certificate,
     * or null, if no response is available for that certificate
     * @param cert the certificate
     * @throws Exception in case of any errors
     */
    public static OCSPResp getOcspResponse(X509Certificate cert)
            throws Exception {
        LOG.trace("getOcspResponse({})",
                cert.getSubjectX500Principal().getName());

        return getInstance().getOcspResponse(cert);
    }

    /**
     * @return OCSP responses for all given certificates.
     * @param certs list of certificates
     * @throws Exception if OCSP response could not be found for at least one certificate
     */
    public static List<OCSPResp> getAllOcspResponses(
            List<X509Certificate> certs) throws Exception {
        LOG.trace("getAllOcspResponses({} certs)", certs.size());

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
     * @return OCSP responses for given certificates. For OCSP responses that
     * could not be found, the list contains null values
     * @param certs list of certificates
     * @throws Exception in case of any errors
     */
    public static List<OCSPResp> getOcspResponses(List<X509Certificate> certs)
            throws Exception {
        LOG.trace("getOcspResponses({} certs)", certs.size());

        return getInstance().getOcspResponses(certs);
    }

    /**
     * Updates the existing OCSP response or stores the OCSP response,
     * if it does not exist for the given certificate.
     * @param certs list of certificates
     * @param responses list of OCSP responses
     * @throws Exception in case of any errors
     */
    public static void setOcspResponses(List<X509Certificate> certs,
            List<OCSPResp> responses) throws Exception {
        LOG.trace("setOcspResponses({})", certs.size());

        getInstance().setOcspResponses(certs, responses);
    }

    // ------------------------------------------------------------------------

    private static void initInstance() {
        instance = null;
        try {
            instance = new CachingKeyConfImpl();
            initializationError = null;
        } catch (Exception ex) {
            initializationError = translateWithPrefix(X_MALFORMED_KEYCONF, ex);
            throw initializationError;
        }
    }
}
