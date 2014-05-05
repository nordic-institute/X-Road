package ee.cyber.sdsb.proxy.testsuite;

import java.io.OutputStream;
import java.security.cert.X509Certificate;
import java.util.List;
import java.util.Set;

import org.bouncycastle.cert.ocsp.OCSPResp;

import ee.cyber.sdsb.common.conf.InternalSSLKey;
import ee.cyber.sdsb.common.conf.IsAuthentication;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.SecurityCategoryId;
import ee.cyber.sdsb.common.identifier.SecurityServerId;
import ee.cyber.sdsb.common.identifier.ServiceId;
import ee.cyber.sdsb.proxy.conf.ServerConfProvider;

import static java.util.Collections.emptyList;
import static java.util.Collections.emptySet;

/**
 * Implementation of ServerConfProvider that does nothing but return nulls. You
 * can extend this class and override only the more interesting methods.
 */
public class EmptyServerConf implements ServerConfProvider {

    @Override
    public List<X509Certificate> getMemberCerts() throws Exception {
        return emptyList();
    }

    @Override
    public boolean isCachedOcspResponse(String certHash) throws Exception {
        return false;
    }

    @Override
    public void setOcspResponse(String certHash, OCSPResp response)
            throws Exception {
    }

    @Override
    public List<X509Certificate> getCertsForOcsp() throws Exception {
        return getMemberCerts();
    }

    @Override
    public OCSPResp getOcspResponse(X509Certificate cert) throws Exception {
        return null;
    }

    @Override
    public boolean hasChanged() {
        return false;
    }

    @Override
    public String getGlobalConfDistributorUrl() {
        return null;
    }

    @Override
    public X509Certificate getGlobalConfVerificationCert() throws Exception {
        return null;
    }

    @Override
    public void save() throws Exception {
    }

    @Override
    public void save(OutputStream out) throws Exception {
    }

    @Override
    public boolean serviceExists(ServiceId service) {
        return true;
    }

    @Override
    public boolean isQueryAllowed(ClientId sender, ServiceId service) {
        return true;
    }

    @Override
    public String getDisabledNotice(ServiceId service) {
        return null;
    }

    @Override
    public String getServiceAddress(ServiceId service) {
        return null;
    }

    @Override
    public Set<SecurityCategoryId> getRequiredCategories(ServiceId service) {
        return emptySet();
    }

    @Override
    public SecurityServerId getIdentifier() {
        return null;
    }

    @Override
    public void load(String fileName) throws Exception {
    }

    @Override
    public int getServiceTimeout(ServiceId service) {
        return 300;
    }

    @Override
    public IsAuthentication getIsAuthentication(ClientId client) {
        return null;
    }

    @Override
    public List<X509Certificate> getIsCerts(ClientId client) throws Exception {
        return null;
    }

    @Override
    public InternalSSLKey getSSLKey() throws Exception {
        return null;
    }

    @Override
    public List<String> getTspUrl() {
        return emptyList();
    }
}
