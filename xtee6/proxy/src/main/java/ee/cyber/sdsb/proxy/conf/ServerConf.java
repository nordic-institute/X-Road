package ee.cyber.sdsb.proxy.conf;

import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;

import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.SecurityCategoryId;
import ee.cyber.sdsb.common.identifier.SecurityServerId;
import ee.cyber.sdsb.common.identifier.ServiceId;

import static ee.cyber.sdsb.common.ErrorCodes.X_MALFORMED_SERVERCONF;
import static ee.cyber.sdsb.common.ErrorCodes.translateWithPrefix;

/** Configuration of the current proxy server.
 *
 * TODO: Make thread safe
 */
public class ServerConf {

    private static final Logger LOG = LoggerFactory.getLogger(ServerConf.class);

    private static final ThreadLocal<ServerConfProvider> threadLocal =
            new InheritableThreadLocal<>();

    private static volatile ServerConfProvider instance = null;

    static {
        // TODO: Move to a more relevant place?
        org.apache.xml.security.Init.init();
        Security.addProvider(new BouncyCastleProvider());
    }

    /** Returns the singleton instance of the configuration. */
    static ServerConfProvider getInstance() {
        if (threadLocal.get() != null) {
            return threadLocal.get();
        }

        reloadIfChanged();

        return instance;
    }

    /** Initializes current instance of conf for the calling thread.
     * Example usage: calling this method in RequestProcessor to have
     * a copy of current config for the current message. */
    public static void initForCurrentThread() {
        LOG.trace("initForCurrentThread()");

        reloadIfChanged();

        threadLocal.set(instance);
    }

    /** Reloads the configuration. */
    public static void reload() {
        LOG.trace("reload()");

        initInstance();
    }

    /** Reloads the configuration with given configuration instance. */
    public static void reload(ServerConfProvider conf) {
        LOG.trace("reload({})", conf.getClass());

        instance = conf;
    }

    /** Reloads the configuration if the underlying configuration
     * file has changed. */
    public static void reloadIfChanged() {
        LOG.trace("reloadIfChanged()");

        if (shouldInitInstance()) {
            LOG.info("Conf has changed, reloading...");
            initInstance();
        } else {
            LOG.trace("Conf has not changed, no reloading necessary.");
        }
    }

    // ------------------------------------------------------------------------

    /**
     * Returns the identifier of this Security Server.
     */
    public static SecurityServerId getIdentifier() {
        LOG.trace("getIdentifier()");

        return getInstance().getIdentifier();
    }

    /**
     * Returns true, if service with the given identifier exists in
     * the configuration.
     */
    public static boolean serviceExists(ServiceId service) {
        LOG.trace("serviceExists({})", service);

        return getInstance().serviceExists(service);
    }

    /** Returns true, if member <code>sender</code> is allowed
     * to invoke service <code>serviceName</code>
     */
    public static boolean isQueryAllowed(ClientId sender, ServiceId service) {
        LOG.trace("isQueryAllowed({}, {})", sender, service);

        return getInstance().isQueryAllowed(sender, service);
    }

    /**
     * If the service is disabled, returns notice about this event.
     * If the service is enabled, returns null.
     */
    public static String getDisabledNotice(ServiceId service) {
        LOG.trace("getDisabledNotice({})", service);

        return getInstance().getDisabledNotice(service);
    }

    /** Return URL for corresponding to service provider for given
     * service name.
     */
    public static String getServiceAddress(ServiceId service) {
        LOG.trace("getServiceAddress({})", service);

        return getInstance().getServiceAddress(service);
    }

    /** Return service timeout in seconds.
     */
    public static int getServiceTimeout(ServiceId service) {
        LOG.trace("getServiceTimeout({})", service);

        return getInstance().getServiceTimeout(service);
    }

    /**
     * Returns set of security category codes required by this service.
     */
    public static Collection<SecurityCategoryId> getRequiredCategories(
            ServiceId service) {
        LOG.trace("getRequiredCategories({})", service);

        return getInstance().getRequiredCategories(service);
    }

    /**
     * Returns time (in minutes) where validity information is considered valid.
     * After that time, OCSP responses must be refreshed.
     */
    public static int getValidationFreshnessTime() {
        return 60;
    }

    /**
     * Returns a list of certificates for which to retrieve OCSP responses.
     */
    public static List<X509Certificate> getCertsForOcsp() throws Exception {
        LOG.trace("getCertsForOcsp()");

        return getInstance().getCertsForOcsp();
    }

    /** Returns certificates for all members. */
    public static List<X509Certificate> getMemberCerts() throws Exception {
        LOG.trace("getMemberCerts()");

        return getInstance().getMemberCerts();
    }

    /** Returns the OCSP server response for the given certificate,
     * or null, if no response is available for that certificate. */
    public static OCSPResp getOcspResponse(X509Certificate cert)
            throws Exception {
        LOG.trace("getOcspResponse({})",
                cert.getSubjectX500Principal().getName());

        return getInstance().getOcspResponse(cert);
    }

    /** Returns true, if we have cached OCSP response for this certificate */
    public static boolean isCachedOcspResponse(String certHash)
            throws Exception {
        LOG.trace("getOcspResponse({})", certHash);

        return getInstance().isCachedOcspResponse(certHash);
    }

    /** Updates the existing OCSP response or stores the OCSP response,
     * if it does not exist for the given certificate.*/
    public static void setOcspResponse(String certHash, OCSPResp response)
            throws Exception {
        LOG.trace("setOcspResponse({}, {})", certHash, response.getStatus());

        getInstance().setOcspResponse(certHash, response);
    }

    /**
     * ClientProxy and ServerProsy will listen to this address.
     * @return IP address encoded as string.
     */
    public static String getConnectorHost() {
        LOG.trace("getConnectorHost()");

        return SystemProperties.getConnectorHost();
    }

    /**
     * Returns the URL of the GlobalConf distributor.
     */
    public static String getGlobalConfDistributorUrl() {
        LOG.trace("getGlobalConfDistributorUrl()");

        return getInstance().getGlobalConfDistributorUrl();
    }

    /**
     * Returns the certificate used to verify signed GlobalConf.
     */
    public static X509Certificate getGlobalConfVerificationCert()
            throws Exception {
        LOG.trace("getGlobalConfVerificationCert()");

        return getInstance().getGlobalConfVerificationCert();
    }

    /**
     * Returns list of URLs for the Time-stamping providers configured
     * in this security server.
     */
    public static List<String> getTspUrl() {
        LOG.trace("getTspUrl()");

        return getInstance().getTspUrl();
    }

    private static void initInstance() {
        instance = null;
        try {
            instance = new ServerConfImpl(SystemProperties.getServerConfFile());
        } catch (Exception ex) {
            throw translateWithPrefix(X_MALFORMED_SERVERCONF, ex);
        }
    }

    // Checks that if the underlying configuration file has changed or not.
    private static boolean shouldInitInstance() {
        return instance == null || instance.hasChanged();
    }
}
