/**
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.common.conf.globalconf;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.cert.CertChain;
import ee.ria.xroad.common.certificateprofile.AuthCertificateProfileInfo;
import ee.ria.xroad.common.certificateprofile.SignCertificateProfileInfo;
import ee.ria.xroad.common.conf.globalconf.sharedparameters.v2.ApprovedTSAType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.GlobalGroupId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * API for implementing global configuration providers.
 */
public interface GlobalConfProvider {

    /**
     * Reloads configuration from disk
     */
    void reload();

    /**
     * Returns true, if the global configuration is valid and can be used
     * for security-critical tasks.
     * Configuration is considered valid if main/home instance parameteres are valid
     * @return true if the global configuration is valid
     */
    boolean isValid();

    /**
     * @return the instance identifier for this configuration source
     */
    String getInstanceIdentifier();

    /**
     * @return the instance identifiers for all configuration sources
     */
    List<String> getInstanceIdentifiers();

    /**
     * @param instanceIdentifiers the instance identifiers
     * @return members and subsystems of a given instance, or all members and subsystems if
     * no instance identifiers are specified
     */
    List<MemberInfo> getMembers(String... instanceIdentifiers);

    /**
     * @param clientId the client identifier
     * @return member name for the given client identifier
     */
    String getMemberName(ClientId clientId);

    /**
     * @param instanceIdentifiers the optional instance identifiers
     * @return global groups of a given instance or all global groups if no
     * instance identifiers are specified
     */
    List<GlobalGroupInfo> getGlobalGroups(String... instanceIdentifiers);

    /**
     * @param globalGroupId the global group identifier
     * @return global group description for the given global group identifier
     */
    String getGlobalGroupDescription(GlobalGroupId globalGroupId);

    /**
     * @param instanceIdentifiers the optional instance identifiers
     * @return member classes for given instance or all member classes if
     * no instance identifiers are specified
     */
    Set<String> getMemberClasses(String... instanceIdentifiers);

    /**
     * Returns address of the given service provider's proxy.
     * @param serviceProvider the service provider identifier
     * @return IP address converted to string, such as "192.168.2.2".
     */
    Collection<String> getProviderAddress(ClientId serviceProvider);

    /**
     * Returns address of the given security server
     * @param serverId the security server identifier
     * @return IP address converted to string, such as "192.168.2.2".
     */
    String getSecurityServerAddress(SecurityServerId serverId);

    /**
     * Returns a list of OCSP responder addresses for the given member
     * certificate.
     * @param member the member certificate
     * @return list of OCSP responder addresses
     * @throws Exception if an error occurs
     */
    List<String> getOcspResponderAddresses(X509Certificate member)
            throws Exception;


    /** Returns a list of OCSP responder addresses for the given CA certificate
     * @param caCert the CA certificate
     * @return list of OCSP responder addresses
     * @throws Exception if an error occurs
     */
    List<String> getOcspResponderAddressesForCaCertificate(X509Certificate caCert)
            throws Exception;

    /**
     * @return a list of known OCSP responder certificates
     */
    List<X509Certificate> getOcspResponderCertificates();

    /**
     * @param instanceIdentifier the instance identifier
     * @param memberCert the member certificate
     * @return the issuer certificate for the member certificate
     * @throws Exception if an error occurs
     */
    X509Certificate getCaCert(String instanceIdentifier,
                              X509Certificate memberCert) throws Exception;

    /**
     * @return all CA certificates.
     */
    List<X509Certificate> getAllCaCerts();

    /**
     * @return all CA certificates for a given instance
     */
    List<X509Certificate> getAllCaCerts(String instanceIdentifier);

    /**
     * @param instanceIdentifier the instance identifier
     * @param subject the subject certificate
     * @return the top CA and any intermediate CA certificates for a
     * given end entity
     * @throws Exception if an error occurs
     */
    CertChain getCertChain(String instanceIdentifier, X509Certificate subject)
            throws Exception;

    /**
     * @param ca the CA certificate
     * @param ocspCert the OCSP certificate
     * @return true, if the CA has the specified OCSP responder certificate,
     * false otherwise
     */
    boolean isOcspResponderCert(X509Certificate ca, X509Certificate ocspCert);

    /**
     * @return all the trusted and intermediate certificates that can be used
     * to verify the other party in establishing SSL connection.
     */
    X509Certificate[] getAuthTrustChain();

    /**
     * @param cert the authentication certificate
     * @return the security server id for the given authentication certificate
     * of null of the authentication certificate does not map to any security
     * server.
     * @throws Exception if an error occurs
     */
    SecurityServerId.Conf getServerId(X509Certificate cert) throws Exception;

    /**
     * @param serverId the security server id
     * @return the client id that owns the security server with the specified id
     * or null if the given id does not match an existing server
     */
    ClientId.Conf getServerOwner(SecurityServerId serverId);

    /**
     * @param cert the certificate
     * @param memberId the member identifier
     * @return true, if cert can be used to authenticate as
     * member member
     * @throws Exception if an error occurs
     */
    boolean authCertMatchesMember(X509Certificate cert, ClientId memberId)
            throws Exception;

    /**
     * @param instanceIdentifier the instance identifier
     * @return all known approved CAs
     */
    default Collection<ApprovedCAInfo> getApprovedCAs(
            String instanceIdentifier) {
        return Collections.emptyList();
    }

    /**
     * @param parameters the authentication certificate profile info parameters
     * @param cert the certificate
     * @return auth certificate profile info for this certificate
     * @throws Exception if an error occurs
     */
    AuthCertificateProfileInfo getAuthCertificateProfileInfo(
            AuthCertificateProfileInfo.Parameters parameters,
            X509Certificate cert) throws Exception;

    /**
     * @param parameters the signing certificate profile info parameters
     * @param cert the certificate
     * @return signing certificate profile info for this certificate
     * @throws Exception if an error occurs
     */
    SignCertificateProfileInfo getSignCertificateProfileInfo(
            SignCertificateProfileInfo.Parameters parameters,
            X509Certificate cert) throws Exception;

    /**
     * @param instanceIdentifier the instance identifier
     * @return all approved TSPs for the given instance identifier
     */
    List<String> getApprovedTsps(String instanceIdentifier);

    /**
     * @param instanceIdentifier the instance identifier
     * @return all approved TSP types for the given instance identifier
     */
    List<ApprovedTSAType> getApprovedTspTypes(String instanceIdentifier);

    /**
     * @param instanceIdentifier the instance identifier
     * @param approvedTspUrl the TSP url
     * @return approved TSP name for the given instance identifier and TSP
     * url
     */
    String getApprovedTspName(String instanceIdentifier, String approvedTspUrl);

    /**
     * @return the list of TSP certificates
     * @throws Exception if an error occurs
     */
    List<X509Certificate> getTspCertificates() throws Exception;

    /**
     * @return all addresses of all members
     */
    Set<String> getKnownAddresses();

    /**
     * @param subject the client identifier
     * @param group the global group
     * @return true, if given subject belongs to given global group
     */
    boolean isSubjectInGlobalGroup(ClientId subject, GlobalGroupId group);

    /**
     * @param client the client identifier
     * @param securityServer the security server identifier
     * @return true, if client belongs to the security server
     */
    boolean isSecurityServerClient(ClientId client,
                                   SecurityServerId securityServer);

    /**
     * @param securityServerId the security server id
     * @return true, if the given security server id exists
     */
    boolean existsSecurityServer(SecurityServerId securityServerId);

    /**
     * @return all CA certificates that are suitable for verification, that is
     * pki elements that are not marked authenticationOnly
     */
    List<X509Certificate> getVerificationCaCerts();

    /**
     * @return the address of the management request service
     */
    String getManagementRequestServiceAddress();

    /**
     * @return the service id of the management request service
     */
    ClientId getManagementRequestService();

    /**
     * @return SSL certificates of central servers
     * @throws Exception if an error occurs
     */
    X509Certificate getCentralServerSslCertificate() throws Exception;

    /**
     * @return maximum allowed validity time of OCSP responses. If thisUpdate
     * field of an OCSP response is older than ocspFreshnessSeconds seconds,
     * it is no longer valid.
     */
    int getOcspFreshnessSeconds();

    /**
     * @return the timestamping interval in seconds
     */
    int getTimestampingIntervalSeconds();

    /**
     * @param instanceIdentifiers the instance identifiers
     * @return security server identifiers of the specified instance identifiers
     * or all security server identifiers if no instance identifiers are
     * specified
     */
    List<SecurityServerId.Conf> getSecurityServers(String... instanceIdentifiers);

    /**
     * Get ApprovedCAInfo matching given CA certificate
     * @param instanceIdentifier instance id
     * @param cert intermediate or top CA cert
     * @return ApprovedCAInfo (for the top CA)
     * @throws CodedException if something went wrong, for example
     * {@code cert} was not an approved CA cert
     */
    ApprovedCAInfo getApprovedCA(String instanceIdentifier, X509Certificate cert) throws CodedException;
}
