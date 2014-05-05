package ee.cyber.sdsb.proxy.testsuite;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

import ee.cyber.sdsb.common.TestCertUtil;
import ee.cyber.sdsb.common.TestCertUtil.PKCS12;
import ee.cyber.sdsb.common.conf.AuthKey;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.proxy.conf.SigningCtx;
import ee.cyber.sdsb.proxy.util.TestUtil;

public class TestKeyConf extends EmptyKeyConf {

    Map<String, SigningCtx> signingCtx = new HashMap<>();

    @Override
    public SigningCtx getSigningCtx(ClientId clientId) {
        // TODO: Use ClientId in MessageTestCase.getSigningCtx ?
        String orgName = clientId.getMemberCode();
        SigningCtx ctx = currentTestCase().getSigningCtx(orgName);
        if (ctx != null) {
            return ctx;
        }

        if (!signingCtx.containsKey(orgName)) {
            signingCtx.put(orgName, TestUtil.getSigningCtx(orgName));
        }

        return signingCtx.get(orgName);
    }

    @Override
    public AuthKey getAuthKey() {
        PKCS12 consumer = TestCertUtil.getConsumer();
        return new AuthKey(consumer.cert, consumer.key);
    }

    @Override
    public X509Certificate getOcspSignerCert() throws Exception {
        return TestCertUtil.getOcspSigner().cert;
    }

    @Override
    public PrivateKey getOcspRequestKey(X509Certificate org) throws Exception {
        return TestCertUtil.getOcspSigner().key;
    }

    private static MessageTestCase currentTestCase() {
        return ProxyTestSuite.currentTestCase;
    }

}
