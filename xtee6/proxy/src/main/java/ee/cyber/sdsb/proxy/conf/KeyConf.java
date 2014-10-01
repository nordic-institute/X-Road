package ee.cyber.sdsb.proxy.conf;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import org.bouncycastle.cert.ocsp.OCSPResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.conf.AuthKey;
import ee.cyber.sdsb.common.identifier.ClientId;

import static ee.cyber.sdsb.common.ErrorCodes.*;
import static ee.cyber.sdsb.common.util.CryptoUtils.calculateCertHexHash;

/**
 * Static class for accessing Key Configuration.
 */
public class KeyConf {

    private static final Logger LOG = LoggerFactory.getLogger(KeyConf.class);

    private static final ThreadLocal<KeyConfProvider> threadLocal =
            new InheritableThreadLocal<>();

    private static volatile KeyConfProvider instance = null;

    // Holds the potential initialization error that might occur when
    // (re)loading a configuration.
    // If this error is not null, getInstance will throw it.
    private static volatile CodedException initializationError = null;

    /** Returns the singleton instance of the configuration. */
    static KeyConfProvider getInstance() {
        if (threadLocal.get() != null) {
            return threadLocal.get();
        }

        if (initializationError != null) {
            throw initializationError;
        }

        if (instance == null) {
            initInstance();
        }

        return instance;
    }

    /** Initializes current instance of configuration for the calling thread.
     * Example usage: calling this method in RequestProcessor to have
     * a copy of current configuration for the current message. */
    public static void initForCurrentThread() {
        LOG.trace("initForCurrentThread()");

        if (instance == null) {
            initInstance();
        }

        threadLocal.set(instance);
    }

    /** Reloads the configuration. */
    public static void reload() {
        LOG.trace("reload()");

        initInstance();
    }

    /** Reloads the configuration with given configuration instance. */
    public static void reload(KeyConfProvider conf) {
        LOG.trace("reload({})", conf.getClass());

        instance = conf;
    }

    /** Returns signing context for given member. */
    public static SigningCtx getSigningCtx(ClientId memberId) {
        LOG.trace("getSigningCtx({})", memberId);

        return getInstance().getSigningCtx(memberId);
    }

    /**
     * Returns the current key and certificate for SSL authentication.
     */
    public static AuthKey getAuthKey() {
        LOG.trace("getAuthKey()");

        return getInstance().getAuthKey();
    }

    /** Returns the OCSP server response for the given certificate,
     * or null, if no response is available for that certificate. */
    public static OCSPResp getOcspResponse(String certHash)
            throws Exception {
        LOG.trace("getOcspResponse({})", certHash);

        return getInstance().getOcspResponse(certHash);
    }

    /** Returns the OCSP server response for the given certificate,
     * or null, if no response is available for that certificate. */
    public static OCSPResp getOcspResponse(X509Certificate cert)
            throws Exception {
        LOG.trace("getOcspResponse({})",
                cert.getSubjectX500Principal().getName());

        return getInstance().getOcspResponse(cert);
    }

    /**
     * Returns OCSP responses for all given certificates. Throws exception,
     * if OCSP response could not be found for at least one certificate.
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
     * Returns OCSP responses for given certificates. For OCSP responses that
     * could not be found, the list contains null values.
     */
    public static List<OCSPResp> getOcspResponses(List<X509Certificate> certs)
            throws Exception {
        LOG.trace("getOcspResponses({} certs)", certs.size());

        return getInstance().getOcspResponses(certs);
    }

    /** Updates the existing OCSP response or stores the OCSP response,
     * if it does not exist for the given certificate.*/
    public static void setOcspResponses(List<X509Certificate> certs,
            List<OCSPResp> responses) throws Exception {
        LOG.trace("setOcspResponses({})", certs.size());

        getInstance().setOcspResponses(certs, responses);
    }

    // ------------------------------------------------------------------------

    private static void initInstance() {
        instance = null;
        try {
            instance = new KeyConfImpl();
            initializationError = null;
        } catch (Exception ex) {
            initializationError = translateWithPrefix(X_MALFORMED_KEYCONF, ex);
            throw initializationError;
        }
    }
}
