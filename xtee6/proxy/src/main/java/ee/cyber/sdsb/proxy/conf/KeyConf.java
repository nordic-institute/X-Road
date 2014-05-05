package ee.cyber.sdsb.proxy.conf;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.conf.AuthKey;
import ee.cyber.sdsb.common.identifier.ClientId;

import static ee.cyber.sdsb.common.ErrorCodes.X_MALFORMED_KEYCONF;
import static ee.cyber.sdsb.common.ErrorCodes.translateWithPrefix;

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

    /** Returns certificates for all members. */
    public static List<X509Certificate> getMemberCerts(ClientId memberId)
            throws Exception {
        LOG.trace("getMemberCerts({})", memberId);

        return getInstance().getMemberCerts(memberId);
    }

    /**
     * Returns the current key and certificate for SSL authentication.
     */
    public static AuthKey getAuthKey() {
        LOG.trace("getAuthKey()");

        return getInstance().getAuthKey();
    }

    /** Returns the certificate used for signing OCSP requests. */
    public static X509Certificate getOcspSignerCert() throws Exception {
        LOG.trace("getOcspSignerCert()");

        return getInstance().getOcspSignerCert();
    }

    /** Returns the key used for signing OCSP requests. */
    public static PrivateKey getOcspRequestKey(X509Certificate subject)
            throws Exception {
        LOG.trace("getOcspRequestKey({})", subject != null
                ? subject.getSubjectX500Principal().getName() : "null");

        return getInstance().getOcspRequestKey(subject);
    }

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
