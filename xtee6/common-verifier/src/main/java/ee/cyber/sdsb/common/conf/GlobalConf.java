package ee.cyber.sdsb.common.conf;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.identifier.AbstractServiceId;
import ee.cyber.sdsb.common.identifier.CentralServiceId;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.GlobalGroupId;
import ee.cyber.sdsb.common.identifier.SecurityCategoryId;
import ee.cyber.sdsb.common.identifier.SecurityServerId;
import ee.cyber.sdsb.common.identifier.ServiceId;

import static ee.cyber.sdsb.common.ErrorCodes.*;

/** Global configuration.
 *
 * TODO: Make thread safe
 */
public class GlobalConf {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalConf.class);

    private static final ThreadLocal<GlobalConfProvider> threadLocal =
            new InheritableThreadLocal<>();

    private static volatile GlobalConfProvider instance = null;

    /** Returns the singleton instance of the configuration. */
    static GlobalConfProvider getInstance() {
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

        if (SystemProperties.isDistributorEnabled()) {
            instance.verifyUpToDate();
        }

        threadLocal.set(instance);
    }

    /** Reloads the configuration. */
    public static void reload() {
        LOG.trace("reload()");

        initInstance();
    }

    /** Reloads the configuration with given configuration instance. */
    public static void reload(GlobalConfProvider conf) {
        LOG.trace("reload({})", conf.getClass());

        instance = conf;
    }

    /** Reloads the configuration if the underlying configuration
     * file has changed. */
    public static void reloadIfChanged() {
        LOG.trace("reloadIfChanged()");

        if (shouldInitInstance()) {
            LOG.debug("Conf has changed, reloading...");
            initInstance();
        } else {
            LOG.trace("Conf has not changed, no reloading necessary.");
        }
    }

    /** Returns concrete service id for the given service id type. If the input
     * is ServiceId, returns it. If the input is CentralServiceId, looks for
     * a mapping to ServiceId in GlobalConf. */
    public static ServiceId getServiceId(AbstractServiceId serviceId) {
        LOG.debug("getServiceId({})", serviceId);

        if (serviceId instanceof CentralServiceId) {
            return getInstance().getServiceId((CentralServiceId) serviceId);
        } else if (serviceId instanceof ServiceId) {
            return (ServiceId) serviceId;
        } else {
            throw new CodedException(X_INTERNAL_ERROR,
                    "Unknown type of service id: %s", serviceId.getClass());
        }
    }

    /**
     * Returns address of the given service provider's proxy based on
     * authentication certificate.
     * @return IP address converted to string, such as "192.168.2.2".
     */
    public static String getProviderAddress(X509Certificate authCert)
            throws Exception {
        LOG.debug("getProviderAddress({})", authCert.getSubjectX500Principal());

        return getInstance().getProviderAddress(authCert);
    }

    /**
     * Returns address of the given service provider's proxy.
     * @return IP address converted to string, such as "192.168.2.2".
     */
    public static Collection<String> getProviderAddress(
            ClientId serviceProvider) {
        LOG.debug("getProviderAddress({})", serviceProvider);

        return getInstance().getProviderAddress(serviceProvider);
    }
    /**
     * Returns a list of OCSP responder addresses for the given member.
     * @param member the member
     */
    public static List<String> getOcspResponderAddresses(
            X509Certificate member) throws Exception {
        LOG.debug("getOcspResponderAddresses({})", member != null
                ? member.getSubjectX500Principal().getName() : "null");

        return getInstance().getOcspResponderAddresses(member);
    }

    /** Returns a list of known OCSP responder certificates. */
    public static List<X509Certificate> getOcspResponderCertificates() {
        LOG.debug("getOcspResponderCertificates()");

        return getInstance().getOcspResponderCertificates();
    }

    /** Returns the issuer certificate for the member certificate. */
    public static X509Certificate getCaCert(X509Certificate subject)
            throws Exception {
        LOG.debug("getCaCert({})", subject.getSubjectX500Principal().getName());

        return getInstance().getCaCert(subject);
    }

    public static Collection<X509Certificate> getAllCaCerts()
            throws CertificateException {
        LOG.debug("getAllCaCerts()");

        return getInstance().getAllCaCerts();
    }

    /** Returns verification context for this server. */
    public static VerificationCtx getVerificationCtx() {
        LOG.debug("getVerificationCtx()");

        return getInstance().getVerificationCtx();
    }

    /** Returns true, if the CA has the specified OCSP responder certificate,
     * false otherwise. */
    public static boolean isOcspResponderCert(
            X509Certificate ca, X509Certificate ocspCert) {
        LOG.debug("isOcspResponderCert({}, {})",
                ca.getSubjectX500Principal().getName(),
                ocspCert.getSubjectX500Principal().getName());

        return getInstance().isOcspResponderCert(ca, ocspCert);
    }

    /**
     * Returns all the trusted and intermediate certificates that can be used
     * to verify the other party in establishing SSL connection.
     */
    public static X509Certificate[] getAuthTrustChain() {
        LOG.debug("getAuthTrustChain()");

        return getInstance().getAuthTrustChain();
    }

    /**
     * Returns true, if <code>cert</code> is registered for any
     * Security Server in GlobalConf.
     *
     * @throws Exception
     */
    public static boolean hasAuthCert(X509Certificate cert,
            SecurityServerId server) throws Exception {
        LOG.debug("hasAuthCert({}, {})", cert.getSubjectDN(), server);

        return getInstance().hasAuthCert(cert, server);
    }

    /**
     * Returns true, if <code>cert</code> can be used to authenticate as
     * member <code>member</code>.
     *
     * @throws Exception
     */
    public static boolean authCertMatchesMember(X509Certificate cert,
            ClientId memberId) throws Exception {
        LOG.debug("authCertMatchesMember({}: {}, {})",
                new Object[] {cert.getSerialNumber(), cert.getSubjectDN(),
                        memberId});

        return getInstance().authCertMatchesMember(cert, memberId);
    }

    /**
     * Returns set of codes corresponding to security categories assigned
     * to security server associated with this authentication certificate.
     */
    public static Collection<SecurityCategoryId> getProvidedCategories(
            X509Certificate authCert) throws Exception {
        LOG.debug("getProvidedCategories()");

        return getInstance().getProvidedCategories(authCert);
    }

    /**
     * Returns short name of the certificate subject.
     * Short name is used in messages and access checking.
     */
    public static ClientId getSubjectName(X509Certificate cert)
            throws Exception {
        LOG.debug("getSubjectName()");

        return getInstance().getSubjectName(cert);
    }
    /**
     * Returns the list of TSP certificates.
     */
    public static List<X509Certificate> getTspCertificates() throws Exception {
        LOG.debug("getTspCertificates()");

        return getInstance().getTspCertificates();
    }

    /**
     * Returns all addresses of all members.
     */
    public static Set<String> getKnownAddresses() {
        LOG.debug("getKnownAddresses()");

        return getInstance().getKnownAddresses();
    }

    /** Returns true, if given subject belongs to given global group. */
    public static boolean isSubjectInGlobalGroup(ClientId subject,
            GlobalGroupId group) {
        LOG.debug("isSubjectInGlobalGroup({}, {})", subject, group);

        return getInstance().isSubjectInGlobalGroup(subject, group);
    }

    /**
     * Returns time (in minutes) where validity information is considered valid.
     * After that time, OCSP responses must be refreshed.
     */
    public static int getValidationFreshnessTime() {
        return 60;
    }

    /**
     * Returns the address of the management request service.
     */
    public static String getManagementRequestServiceAddress() {
        LOG.debug("getManagementRequestServiceAddress()");

        return getInstance().getManagementRequestServiceAddress();
    }

    /**
     * Returns the service id of the management request service.
     */
    public static ClientId getManagementRequestService() {
        LOG.debug("getManagementRequestServiceAddress()");

        // Note that ClientId is sufficient, since the ServiceId is built from
        // this ClientId and the service method name that comes from
        // generated classes of the management requests.
        return getInstance().getManagementRequestService();
    }

    private static void initInstance() {
        instance = null;
        try {
            instance = new GlobalConfImpl(SystemProperties.getGlobalConfFile());
        } catch (Exception ex) {
            throw translateWithPrefix(X_MALFORMED_GLOBALCONF, ex);
        }
    }

    /** Checks that if the underlying configuration file has changed or not. */
    private static boolean shouldInitInstance() {
        return instance == null || instance.hasChanged();
    }
}
