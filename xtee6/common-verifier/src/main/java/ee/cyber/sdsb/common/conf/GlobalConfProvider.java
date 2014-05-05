package ee.cyber.sdsb.common.conf;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import ee.cyber.sdsb.common.identifier.CentralServiceId;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.GlobalGroupId;
import ee.cyber.sdsb.common.identifier.SecurityCategoryId;
import ee.cyber.sdsb.common.identifier.SecurityServerId;
import ee.cyber.sdsb.common.identifier.ServiceId;

/** API for implementing global configuration providers. */
public interface GlobalConfProvider extends ConfProvider {

    /**
     * Returns the service Id that corresponds to the
     * provided central service Id.
     */
    ServiceId getServiceId(CentralServiceId serviceId);

    /**
     * Returns address of the given service provider's proxy based on
     * authentication certificate.
     * @return IP address converted to string, such as "192.168.2.2".
     */
    String getProviderAddress(X509Certificate authCert) throws Exception;

    /**
     * Returns address of the given service provider's proxy.
     * @return IP address converted to string, such as "192.168.2.2".
     */
    Collection<String> getProviderAddress(ClientId clientId);

    /** Returns security (verification) context for this server. */
    VerificationCtx getVerificationCtx();

    /**
     * Returns a list of suitable OCSP responder addresses
     * for the given member.
     * @param member the member
     */
    List<String> getOcspResponderAddresses(X509Certificate member)
            throws Exception;

    /**
     * Returns a list of known OCSP responder certificates.
     */
    List<X509Certificate> getOcspResponderCertificates();

    /**
     * Returns the CA certificate for the given member.
     * */
    X509Certificate getCaCert(X509Certificate memberCert) throws Exception;

    List<X509Certificate> getAllCaCerts() throws CertificateException;

    /**
     * Returns true, if the OCSP certificate belongs to the CA certificate's
     * OCSP responders.
     * @param ca the ca certificate
     * @param ocspCert the OCSP certificate
     */
    boolean isOcspResponderCert(X509Certificate ca, X509Certificate ocspCert);

    /**
     * Returns all the trusted and intermediate certificates that can be used
     * to verify the other party in establishing SSL connection.
     */
    X509Certificate[] getAuthTrustChain();

    /**
     * Returns true, if <code>cert</code> is registered for the
     * Security Server in GlobalConf.
     *
     * @throws Exception
     */
    boolean hasAuthCert(X509Certificate cert, SecurityServerId server)
            throws Exception;

    /**
     * Returns true, if <code>cert</code> can be used to authenticate as
     * member <code>member</code>.
     *
     * @throws Exception
     */
     boolean authCertMatchesMember(X509Certificate cert, ClientId memberId)
            throws Exception;

    /**
     * Returns set of codes corresponding to security categories assigned
     * to security server associated with this authentication certificate.
     */
    Set<SecurityCategoryId> getProvidedCategories(X509Certificate authCert)
            throws Exception;

    /**
     * Returns short name of the certificate subject.
     * Short name is used in messages and access checking.
     */
    ClientId getSubjectName(X509Certificate cert) throws Exception;

    /**
     * Returns a list of TSP certificates.
     */
    List<X509Certificate> getTspCertificates() throws Exception;

    /**
     * Returns all addresses of all members.
     */
    Set<String> getKnownAddresses();

    /** Returns true, if given subject belongs to given global group. */
    boolean isSubjectInGlobalGroup(ClientId subject, GlobalGroupId group);

    /**
     * Returns all CA certificates that are suitable for verification, that is
     * pki elements that are not marked authenticationOnly
     */
    List<X509Certificate> getVerificationCaCerts();

    /** Verifies that the conf is up to date. Throws CodedException, if not.
     */
    void verifyUpToDate();

    /**
     * Returns the address of the management request service.
     */
    String getManagementRequestServiceAddress();

    /**
     * Returns the service id of the management request service.
     */
    ClientId getManagementRequestService();
}
