package ee.cyber.sdsb.proxy.testsuite;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import ee.cyber.sdsb.common.TestCertUtil;
import ee.cyber.sdsb.common.cert.CertChain;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.SecurityCategoryId;
import ee.cyber.sdsb.proxy.EmptyGlobalConf;

import static java.util.Collections.emptySet;
import static java.util.Collections.singleton;

public class TestGlobalConf extends EmptyGlobalConf {
    @Override
    public Collection<String> getProviderAddress(ClientId provider) {
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
    public X509Certificate getCaCert(X509Certificate org) throws Exception {
        return TestCertUtil.getCaCert();
    }

    @Override
    public CertChain getCertChain(X509Certificate subject) throws Exception {
        // TODO: Also add intermediate certs based on the subject...
        return CertChain.create(subject, null);
    }

    @Override
    public List<X509Certificate> getTspCertificates()
            throws CertificateException {
        return Arrays.asList(TestCertUtil.getTspCert());
    }
}
