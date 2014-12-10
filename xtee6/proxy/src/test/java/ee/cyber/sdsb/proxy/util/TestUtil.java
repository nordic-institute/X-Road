package ee.cyber.sdsb.proxy.util;

import java.util.HashMap;
import java.util.Map;

import ee.cyber.sdsb.common.TestCertUtil;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.signature.TestSigningKey;
import ee.cyber.sdsb.proxy.conf.SigningCtx;
import ee.cyber.sdsb.proxy.conf.SigningCtxImpl;

public class TestUtil {

    private static final String PASSWORD = "test";

    private static Map<String, TestCertUtil.PKCS12> cache = new HashMap<>();

    public static synchronized TestCertUtil.PKCS12 loadPKCS12(String orgName) {
        if (cache.containsKey(orgName)) {
            return cache.get(orgName);
        }

        TestCertUtil.PKCS12 pkcs12 =
                TestCertUtil.loadPKCS12(orgName + ".p12", "1", PASSWORD);
        cache.put(orgName, pkcs12);
        return pkcs12;
    }

    /**
     * Return signing context for given organization, assuming that
     * keystore is named orgName.p12 and key in store is named
     * after the organization.
     */
    public static SigningCtx getSigningCtx(String orgName) {
        TestCertUtil.PKCS12 pkcs12 = loadPKCS12(orgName);
        return getSigningCtx(pkcs12);
    }

    public static SigningCtx getSigningCtx() {
        return getSigningCtx(TestCertUtil.getConsumer());
    }

    private static SigningCtx getSigningCtx(TestCertUtil.PKCS12 pkcs12) {
        ClientId subject = ClientId.create("EE", "BUSINESS", "foo"); // TODO
        return new SigningCtxImpl(subject, new TestSigningKey(pkcs12.key),
                pkcs12.cert);
    }
}
