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
import ee.cyber.sdsb.common.cert.CertChain;
import ee.cyber.sdsb.common.identifier.CentralServiceId;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.GlobalGroupId;
import ee.cyber.sdsb.common.identifier.SecurityCategoryId;
import ee.cyber.sdsb.common.identifier.SecurityServerId;
import ee.cyber.sdsb.common.identifier.ServiceId;

import static ee.cyber.sdsb.common.ErrorCodes.*;

/** Global configuration.
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
    public static synchronized void reload() {
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
    public static synchronized void reloadIfChanged() {
        LOG.trace("reloadIfChanged()");

        if (shouldInitInstance()) {
            LOG.debug("Conf has changed, reloading...");
            initInstance();
        } else {
            LOG.trace("Conf has not changed, no reloading necessary.");
        }
    }

    /**
     * Returns the SDSB instance identifier.
     */
    public static String getSdsbInstance() {
        return getInstance().getSdsbInstance();
    }

    /** Returns concrete service id for the given service id type. If the input
     * is ServiceId, returns it. If the input is CentralServiceId, looks for
     * a mapping to ServiceId in GlobalConf. */
    public static ServiceId getServiceId(ServiceId serviceId) {
        LOG.trace("getServiceId({})", serviceId);

        if (serviceId instanceof CentralServiceId) {
            return getInstance().getServiceId((CentralServiceId) serviceId);
        } else if (serviceId instanceof ServiceId) {
            return serviceId;
        } else {
            throw new CodedException(X_INTERNAL_ERROR,
                    "Unknown type of service id: %s", serviceId.getClass());
        }
    }

    /**
     * Returns all members.
     */
    public static List<ClientId> getMembers() {
        LOG.trace("getMembers()");

        return getInstance().getMembers();
    }

    /**
     * Returns all central services.
     */
    public static List<CentralServiceId> getCentralServices() {
        LOG.trace("getCentralServices()");

        return getInstance().getCentralServices();
    }

    /**
     * Returns address of the given service provider's proxy based on
     * authentication certificate.
     * @return IP address converted to string, such as "192.168.2.2".
     */
    public static String getProviderAddress(X509Certificate authCert)
            throws Exception {
        LOG.trace("getProviderAddress({})", authCert.getSubjectX500Principal());

        return getInstance().getProviderAddress(authCert);
    }

    /**
     * Returns address of the given service provider's proxy.
     * @return IP address converted to string, such as "192.168.2.2".
     */
    public static Collection<String> getProviderAddress(
            ClientId serviceProvider) {
        LOG.trace("getProviderAddress({})", serviceProvider);

        return getInstance().getProviderAddress(serviceProvider);
    }

    /**
     * Returns a list of OCSP responder addresses for the given member.
     * @param member the member
     */
    public static List<String> getOcspResponderAddresses(
            X509Certificate member) throws Exception {
        LOG.trace("getOcspResponderAddresses({})", member != null
                ? member.getSubjectX500Principal().getName() : "null");

        return getInstance().getOcspResponderAddresses(member);
    }

    /** Returns a list of known OCSP responder certificates. */
    public static List<X509Certificate> getOcspResponderCertificates() {
        LOG.trace("getOcspResponderCertificates()");

        return getInstance().getOcspResponderCertificates();
    }

    /** Returns the issuer certificate for the member certificate. */
    public static X509Certificate getCaCert(X509Certificate subject)
            throws Exception {
        LOG.trace("getCaCert({})", subject.getSubjectX500Principal().getName());

        return getInstance().getCaCert(subject);
    }

    public static Collection<X509Certificate> getAllCaCerts()
            throws CertificateException {
        LOG.trace("getAllCaCerts()");

        return getInstance().getAllCaCerts();
    }

    /** Returns the top CA and any intermediate CA certs for a
     * given end entity. */
    public static CertChain getCertChain(X509Certificate subject)
            throws Exception {
        LOG.trace("getCertChain({})",
                subject.getSubjectX500Principal().getName());

        return getInstance().getCertChain(subject);
    }

    /** Returns true, if the CA has the specified OCSP responder certificate,
     * false otherwise. */
    public static boolean isOcspResponderCert(
            X509Certificate ca, X509Certificate ocspCert) {
        LOG.trace("isOcspResponderCert({}, {})",
                ca.getSubjectX500Principal().getName(),
                ocspCert.getSubjectX500Principal().getName());

        return getInstance().isOcspResponderCert(ca, ocspCert);
    }

    /**
     * Returns all the trusted and intermediate certificates that can be used
     * to verify the other party in establishing SSL connection.
     */
    public static X509Certificate[] getAuthTrustChain() {
        LOG.trace("getAuthTrustChain()");

        return getInstance().getAuthTrustChain();
    }

    /**
     * If <code>server</code> <b>is not null</b>, returns true if <code>cert</code>
     * is registered for that particular Security Server in GlobalConf.
     * If <code>server</code> <b>is null</b>, then returns true if <code>cert</code>
     * is registered for any Security Server in GlobalConf.
     *
     * @throws Exception
     */
    public static boolean hasAuthCert(X509Certificate cert,
            SecurityServerId server) throws Exception {
        LOG.trace("hasAuthCert({}, {})", cert.getSubjectDN(), server);

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
        LOG.trace("authCertMatchesMember({}: {}, {})",
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
        LOG.trace("getProvidedCategories()");

        return getInstance().getProvidedCategories(authCert);
    }

    /**
     * Returns short name of the certificate subject.
     * Short name is used in messages and access checking.
     */
    public static ClientId getSubjectName(X509Certificate cert)
            throws Exception {
        LOG.trace("getSubjectName()");

        return getInstance().getSubjectName(cert);
    }
    /**
     * Returns the list of TSP certificates.
     */
    public static List<X509Certificate> getTspCertificates() throws Exception {
        LOG.trace("getTspCertificates()");

        return getInstance().getTspCertificates();
    }

    /**
     * Returns all addresses of all members.
     */
    public static Set<String> getKnownAddresses() {
        LOG.trace("getKnownAddresses()");

        return getInstance().getKnownAddresses();
    }

    /** Returns true, if given subject belongs to given global group. */
    public static boolean isSubjectInGlobalGroup(ClientId subject,
            GlobalGroupId group) {
        LOG.trace("isSubjectInGlobalGroup({}, {})", subject, group);

        return getInstance().isSubjectInGlobalGroup(subject, group);
    }

    /** Returns true, if client belongs to the security server. */
    public static boolean isSecurityServerClient(ClientId client,
            SecurityServerId securityServer) {
        LOG.trace("isSecurityServerClient({}, {})", client, securityServer);

        return getInstance().isSecurityServerClient(client, securityServer);
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
        LOG.trace("getManagementRequestServiceAddress()");

        return getInstance().getManagementRequestServiceAddress();
    }

    /**
     * Returns the service id of the management request service.
     */
    public static ClientId getManagementRequestService() {
        LOG.trace("getManagementRequestServiceAddress()");

        // Note that ClientId is sufficient, since the ServiceId is built from
        // this ClientId and the service method name that comes from
        // generated classes of the management requests.
        return getInstance().getManagementRequestService();
    }

    /**
     * Returns SSL certificates of central servers.
     */
    public static X509Certificate getCentralServerSslCertificate()
            throws Exception {
        LOG.trace("getCentralServerSslCertificate()");

        return getInstance().getCentralServerSslCertificate();
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
