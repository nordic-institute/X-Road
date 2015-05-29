package ee.ria.xroad.proxy.util;

import java.util.HashMap;
import java.util.Map;

import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.signature.TestSigningKey;
import ee.ria.xroad.proxy.conf.SigningCtx;
import ee.ria.xroad.proxy.conf.SigningCtxImpl;

/**
 * Contains various test utility methods.
 */
public final class TestUtil {

    private static final String PASSWORD = "test";

    private static Map<String, TestCertUtil.PKCS12> cache = new HashMap<>();

    private TestUtil() {
    }

    /**
     * Load a certificate and private key from the PKC12 keystore with the given name.
     * @param orgName the keystore name
     * @return the certificate and private key container
     */
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
     * @return signing context for given organization, assuming that
     * keystore is named orgName.p12 and key in store is named
     * after the organization.
     * @param orgName the keystore name
     */
    public static SigningCtx getSigningCtx(String orgName) {
        TestCertUtil.PKCS12 pkcs12 = loadPKCS12(orgName);
        return getSigningCtx(pkcs12);
    }

    /**
     * @return default signing context
     */
    public static SigningCtx getSigningCtx() {
        return getSigningCtx(TestCertUtil.getConsumer());
    }

    private static SigningCtx getSigningCtx(TestCertUtil.PKCS12 pkcs12) {
        ClientId subject = ClientId.create("EE", "BUSINESS", "foo"); // TODO
        return new SigningCtxImpl(subject, new TestSigningKey(pkcs12.key),
                pkcs12.cert);
    }
}
