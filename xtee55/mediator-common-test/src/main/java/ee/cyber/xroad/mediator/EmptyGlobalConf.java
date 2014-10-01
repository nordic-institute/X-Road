package ee.cyber.xroad.mediator;

import java.io.OutputStream;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import ee.cyber.sdsb.common.cert.CertChain;
import ee.cyber.sdsb.common.conf.GlobalConfProvider;
import ee.cyber.sdsb.common.identifier.CentralServiceId;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.GlobalGroupId;
import ee.cyber.sdsb.common.identifier.SecurityCategoryId;
import ee.cyber.sdsb.common.identifier.SecurityServerId;
import ee.cyber.sdsb.common.identifier.ServiceId;

public class EmptyGlobalConf implements GlobalConfProvider {

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
    public List<X509Certificate> getAllCaCerts() throws CertificateException {
        return null;
    }

    @Override
    public X509Certificate[] getAuthTrustChain() {
        return null;
    }

    @Override
    public X509Certificate getCaCert(X509Certificate arg0) throws Exception {
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
    public String getProviderAddress(X509Certificate arg0) throws Exception {
        return null;
    }

    @Override
    public Collection<String> getProviderAddress(ClientId arg0) {
        return null;
    }

    @Override
    public ClientId getSubjectName(X509Certificate arg0) throws Exception {
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
    public boolean hasAuthCert(X509Certificate arg0, SecurityServerId arg1)
            throws Exception {
        return false;
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
    public void verifyUpToDate() {
    }

    @Override
    public void load(String arg0) throws Exception {
    }

    @Override
    public ServiceId getServiceId(CentralServiceId arg0) {
        return null;
    }

    @Override
    public String getSdsbInstance() {
        return null;
    }

    @Override
    public CertChain getCertChain(X509Certificate subject) throws Exception {
        return null;
    }

    @Override
    public X509Certificate getCentralServerSslCertificate() throws Exception {
        return null;
    }

    @Override
    public List<CentralServiceId> getCentralServices() {
        return null;
    }

    @Override
    public List<ClientId> getMembers() {
        return null;
    }
}
