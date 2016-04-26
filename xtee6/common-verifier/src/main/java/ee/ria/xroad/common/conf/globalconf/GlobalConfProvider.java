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
package ee.ria.xroad.common.conf.globalconf;

import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import ee.ria.xroad.common.cert.CertChain;
import ee.ria.xroad.common.conf.ConfProvider;
import ee.ria.xroad.common.identifier.CentralServiceId;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.GlobalGroupId;
import ee.ria.xroad.common.identifier.SecurityCategoryId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;

/**
 * API for implementing global configuration providers.
 */
public interface GlobalConfProvider extends ConfProvider {

    /**
     * Returns true, if the global configuration is valid and can be used
     * for security-critical tasks.
     * Configuration is considered to be valid if all the files of all
     * the instances are up-to-date (not expired).
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
     * Returns concrete service id for the given service id type. If the input
     * is ServiceId, returns it. If the input is CentralServiceId, looks for
     * a mapping to ServiceId in GlobalConf.
     *
     * @param serviceId the service id
     * @return mapped service identifier
     */
    ServiceId getServiceId(CentralServiceId serviceId);

    /**
     * @param instanceIdentifiers the instance identifiers
     * @return members and subsystems of a given instance or all members if
     * no instance identifiers are specified
     */
    List<MemberInfo> getMembers(String... instanceIdentifiers);

    /**
     * @param clientId the client identifier
     * @return member name for the given client identifier
     */
    String getMemberName(ClientId clientId);

    /**
     * @param instanceIdentifier the instance identifier
     * @return all central services for the given instance identifier
     */
    List<CentralServiceId> getCentralServices(String instanceIdentifier);

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
     * Returns address of the given service provider's proxy based on
     * authentication certificate.
     * @param authCert the authentication certificate
     * @return IP address converted to string, such as "192.168.2.2".
     * @throws Exception if an error occurs
     */
    String getProviderAddress(X509Certificate authCert) throws Exception;

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
    SecurityServerId getServerId(X509Certificate cert) throws Exception;

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
      * @param authCert the authentication certificate
      * @return set of codes corresponding to security categories assigned
      * to security server associated with this authentication certificate
      * @throws Exception if an error occurs
      */
    Set<SecurityCategoryId> getProvidedCategories(X509Certificate authCert)
            throws Exception;

    /**
     * @param instanceIdentifier instance identifier
     * @param cert the certificate
     * @return short name of the certificate subject. Short name is used
     * in messages and access checking.
     * @throws Exception if an error occurs
     */
    ClientId getSubjectName(String instanceIdentifier, X509Certificate cert)
            throws Exception;

    /**
     * @param instanceIdentifier the instance identifier
     * @return all approved TSPs for the given instance identifier
     */
    List<String> getApprovedTsps(String instanceIdentifier);

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
     * @param smallestValue if true, the smallest value computed over all
     * known instances is returned. Otherwise the value of the current instance
     * is returned.
     * @return maximum allowed validity time of OCSP responses. If producedAt
     * field of an OCSP response is older than ocspFreshnessSeconds seconds,
     * it is no longer valid.
     */
    int getOcspFreshnessSeconds(boolean smallestValue);

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
    List<SecurityServerId> getSecurityServers(String... instanceIdentifiers);
}
