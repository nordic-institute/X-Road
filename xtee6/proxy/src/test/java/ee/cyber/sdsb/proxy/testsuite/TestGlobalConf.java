package ee.cyber.sdsb.proxy.testsuite;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.TestCertUtil;
import ee.cyber.sdsb.common.cert.CertChain;
import ee.cyber.sdsb.common.cert.CertHelper;
import ee.cyber.sdsb.common.conf.globalconf.EmptyGlobalConf;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.SecurityCategoryId;

import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;

public class TestGlobalConf extends EmptyGlobalConf {

    @Override
    public String getInstanceIdentifier() {
        return "EE";
    }

    @Override
    public Collection<String> getProviderAddress(ClientId provider) {
        if (currentTestCase() == null || provider == null) {
            return singleton("http://127.0.0.1:"
                    + SystemProperties.getServerProxyPort());
        }

        String addr = currentTestCase().getProviderAddress(
                provider.getMemberCode());
        if (addr == null) {
            return emptySet();
        } else {
            return singleton(addr);
        }
    }

    @Override
    public Set<SecurityCategoryId> getProvidedCategories(
            X509Certificate authCert) {
        return currentTestCase().getProvidedCategories();
    }

    @Override
    public List<X509Certificate> getOcspResponderCertificates() {
        try {
            return Arrays.asList(TestCertUtil.getOcspSigner().cert);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    @Override
    public X509Certificate[] getAuthTrustChain() {
        return new X509Certificate[] { TestCertUtil.getCaCert() };
    }

    private MessageTestCase currentTestCase() {
        return ProxyTestSuite.currentTestCase;
    }

    @Override
    public X509Certificate getCaCert(String instanceIdentifier,
            X509Certificate org) throws Exception {
        return TestCertUtil.getCaCert();
    }

    @Override
    public CertChain getCertChain(String instanceIdentifier,
            X509Certificate subject) throws Exception {
        // TODO: Also add intermediate certs based on the subject...
        return CertChain.create(instanceIdentifier, subject, null);
    }

    @Override
    public List<X509Certificate> getTspCertificates()
            throws CertificateException {
        return Arrays.asList(TestCertUtil.getTspCert());
    }

    @Override
    public ClientId getSubjectName(String instancedentifier,
            X509Certificate cert) throws Exception {
        String commonName = CertHelper.getSubjectCommonName(cert);
        return ClientId.create("EE", "BUSINESS", commonName);
    }

    @Override
    public boolean authCertMatchesMember(X509Certificate cert, ClientId memberId)
            throws Exception {
        return true;
    }
}
