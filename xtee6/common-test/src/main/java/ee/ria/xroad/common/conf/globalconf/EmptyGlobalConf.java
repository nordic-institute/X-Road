package ee.ria.xroad.common.conf.globalconf;

import java.io.OutputStream;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import ee.ria.xroad.common.cert.CertChain;
import ee.ria.xroad.common.identifier.CentralServiceId;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.GlobalGroupId;
import ee.ria.xroad.common.identifier.SecurityCategoryId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;

/**
 * Implementation of GlobalConfProvider that does nothing but
 * return nulls. You can extend this class and override only the
 * more interesting methods.
 */
public class EmptyGlobalConf implements GlobalConfProvider {

    private static final int DEFAULT_TIMESTAMPING_INTERVAL = 60;
    private static final int DEFAULT_OCSP_FRESHNESS = 3600;

    @Override
    public List<String> getOcspResponderAddresses(X509Certificate org)
            throws Exception {
        return Collections.emptyList();
    }

    @Override
    public List<X509Certificate> getOcspResponderCertificates() {
        return Collections.emptyList();
    }

    @Override
    public X509Certificate getCaCert(String instanceIdentifier,
            X509Certificate orgCert) throws Exception {
        return null;
    }

    @Override
    public List<X509Certificate> getAllCaCerts() {
        return Collections.emptyList();
    }

    @Override
    public boolean isOcspResponderCert(X509Certificate ca,
            X509Certificate ocspCert) {
        return true;
    }

    @Override
    public List<X509Certificate> getTspCertificates()
            throws CertificateException {
        return null;
    }

    @Override
    public List<X509Certificate> getVerificationCaCerts() {
        return null;
    }

    @Override
    public boolean hasChanged() {
        return false;
    }

    @Override
    public Set<String> getKnownAddresses() {
        return Collections.emptySet();
    }

    @Override
    public boolean isSubjectInGlobalGroup(ClientId subject,
            GlobalGroupId group) {
        return false;
    }

    @Override
    public X509Certificate[] getAuthTrustChain() {
        return null;
    }

    @Override
    public void save() throws Exception {
    }

    @Override
    public void save(OutputStream out) throws Exception {
    }

    @Override
    public String getProviderAddress(X509Certificate authCert) {
        return null;
    }

    @Override
    public Collection<String> getProviderAddress(ClientId serviceProvider) {
        return null;
    }

    @Override
    public boolean authCertMatchesMember(X509Certificate cert,
            ClientId memberId) throws Exception {
        return false;
    }

    @Override
    public Set<SecurityCategoryId> getProvidedCategories(
            X509Certificate authCert) {
        return Collections.emptySet();
    }

    @Override
    public ClientId getSubjectName(String instanceIdentifier,
            X509Certificate cert) throws Exception {
        return null;
    }

    @Override
    public String getManagementRequestServiceAddress() {
        return null;
    }

    @Override
    public ClientId getManagementRequestService() {
        return null;
    }

    @Override
    public void load(String fileName) throws Exception {
    }

    @Override
    public ServiceId getServiceId(CentralServiceId serviceId) {
        return null;
    }

    @Override
    public X509Certificate getCentralServerSslCertificate() {
        return null;
    }

    @Override
    public boolean isSecurityServerClient(ClientId client,
            SecurityServerId securityServer) {
        return false;
    }

    @Override
    public CertChain getCertChain(String instanceIdentifier,
            X509Certificate subject) throws Exception {
        return null;
    }

    @Override
    public List<CentralServiceId> getCentralServices(
            String instanceIdentifier) {
        return Collections.emptyList();
    }

    @Override
    public List<MemberInfo> getMembers(String... instanceIdentifier) {
        return Collections.emptyList();
    }

    @Override
    public int getOcspFreshnessSeconds(boolean smallestValue) {
        return DEFAULT_OCSP_FRESHNESS;
    }

    @Override
    public int getTimestampingIntervalSeconds() {
        return DEFAULT_TIMESTAMPING_INTERVAL;
    }

    @Override
    public String getInstanceIdentifier() {
        return null;
    }

    @Override
    public List<String> getInstanceIdentifiers() {
        return Collections.emptyList();
    }

    @Override
    public List<GlobalGroupInfo> getGlobalGroups(String... instanceIdentifier) {
        return Collections.emptyList();
    }

    @Override
    public String getGlobalGroupDescription(GlobalGroupId globalGroupId) {
        return null;
    }

    @Override
    public Set<String> getMemberClasses(String... instanceIdentifier) {
        return Collections.emptySet();
    }

    @Override
    public String getMemberName(ClientId member) {
        return null;
    }

    @Override
    public List<String> getApprovedTsps(String instanceIdentifier) {
        return null;
    }

    @Override
    public String getApprovedTspName(String instanceIdentifier,
            String approvedTspUrl) {
        return null;
    }

    @Override
    public SecurityServerId getServerId(X509Certificate cert) throws Exception {
        return null;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public List<SecurityServerId> getSecurityServers(
            String... instanceIdentifiers) {
        return Collections.emptyList();
    }
}
