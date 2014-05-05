package ee.cyber.sdsb.proxy.testsuite;

import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Set;

import ee.cyber.sdsb.common.TestCertUtil;
import ee.cyber.sdsb.common.conf.VerificationCtx;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.SecurityCategoryId;
import ee.cyber.sdsb.common.signature.SignatureVerifier;
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
    public Set<SecurityCategoryId> getProvidedCategories(X509Certificate authCert) {
        return currentTestCase().getProvidedCategories();
    }

    @Override
    public VerificationCtx getVerificationCtx() {
        VerificationCtx ctx = currentTestCase().getVerificationCtx();
        if (ctx != null) {
            return ctx;
        }

        return new VerificationCtx() {
            @Override
            public void verifySslCert(X509Certificate cert) {
                // Declare everything OK.
            }

            @Override
            public String getOrganization(X509Certificate cert) {
                return "TODO";
            }

            @Override
            public void verifySignature(ClientId sender,
                    SignatureVerifier verifier) throws Exception {
                verifier.verify(sender, new Date());
            }
        };
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
    public List<X509Certificate> getTspCertificates()
            throws CertificateException {
        return Arrays.asList(TestCertUtil.getTspCert());
    }
}
