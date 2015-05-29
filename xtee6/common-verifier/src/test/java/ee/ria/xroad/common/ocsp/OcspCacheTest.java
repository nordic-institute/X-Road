package ee.ria.xroad.common.ocsp;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Date;

import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.joda.time.DateTime;
import org.junit.BeforeClass;
import org.junit.Test;

import ee.ria.xroad.common.OcspTestUtils;
import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.conf.globalconf.EmptyGlobalConf;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;

import static org.junit.Assert.*;

/**
 * Tests the OCSP cache.
 */
public class OcspCacheTest {

    static X509Certificate subject;
    static X509Certificate issuer;
    static X509Certificate signer;
    static PrivateKey signerKey;

    /**
     * Sets up an empty global configuration and loads test certificates.
     * @throws Exception if an error occurs
     */
    @BeforeClass
    public static void loadCerts() throws Exception {
        GlobalConf.reload(new EmptyGlobalConf());

        issuer = TestCertUtil.getCertChainCert("root_ca.p12");
        assertNotNull(issuer);

        signer = issuer;
        signerKey = TestCertUtil.getCertChainKey("root_ca.p12");
        assertNotNull(signerKey);

        subject = TestCertUtil.getCertChainCert("user_0.p12");
        assertNotNull(subject);
    }

    /**
     * Tests putting and getting the OCSP response from the cache.
     * @throws Exception if an error occurs
     */
    @Test
    public void putGet() throws Exception {
        Date thisUpdate = new DateTime().plusDays(1).toDate();
        OCSPResp ocsp = OcspTestUtils.createOCSPResponse(subject, issuer,
                signer, signerKey, CertificateStatus.GOOD, thisUpdate, null);

        OcspCache cache = new OcspCache();
        assertNull(cache.put("foo", ocsp));
        assertEquals(ocsp, cache.get("foo"));
    }

    /**
     * Tests that getting an expired OCSP response from the cache returns null.
     * @throws Exception if an error occurs
     */
    @Test
    public void expiredResponse() throws Exception {
        Date thisUpdate = new DateTime().minusDays(1).toDate();
        OCSPResp ocsp = OcspTestUtils.createOCSPResponse(subject, issuer,
                signer, signerKey, CertificateStatus.GOOD, thisUpdate, null);

        OcspCache cache = new OcspCache();
        assertNull(cache.put("foo", ocsp));
        assertNull(cache.get("foo"));
    }
}
