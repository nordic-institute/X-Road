package ee.cyber.xroad.mediator;

import java.io.OutputStream;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import ee.ria.xroad.common.cert.CertChain;
import ee.ria.xroad.common.certificateprofile.AuthCertificateProfileInfo;
import ee.ria.xroad.common.certificateprofile.AuthCertificateProfileInfo.Parameters;
import ee.ria.xroad.common.certificateprofile.SignCertificateProfileInfo;
import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;
import ee.ria.xroad.common.conf.globalconf.GlobalGroupInfo;
import ee.ria.xroad.common.conf.globalconf.MemberInfo;
import ee.ria.xroad.common.identifier.CentralServiceId;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.GlobalGroupId;
import ee.ria.xroad.common.identifier.SecurityCategoryId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;

/**
 * Empty test globalconf implementation.
 */
public class EmptyGlobalConf implements GlobalConfProvider {

    private static final int DEFAULT_TIMESTAMPING_INTERVAL = 60;
    private static final int DEFAULT_OCSP_FRESHNESS = 600;

    @Override
    public boolean hasChanged() {
        return false;
    }

    @Override
    public void save() throws Exception {
    }

    @Override
    public void save(OutputStream arg0) throws Exception {
    }

    @Override
    public boolean authCertMatchesMember(X509Certificate arg0, ClientId arg1)
            throws Exception {
        return false;
    }

    @Override
    public List<X509Certificate> getAllCaCerts() {
        return null;
    }

    @Override
    public X509Certificate[] getAuthTrustChain() {
        return null;
    }

    @Override
    public Set<String> getKnownAddresses() {
        return null;
    }

    @Override
    public ClientId getManagementRequestService() {
        return null;
   }

    @Override
    public String getManagementRequestServiceAddress() {
        return null;
    }

    @Override
    public List<String> getOcspResponderAddresses(X509Certificate arg0)
            throws Exception {
        return null;
    }

    @Override
    public List<X509Certificate> getOcspResponderCertificates() {
        return null;
    }

    @Override
    public Set<SecurityCategoryId> getProvidedCategories(X509Certificate arg0)
            throws Exception {
        return null;
    }

    @Override
    public Collection<String> getProviderAddress(ClientId arg0) {
        return null;
    }

    @Override
    public List<X509Certificate> getTspCertificates() throws Exception {
        return null;
    }

    @Override
    public List<X509Certificate> getVerificationCaCerts() {
        return null;
    }

    @Override
    public boolean isSecurityServerClient(ClientId client, SecurityServerId securityServer) {
        return false;
    }

    @Override
    public boolean isOcspResponderCert(X509Certificate arg0,
            X509Certificate arg1) {
        return false;
    }

    @Override
    public boolean isSubjectInGlobalGroup(ClientId arg0, GlobalGroupId arg1) {
        return false;
    }

    @Override
    public void load(String arg0) throws Exception {
    }

    @Override
    public ServiceId getServiceId(CentralServiceId arg0) {
        return null;
    }

    @Override
    public CertChain getCertChain(String instanceIdentifier,
            X509Certificate subject) throws Exception {
        return null;
    }

    @Override
    public X509Certificate getCentralServerSslCertificate() throws Exception {
        return null;
    }

    @Override
    public List<CentralServiceId> getCentralServices(String arg0) {
        return null;
    }

    @Override
    public int getOcspFreshnessSeconds(boolean arg0) {
        return DEFAULT_OCSP_FRESHNESS;
    }

    @Override
    public int getTimestampingIntervalSeconds() {
        return DEFAULT_TIMESTAMPING_INTERVAL;
    }

    @Override
    public String getApprovedTspName(String arg0, String arg1) {
        return null;
    }

    @Override
    public List<String> getApprovedTsps(String arg0) {
        return null;
    }

    @Override
    public X509Certificate getCaCert(String arg0, X509Certificate arg1)
            throws Exception {
        return null;
    }

    @Override
    public String getGlobalGroupDescription(GlobalGroupId arg0) {
        return null;
    }

    @Override
    public List<GlobalGroupInfo> getGlobalGroups(String... arg0) {
        return null;
    }

    @Override
    public String getInstanceIdentifier() {
        return null;
    }

    @Override
    public List<String> getInstanceIdentifiers() {
        return null;
    }

    @Override
    public Set<String> getMemberClasses(String... arg0) {
        return null;
    }

    @Override
    public String getMemberName(ClientId arg0) {
        return null;
    }

    @Override
    public List<MemberInfo> getMembers(String... instanceIdentifiers) {
        return null;
    }

    @Override
    public SecurityServerId getServerId(X509Certificate arg0) throws Exception {
        return null;
    }

    @Override
    public boolean isValid() {
        return true;
    }

    @Override
    public List<SecurityServerId> getSecurityServers(
            String... instanceIdentifiers) {
        return null;
    }

    @Override
    public AuthCertificateProfileInfo getAuthCertificateProfileInfo(
            Parameters arg0, X509Certificate arg1) throws Exception {
        return null;
    }

    @Override
    public SignCertificateProfileInfo getSignCertificateProfileInfo(
            ee.ria.xroad.common.certificateprofile.SignCertificateProfileInfo.Parameters arg0,
            X509Certificate arg1) throws Exception {
        return null;
    }

}
