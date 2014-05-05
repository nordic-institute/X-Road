package ee.cyber.sdsb.common;

import java.io.OutputStream;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import ee.cyber.sdsb.common.conf.GlobalConfProvider;
import ee.cyber.sdsb.common.conf.VerificationCtx;
import ee.cyber.sdsb.common.identifier.CentralServiceId;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.GlobalGroupId;
import ee.cyber.sdsb.common.identifier.SecurityCategoryId;
import ee.cyber.sdsb.common.identifier.SecurityServerId;
import ee.cyber.sdsb.common.identifier.ServiceId;

/**
 * Implementation of GlobalConfProvider that does nothing but
 * return nulls. You can extend this class and override only the
 * more interesting methods.
 */
public class EmptyGlobalConf implements GlobalConfProvider {

    @Override
    public VerificationCtx getVerificationCtx() {
        return null;
    }

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
    public X509Certificate getCaCert(X509Certificate orgCert) throws Exception {
        return null;
    }

    @Override
    public List<X509Certificate> getAllCaCerts() throws CertificateException {
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
    public void verifyUpToDate() {
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
    public boolean hasAuthCert(X509Certificate cert, SecurityServerId server)
            throws Exception {
        return false;
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
    public ClientId getSubjectName(X509Certificate cert) throws Exception {
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
}
