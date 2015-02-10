package ee.cyber.sdsb.common.conf.globalconf;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import ee.cyber.sdsb.common.cert.CertChain;
import ee.cyber.sdsb.common.conf.ConfProvider;
import ee.cyber.sdsb.common.identifier.CentralServiceId;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.GlobalGroupId;
import ee.cyber.sdsb.common.identifier.SecurityCategoryId;
import ee.cyber.sdsb.common.identifier.SecurityServerId;
import ee.cyber.sdsb.common.identifier.ServiceId;

/**
 * API for implementing global configuration providers.
 */
public interface GlobalConfProvider extends ConfProvider {

    /**
     * @return true, if the global configuration is valid and can be used
     * for security-critical tasks.
     */
    boolean isValid();

    /**
     * @return the instance identifier for this configuration source.
     */
    String getInstanceIdentifier();

    /**
     * @return the instance identifiers for all configuration sources.
     */
    List<String> getInstanceIdentifiers();

    /**
     * @return the service Id that corresponds to the provided central
     * service Id.
     */
    ServiceId getServiceId(CentralServiceId serviceId);

    /**
     * @return members and subsystems of a given instance.
     */
    List<MemberInfo> getMembers(String... instanceIdentifiers);

    /**
     * @return member name.
     */
    String getMemberName(ClientId clientId);

    /**
     * @return all central services.
     */
    List<CentralServiceId> getCentralServices(String instanceIdentifier);

    /**
     * @return global groups of a given instance.
     */
    List<GlobalGroupInfo> getGlobalGroups(String... instanceIdentifiers);

    /**
     * @return global groups description.
     */
    String getGlobalGroupDescription(GlobalGroupId globalGroupId);

    /**
     * @return member classes for given instance.
     */
    Set<String> getMemberClasses(String... instanceIdentifiers);

    /**
     * @return address of the given service provider's proxy based on
     * authentication certificate.
     */
    String getProviderAddress(X509Certificate authCert) throws Exception;

    /**
     * @return address of the given service provider's proxy.
     */
    Collection<String> getProviderAddress(ClientId clientId);

    /**
     * @return a list of suitable OCSP responder addresses for the given member.
     */
    List<String> getOcspResponderAddresses(X509Certificate member)
            throws Exception;

    /**
     * @return a list of known OCSP responder certificates.
     */
    List<X509Certificate> getOcspResponderCertificates();

    /**
     * @return the CA certificate for the given member.
     * */
    X509Certificate getCaCert(String instanceIdentifier,
            X509Certificate memberCert) throws Exception;

    /**
     * @return all CA certificates.
     */
    List<X509Certificate> getAllCaCerts() throws CertificateException;

    /**
     * @return the top CA and any intermediate CA certs for a given end entity.
     */
    CertChain getCertChain(String instanceIdentifer, X509Certificate subject)
            throws Exception;

    /**
     * @return true, if the OCSP certificate belongs to the CA certificate's
     * OCSP responders.
     */
    boolean isOcspResponderCert(X509Certificate ca, X509Certificate ocspCert);

    /**
     * @return all the trusted and intermediate certificates that can be used
     * to verify the other party in establishing SSL connection.
     */
    X509Certificate[] getAuthTrustChain();

    /**
     * @return the security server id for the given authentication certificate
     * of null of the authentication certificate does not map to any security
     * server.
     */
    SecurityServerId getServerId(X509Certificate cert) throws Exception;

    /**
     * @return true, if <code>cert</code> can be used to authenticate as
     * member <code>member</code>.
     */
     boolean authCertMatchesMember(X509Certificate cert, ClientId memberId)
            throws Exception;

    /**
     * @return set of codes corresponding to security categories assigned
     * to security server associated with this authentication certificate.
     */
    Set<SecurityCategoryId> getProvidedCategories(X509Certificate authCert)
            throws Exception;

    /**
     * @return short name of the certificate subject.
     * Short name is used in messages and access checking.
     */
    ClientId getSubjectName(String instanceIdentifier, X509Certificate cert)
            throws Exception;

    /**
     * @return all approved TSPs.
     */
    List<String> getApprovedTsps(String instanceIdentifier);

    /**
     * @return approved TSP name.
     */
    String getApprovedTspName(String instanceIdentifier, String approvedTspUrl);

    /**
     * @return a list of TSP certificates.
     */
    List<X509Certificate> getTspCertificates() throws Exception;

    /**
     * @return all addresses of all members.
     */
    Set<String> getKnownAddresses();

    /**
     * @return true, if given subject belongs to given global group.
     */
    boolean isSubjectInGlobalGroup(ClientId subject, GlobalGroupId group);

    /**
     * @return true, if client belongs to the security server.
     */
    boolean isSecurityServerClient(ClientId client,
            SecurityServerId securityServer);

    /**
     * @return all CA certificates that are suitable for verification, that is
     * pki elements that are not marked authenticationOnly
     */
    List<X509Certificate> getVerificationCaCerts();

    /**
     * @return the address of the management request service.
     */
    String getManagementRequestServiceAddress();

    /**
     * @return the service id of the management request service.
     */
    ClientId getManagementRequestService();

    /**
     * @return SSL certificates of central servers.
     */
    X509Certificate getCentralServerSslCertificate() throws Exception;

    /**
     * @param smallestValue if true, the smallest value computed over all
     * known instances is returned. Otherwise the value of the current instance
     * is returned.
     * @return maximum allowed validity time of OCSP responses. If producedAt
     * field of an OCSP response is older than ocspFreshnessSeconds seconds,
     * it is no longer valid.
     */
    int getOcspFreshnessSeconds(boolean smallestValue);

    /**
     * @return the timestamping interval in seconds.
     */
    int getTimestampingIntervalSeconds();

    /**
     * @return all security server ids
     */
    List<SecurityServerId> getSecurityServers(String... instanceIdentifiers);
}
