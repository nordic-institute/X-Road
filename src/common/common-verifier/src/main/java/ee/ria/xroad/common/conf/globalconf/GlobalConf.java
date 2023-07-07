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
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.cert.CertChain;
import ee.ria.xroad.common.certificateprofile.AuthCertificateProfileInfo;
import ee.ria.xroad.common.certificateprofile.SignCertificateProfileInfo;
import ee.ria.xroad.common.conf.globalconf.sharedparameters.v2.ApprovedTSAType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.GlobalGroupId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import lombok.extern.slf4j.Slf4j;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static ee.ria.xroad.common.ErrorCodes.X_OUTDATED_GLOBALCONF;

/**
 * Global configuration.
 */
@Slf4j
public final class GlobalConf {

    private static volatile GlobalConfProvider instance;

    private GlobalConf() {
    }

    /**
     * Returns the singleton instance of the configuration.
     */
    static GlobalConfProvider getInstance() {
        if (instance == null) {
            synchronized (GlobalConf.class) {
                if (instance == null) {
                    instance = new GlobalConfImpl();
                }
            }
        }
        return instance;
    }

    /**
     * Reloads the configuration.
     */
    public static void reload() {
        log.debug("reload called");
        if (instance == null) {
            log.debug("creating instance");
            getInstance();
        } else {
            log.debug("reloading instance");
            instance.reload();
        }
    }

    /**
     * Reloads the configuration with given configuration instance.
     * Used in tests. DO NOT USE in other circumstances.
     * @param conf the configuration provider instance
     */
    public static void reload(GlobalConfProvider conf) {
        log.trace("reload called with parameter class {}", conf.getClass());
        synchronized (GlobalConf.class) {
            instance = conf;
        }
    }

    /**
     * Resets global configuration to empty.
     * Used in tests. DO NOT USE in other circumstances.
     */
    public static void reset() {
        log.trace("reset called");
        synchronized (GlobalConf.class) {
            instance = null;
        }
    }

    // ------------------------------------------------------------------------

    /**
     * Returns an absolute file name for the current instance.
     * @param fileName the file name
     * @return the absolute path to the file of the current instance
     */
    public static Path getInstanceFile(String fileName) {
        return getFile(getInstanceIdentifier(), fileName);
    }

    /**
     * Returns an absolute file name for the specified instance.
     * @param instanceIdentifier the instance identifier
     * @param fileName the file name
     * @return the absolute path to the file of the specified instance
     */
    public static Path getFile(String instanceIdentifier, String fileName) {
        return Paths.get(SystemProperties.getConfigurationPath(),
                instanceIdentifier, fileName);
    }

    // ------------------------------------------------------------------------

    /**
     * Verifies that the global configuration is valid. Throws exception
     * with error code ErrorCodes.X_OUTDATED_GLOBALCONF if the it is too old.
     */
    public static void verifyValidity() {
        if (!isValid()) {
            throw new CodedException(X_OUTDATED_GLOBALCONF,
                    "Global configuration is expired");
        }
    }

    /**
     * Returns true, if the global configuration is valid and can be used
     * for security-critical tasks.
     * Configuration is considered to be valid if all the files of all
     * the instances are up-to-date (not expired).
     * @return true if the global configuration is valid
     */
    public static boolean isValid() {
        GlobalConfProvider provider = getInstance();
        if (provider == null) {
            return false;
        }
        return provider.isValid();
    }

    /**
     * @return the instance identifier for this configuration source
     */
    public static String getInstanceIdentifier() {
        log.trace("getInstanceIdentifier()");

        return getInstance().getInstanceIdentifier();
    }

    /**
     * @return the instance identifiers for all configuration sources
     */
    public static List<String> getInstanceIdentifiers() {
        log.trace("getInstanceIdentifiers()");

        return getInstance().getInstanceIdentifiers();
    }

    /**
     * @param instanceIdentifiers the instance identifiers
     * @return members and subsystems of a given instance, or all members and subsystems if
     * no instance identifiers are specified
     */
    public static List<MemberInfo> getMembers(String... instanceIdentifiers) {
        log.trace("getMembers({})", (Object[]) instanceIdentifiers);

        return getInstance().getMembers(instanceIdentifiers);
    }

    /**
     * @param clientId the client identifier
     * @return member name for the given client identifier, or null if member does
     * not exist in global conf
     */
    public static String getMemberName(ClientId clientId) {
        log.trace("getMemberName({})", clientId);

        return getInstance().getMemberName(clientId);
    }

    /**
     * @param instanceIdentifiers the optional instance identifiers
     * @return global groups of a given instance or all global groups if no
     * instance identifiers are specified
     */
    public static List<GlobalGroupInfo> getGlobalGroups(
            String... instanceIdentifiers) {
        log.trace("getGlobalGroups({})", (Object[]) instanceIdentifiers);

        return getInstance().getGlobalGroups(instanceIdentifiers);
    }

    /**
     * @param globalGroupId the global group identifier
     * @return global group description for the given global group identifier
     */
    public static String getGlobalGroupDescription(
            GlobalGroupId globalGroupId) {
        log.trace("getGlobalGroupDescription({})", globalGroupId);

        return getInstance().getGlobalGroupDescription(globalGroupId);
    }

    /**
     * @param instanceIdentifiers the optional instance identifiers
     * @return member classes for given instance or all member classes if
     * no instance identifiers are specified
     */
    public static Set<String> getMemberClasses(String... instanceIdentifiers) {
        log.trace("getMemberClasses({})", (Object[]) instanceIdentifiers);

        return getInstance().getMemberClasses(instanceIdentifiers);
    }

    /**
     * Returns address of the given service provider's proxy.
     * @param serviceProvider the service provider identifier
     * @return IP address converted to string, such as "192.168.2.2".
     */
    public static Collection<String> getProviderAddress(
            ClientId serviceProvider) {
        log.trace("getProviderAddress({})", serviceProvider);

        return getInstance().getProviderAddress(serviceProvider);
    }

    /**
     * Returns address of the given service provider's proxy.
     * @return IP address converted to string, such as "192.168.2.2".
     */
    public static String getSecurityServerAddress(
            SecurityServerId serverId) {
        log.trace("getSecurityServerAddress({})", serverId);

        return getInstance().getSecurityServerAddress(serverId);
    }

    /**
     * Returns a list of OCSP responder addresses for the given member
     * certificate.
     * @param member the member certificate
     * @return list of OCSP responder addresses
     * @throws Exception if an error occurs
     */
    public static List<String> getOcspResponderAddresses(
            X509Certificate member) throws Exception {
        log.trace("getOcspResponderAddresses({})", member != null
                ? member.getSubjectX500Principal().getName() : "null");

        return getInstance().getOcspResponderAddresses(member);
    }

    /**
     * Returns a list of OCSP responder addresses for the given CA certificate.
     * @param caCertificate the CA certificate
     * @return list of OCSP responder addresses
     * @throws Exception if an error occurs
     */
    public static List<String> getOcspResponderAddressesForCaCertificate(
            X509Certificate caCertificate) throws Exception {
        log.trace("getOcspResponderAddressesForCaCertificate({})", caCertificate != null
                ? caCertificate.getSubjectX500Principal().getName() : "null");

        return getInstance().getOcspResponderAddressesForCaCertificate(caCertificate);
    }

    /**
     * @return a list of known OCSP responder certificates
     */
    public static List<X509Certificate> getOcspResponderCertificates() {
        log.trace("getOcspResponderCertificates()");

        return getInstance().getOcspResponderCertificates();
    }

    /**
     * @param instanceIdentifier the instance identifier
     * @param subject the member certificate
     * @return the issuer certificate for the member certificate
     * @throws Exception if an error occurs
     */
    public static X509Certificate getCaCert(String instanceIdentifier,
            X509Certificate subject) throws Exception {
        log.trace("getCaCert({}, {})", instanceIdentifier,
                subject.getSubjectX500Principal().getName());

        return getInstance().getCaCert(instanceIdentifier, subject);
    }

    /**
     * @return a list of all CA certificates
     */
    public static Collection<X509Certificate> getAllCaCerts() {
        log.trace("getAllCaCerts()");

        return getInstance().getAllCaCerts();
    }

    /**
     * @return a list of all CA certificates for a given instance
     */
    public static Collection<X509Certificate> getAllCaCerts(String instanceIdentifier) {
        log.trace("getAllCaCerts()");

        return getInstance().getAllCaCerts(instanceIdentifier);
    }

    /**
     * @param instanceIdentifier the instance identifier
     * @param subject the subject certificate
     * @return the top CA and any intermediate CA certificates for a
     * given end entity
     * @throws Exception if an error occurs
     */
    public static CertChain getCertChain(String instanceIdentifier,
            X509Certificate subject) throws Exception {
        log.trace("getCertChain({}, {})", instanceIdentifier,
                subject.getSubjectX500Principal().getName());

        return getInstance().getCertChain(instanceIdentifier, subject);
    }

    /**
     * @param ca the CA certificate
     * @param ocspCert the OCSP certificate
     * @return true, if the CA has the specified OCSP responder certificate,
     * false otherwise
     */
    public static boolean isOcspResponderCert(X509Certificate ca,
            X509Certificate ocspCert) {
        log.trace("isOcspResponderCert({}, {})",
                ca.getSubjectX500Principal().getName(),
                ocspCert.getSubjectX500Principal().getName());

        return getInstance().isOcspResponderCert(ca, ocspCert);
    }

    /**
     * @return all the trusted and intermediate certificates that can be used
     * to verify the other party in establishing SSL connection
     */
    public static X509Certificate[] getAuthTrustChain() {
        log.trace("getAuthTrustChain()");

        return getInstance().getAuthTrustChain();
    }

    /**
     * @param cert the authentication certificate
     * @return the security server id for the given authentication certificate
     * of null of the authentication certificate does not map to any security
     * server.
     * @throws Exception if an error occurs
     */
    public static SecurityServerId getServerId(X509Certificate cert)
            throws Exception {
        log.trace("getServerId({})", cert.getSubjectDN());

        return getInstance().getServerId(cert);
    }

    /**
     * Checks if the authentication certificate belongs to registered
     * security server
     * @param cert the authentication certificate
     * @return true if the authentication certificate belongs to registered
     * security server
     */
    public static boolean isSecurityServerAuthCert(X509Certificate cert) {
        log.trace("isSecurityServerAuthCert({})", cert.getSubjectDN());
        try {
            return getInstance().getServerId(cert) != null;
        } catch (Exception e) {
            log.error("Error occurred while getting server id", e);
            return false;
        }
    }

    /**
     * @param serverId the security server id
     * @return the client id that owns the security server with the specified id
     * or null if the given id does not match an existing server
     * @throws Exception if an error occurs
     */
    public static ClientId getServerOwner(SecurityServerId serverId) {
        return getInstance().getServerOwner(serverId);
    }

    /**
     * @param cert the certificate
     * @param memberId the member identifier
     * @return true, if cert can be used to authenticate as
     * member member
     * @throws Exception if an error occurs
     */
    public static boolean authCertMatchesMember(X509Certificate cert,
            ClientId memberId) throws Exception {
        log.trace("authCertMatchesMember({}: {}, {})",
                cert.getSerialNumber(), cert.getSubjectDN(),
                memberId);

        return getInstance().authCertMatchesMember(cert, memberId);
    }

    /**
     * @param instanceIdentifier the instance identifier
     * @return all known approved CAs
     */
    public static Collection<ApprovedCAInfo> getApprovedCAs(
            String instanceIdentifier) {
        log.trace("getApprovedCAs()");

        return getInstance().getApprovedCAs(instanceIdentifier);
    }

    /**
     * @param parameters the parameters
     * @param cert the certificate
     * @return authentication certificate profile info for this certificate
     * @throws Exception if an error occurs
     */
    public static AuthCertificateProfileInfo getAuthCertificateProfileInfo(
            AuthCertificateProfileInfo.Parameters parameters,
            X509Certificate cert) throws Exception {
        log.trace("getAuthCertificateProfileInfo({}, {})",
                parameters.getServerId(),
                cert.getSubjectX500Principal());

        return getInstance().getAuthCertificateProfileInfo(parameters, cert);
    }

    /**
     * @param parameters the parameters
     * @param cert the certificate
     * @return signing certificate profile info for this certificate
     * @throws Exception if an error occurs
     */
    public static SignCertificateProfileInfo getSignCertificateProfileInfo(
            SignCertificateProfileInfo.Parameters parameters,
            X509Certificate cert) throws Exception {
        log.trace("getSignCertificateProfileInfo({}, {})",
                parameters.getClientId(),
                cert.getSubjectX500Principal());

        return getInstance().getSignCertificateProfileInfo(parameters, cert);
    }

    /**
     * @param parameters the parameters
     * @param cert the signing certificate
     * @return subject client identifier
     * @throws Exception if an error occurs
     */
    public static ClientId.Conf getSubjectName(
            SignCertificateProfileInfo.Parameters parameters,
            X509Certificate cert) throws Exception {
        log.trace("getSubjectName({})", parameters.getClientId());

        return getSignCertificateProfileInfo(parameters, cert)
                .getSubjectIdentifier(cert);
    }

    /**
     * @param instanceIdentifier the instance identifier
     * @return all approved TSPs for the given instance identifier
     */
    public static List<String> getApprovedTsps(String instanceIdentifier) {
        log.trace("getApprovedTsps({})", instanceIdentifier);

        return getInstance().getApprovedTsps(instanceIdentifier);
    }

    /**
     * @param instanceIdentifier the instance identifier
     * @return all approved TSP types for the given instance identifier
     */
    public static List<ApprovedTSAType> getApprovedTspTypes(String instanceIdentifier) {
        return getInstance().getApprovedTspTypes(instanceIdentifier);
    }

    /**
     * @param instanceIdentifier the instance identifier
     * @param approvedTspUrl the TSP url
     * @return approved TSP name for the given instance identifier and TSP
     * url
     */
    public static String getApprovedTspName(String instanceIdentifier,
            String approvedTspUrl) {
        log.trace("getApprovedTspName({}, {})", instanceIdentifier,
                approvedTspUrl);

        return getInstance().getApprovedTspName(instanceIdentifier,
                approvedTspUrl);
    }

    /**
     * @return the list of TSP certificates
     * @throws Exception if an error occurs
     */
    public static List<X509Certificate> getTspCertificates() throws Exception {
        log.trace("getTspCertificates()");

        return getInstance().getTspCertificates();
    }

    /**
     * @return all addresses of all members
     */
    public static Set<String> getKnownAddresses() {
        log.trace("getKnownAddresses()");

        return getInstance().getKnownAddresses();
    }

    /**
     * @param subject the client identifier
     * @param group the global group
     * @return true, if given subject belongs to given global group
     */
    public static boolean isSubjectInGlobalGroup(ClientId subject,
            GlobalGroupId group) {
        log.trace("isSubjectInGlobalGroup({}, {})", subject, group);

        return getInstance().isSubjectInGlobalGroup(subject, group);
    }

    /**
     * @param client the client identifier
     * @param securityServer the security server identifier
     * @return true, if client belongs to the security server
     */
    public static boolean isSecurityServerClient(ClientId client,
            SecurityServerId securityServer) {
        log.trace("isSecurityServerClient({}, {})", client, securityServer);

        return getInstance().isSecurityServerClient(client, securityServer);
    }

    /**
     * @param serverId the security server id
     * @return true, if the given security server id exists.
     */
    public static boolean existsSecurityServer(SecurityServerId serverId) {
        log.trace("existsSecurityServer({})", serverId);

        return getInstance().existsSecurityServer(serverId);
    }

    /**
     * @return time (in seconds) where validity information is considered valid.
     * After that time, OCSP responses must be refreshed.
     */
    public static int getOcspFreshnessSeconds() {
        log.trace("getOcspFreshnessSeconds()");

        final int ocspFreshnessSeconds = getInstance().getOcspFreshnessSeconds();
        log.trace("ocspFreshnessSeconds={}", ocspFreshnessSeconds);
        return ocspFreshnessSeconds;
    }

    /**
     * @return all CA certificates that are suitable for verification, that is
     * pki elements that are not marked authenticationOnly
     */
    public static List<X509Certificate> getVerificationCaCerts() {
        log.trace("getVerificationCaCerts()");
        return getInstance().getVerificationCaCerts();
    }

    /**
     * @return the address of the management request service
     */
    public static String getManagementRequestServiceAddress() {
        log.trace("getManagementRequestServiceAddress()");

        return getInstance().getManagementRequestServiceAddress();
    }

    /**
     * @return the service id of the management request service
     */
    public static ClientId getManagementRequestService() {
        log.trace("getManagementRequestServiceAddress()");

        return getInstance().getManagementRequestService();
    }

    /**
     * @return SSL certificates of central servers
     * @throws Exception if an error occurs
     */
    public static X509Certificate getCentralServerSslCertificate()
            throws Exception {
        log.trace("getCentralServerSslCertificate()");

        return getInstance().getCentralServerSslCertificate();
    }

    /**
     * @return the timestamping interval in seconds
     */
    public static int getTimestampingIntervalSeconds() {
        log.trace("getTimestampingIntervalSeconds()");

        return getInstance().getTimestampingIntervalSeconds();
    }

    /**
     * @param instanceIdentifiers the instance identifiers
     * @return security server identifiers of the specified instance identifiers
     * or all security server identifiers if no instance identifiers are
     * specified
     */
    public static List<SecurityServerId.Conf> getSecurityServers(
            String... instanceIdentifiers) {
        log.trace("getSecurityServers()");

        return getInstance().getSecurityServers(instanceIdentifiers);
    }

    /**
     * Get ApprovedCAInfo matching given CA certificate
     * @param instanceIdentifier instance id
     * @param cert intermediate or top CA cert
     * @return ApprovedCAInfo (for the top CA)
     * @throws CodedException if something went wrong, for example
     * {@code cert} was not an approved CA cert
     */
    public static ApprovedCAInfo getApprovedCA(
            String instanceIdentifier, X509Certificate cert) throws CodedException {
        log.trace("getApprovedCA()");

        return getInstance().getApprovedCA(instanceIdentifier, cert);
    }
}
