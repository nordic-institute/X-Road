package ee.cyber.sdsb.common.conf.globalconf;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.cert.CertChain;
import ee.cyber.sdsb.common.identifier.CentralServiceId;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.GlobalGroupId;
import ee.cyber.sdsb.common.identifier.SecurityCategoryId;
import ee.cyber.sdsb.common.identifier.SecurityServerId;
import ee.cyber.sdsb.common.identifier.ServiceId;

import static ee.cyber.sdsb.common.ErrorCodes.*;

/**
 * Global configuration.
 */
@Slf4j
public class GlobalConf {

    private static final ThreadLocal<GlobalConfProvider> threadLocal =
            new InheritableThreadLocal<>();

    private static volatile GlobalConfProvider instance;

    /** Returns the singleton instance of the configuration. */
    static GlobalConfProvider getInstance() {
        if (threadLocal.get() != null) {
            return threadLocal.get();
        }

        if (instance == null) {
            instance = new GlobalConfImpl(true);
        }

        return instance;
    }

    /** Initializes current instance of conf for the calling thread.
     * Example usage: calling this method in RequestProcessor to have
     * a copy of current config for the current message. */
    public static void initForCurrentThread() {
        log.trace("initForCurrentThread()");

        if (instance == null) {
            instance = new GlobalConfImpl(false);
        }

        reloadIfChanged();

        threadLocal.set(instance);
    }

    /** Reloads the configuration. */
    public static synchronized void reload() {
        log.trace("reload()");

        instance = new GlobalConfImpl(true);
    }

    /** Reloads the configuration with given configuration instance. */
    public static void reload(GlobalConfProvider conf) {
        log.trace("reload({})", conf.getClass());

        instance = conf;
    }

    /** Reloads the configuration if the underlying configuration
     * file has changed. */
    public static synchronized void reloadIfChanged() {
        log.trace("reloadIfChanged()");

        if (instance != null) {
            try {
                instance.load(null /* ignored */);
            } catch (Exception e) {
                throw translateException(e);
            }
        }
    }

    // ------------------------------------------------------------------------

    /**
     * Verifies that the global configuration is valid. Throws exception
     * with error code ErrorCodes.X_OUTDATED_GLOBALCONF if the it is too old.
     */
    public static void verifyValidity() {
        if (!isValid()) {
            throw new CodedException(X_OUTDATED_GLOBALCONF,
                    "Global configuration is too old");
        }
    }

    /**
     * Returns true, if the global configuration is valid and can be used
     * for security-critical tasks.
     * Configuration is considered to be valid if all the files of all
     * the instances are up-to-date (not expired).
     */
    public static boolean isValid() {
        return getInstance().isValid();
    }

    /**
     * Returns the instance identifier for this configuration source.
     */
    public static String getInstanceIdentifier() {
        log.trace("getInstanceIdentifier()");

        return getInstance().getInstanceIdentifier();
    }

    /**
     * Returns the instance identifiers for all configuration sources.
     */
    public static List<String> getInstanceIdentifiers() {
        log.trace("getInstanceIdentifiers()");

        return getInstance().getInstanceIdentifiers();
    }

    /** Returns concrete service id for the given service id type. If the input
     * is ServiceId, returns it. If the input is CentralServiceId, looks for
     * a mapping to ServiceId in GlobalConf. */
    public static ServiceId getServiceId(ServiceId serviceId) {
        log.trace("getServiceId({})", serviceId);

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
     * Returns members and subsystems of a given instance.
     */
    public static List<MemberInfo> getMembers(String... instanceIdentifiers) {
        log.trace("getMembers({})", instanceIdentifiers);

        return getInstance().getMembers(instanceIdentifiers);
    }

    /**
     * Returns member name.
     */
    public static String getMemberName(ClientId clientId) {
        log.trace("getMemberName({})", clientId);

        return getInstance().getMemberName(clientId);
    }

    /**
     * Returns all central services.
     */
    public static List<CentralServiceId> getCentralServices(
            String instanceIdentifier) {
        log.trace("getCentralServices({})", instanceIdentifier);

        return getInstance().getCentralServices(instanceIdentifier);
    }

    /**
     * Returns global groups of a given instance.
     */
    public static List<GlobalGroupInfo> getGlobalGroups(
            String... instanceIdentifiers) {
        log.trace("getGlobalGroups({})", instanceIdentifiers);

        return getInstance().getGlobalGroups(instanceIdentifiers);
    }

    /**
     * Returns global groups description.
     */
    public static String getGlobalGroupDescription(
            GlobalGroupId globalGroupId) {
        log.trace("getGlobalGroupDescription({})", globalGroupId);

        return getInstance().getGlobalGroupDescription(globalGroupId);
    }

    /**
     * Returns member classes for given instance.
     */
    public static Set<String> getMemberClasses(String... instanceIdentifiers) {
        log.trace("getMemberClasses({})", instanceIdentifiers);

        return getInstance().getMemberClasses(instanceIdentifiers);
    }

    /**
     * Returns address of the given service provider's proxy based on
     * authentication certificate.
     * @return IP address converted to string, such as "192.168.2.2".
     */
    public static String getProviderAddress(X509Certificate authCert)
            throws Exception {
        log.trace("getProviderAddress({})", authCert.getSubjectX500Principal());

        return getInstance().getProviderAddress(authCert);
    }

    /**
     * Returns address of the given service provider's proxy.
     * @return IP address converted to string, such as "192.168.2.2".
     */
    public static Collection<String> getProviderAddress(
            ClientId serviceProvider) {
        log.trace("getProviderAddress({})", serviceProvider);

        return getInstance().getProviderAddress(serviceProvider);
    }

    /**
     * Returns a list of OCSP responder addresses for the given member.
     * @param member the member
     */
    public static List<String> getOcspResponderAddresses(
            X509Certificate member) throws Exception {
        log.trace("getOcspResponderAddresses({})", member != null
                ? member.getSubjectX500Principal().getName() : "null");

        return getInstance().getOcspResponderAddresses(member);
    }

    /** Returns a list of known OCSP responder certificates. */
    public static List<X509Certificate> getOcspResponderCertificates() {
        log.trace("getOcspResponderCertificates()");

        return getInstance().getOcspResponderCertificates();
    }

    /** Returns the issuer certificate for the member certificate. */
    public static X509Certificate getCaCert(String instanceIdentifier,
            X509Certificate subject) throws Exception {
        log.trace("getCaCert({}, {})", instanceIdentifier,
                subject.getSubjectX500Principal().getName());

        return getInstance().getCaCert(instanceIdentifier, subject);
    }

    public static Collection<X509Certificate> getAllCaCerts()
            throws CertificateException {
        log.trace("getAllCaCerts()");

        return getInstance().getAllCaCerts();
    }

    /** Returns the top CA and any intermediate CA certs for a
     * given end entity. */
    public static CertChain getCertChain(String instanceIdentifier,
            X509Certificate subject) throws Exception {
        log.trace("getCertChain({}, {})", instanceIdentifier,
                subject.getSubjectX500Principal().getName());

        return getInstance().getCertChain(instanceIdentifier, subject);
    }

    /** Returns true, if the CA has the specified OCSP responder certificate,
     * false otherwise. */
    public static boolean isOcspResponderCert(
            X509Certificate ca, X509Certificate ocspCert) {
        log.trace("isOcspResponderCert({}, {})",
                ca.getSubjectX500Principal().getName(),
                ocspCert.getSubjectX500Principal().getName());

        return getInstance().isOcspResponderCert(ca, ocspCert);
    }

    /**
     * Returns all the trusted and intermediate certificates that can be used
     * to verify the other party in establishing SSL connection.
     */
    public static X509Certificate[] getAuthTrustChain() {
        log.trace("getAuthTrustChain()");

        return getInstance().getAuthTrustChain();
    }

    /**
     * Returns the security server id for the given authentication certificate
     * of null of the authentication certificate does not map to any security
     * server.
     */
    public static SecurityServerId getServerId(X509Certificate cert)
            throws Exception {
        log.trace("getServerId({})", cert.getSubjectDN());

        return getInstance().getServerId(cert);
    }

    /**
     * Returns true, if <code>cert</code> can be used to authenticate as
     * member <code>member</code>.
     *
     * @throws Exception
     */
    public static boolean authCertMatchesMember(X509Certificate cert,
            ClientId memberId) throws Exception {
        log.trace("authCertMatchesMember({}: {}, {})",
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
        log.trace("getProvidedCategories()");

        return getInstance().getProvidedCategories(authCert);
    }

    /**
     * Returns short name of the certificate subject.
     * Short name is used in messages and access checking.
     */
    public static ClientId getSubjectName(String instanceIdentifier,
            X509Certificate cert) throws Exception {
        log.trace("getSubjectName({})", instanceIdentifier);

        return getInstance().getSubjectName(instanceIdentifier, cert);
    }

    /**
     * Returns all approved TSPs.
     */
    public static List<String> getApprovedTsps(String instanceIdentifier) {
        log.trace("getApprovedTsps({})", instanceIdentifier);

        return getInstance().getApprovedTsps(instanceIdentifier);
    }

    /**
     * Returns approved TSP name.
     */
    public static String getApprovedTspName(String instanceIdentifier,
            String approvedTspUrl) {
        log.trace("getApprovedTspName({}, {})",
            instanceIdentifier, approvedTspUrl);

        return getInstance().getApprovedTspName(
            instanceIdentifier, approvedTspUrl);
    }

    /**
     * Returns the list of TSP certificates.
     */
    public static List<X509Certificate> getTspCertificates() throws Exception {
        log.trace("getTspCertificates()");

        return getInstance().getTspCertificates();
    }

    /**
     * Returns all addresses of all members.
     */
    public static Set<String> getKnownAddresses() {
        log.trace("getKnownAddresses()");

        return getInstance().getKnownAddresses();
    }

    /** Returns true, if given subject belongs to given global group. */
    public static boolean isSubjectInGlobalGroup(ClientId subject,
            GlobalGroupId group) {
        log.trace("isSubjectInGlobalGroup({}, {})", subject, group);

        return getInstance().isSubjectInGlobalGroup(subject, group);
    }

    /** Returns true, if client belongs to the security server. */
    public static boolean isSecurityServerClient(ClientId client,
            SecurityServerId securityServer) {
        log.trace("isSecurityServerClient({}, {})", client, securityServer);

        return getInstance().isSecurityServerClient(client, securityServer);
    }

    /**
     * Returns time (in seconds) where validity information is considered valid.
     * After that time, OCSP responses must be refreshed.
     */
    public static int getOcspFreshnessSeconds(boolean smallestValue) {
        log.trace("getOcspFreshnessSeconds()");

        return getInstance().getOcspFreshnessSeconds(smallestValue);
    }

    /**
     * Returns the address of the management request service.
     */
    public static String getManagementRequestServiceAddress() {
        log.trace("getManagementRequestServiceAddress()");

        return getInstance().getManagementRequestServiceAddress();
    }

    /**
     * Returns the service id of the management request service.
     */
    public static ClientId getManagementRequestService() {
        log.trace("getManagementRequestServiceAddress()");

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
        log.trace("getCentralServerSslCertificate()");

        return getInstance().getCentralServerSslCertificate();
    }

    /**
     * Returns the timestamping interval in seconds.
     */
    public static int getTimestampingIntervalSeconds() {
        log.trace("getTimestampingIntervalSeconds()");

        return getInstance().getTimestampingIntervalSeconds();
    }

}
