package ee.cyber.sdsb.common.ocsp;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Date;

import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.joda.time.DateTime;
import org.junit.BeforeClass;
import org.junit.Test;

import ee.cyber.sdsb.common.OcspTestUtils;
import ee.cyber.sdsb.common.TestCertUtil;

import static org.junit.Assert.*;

public class OcspCacheTest {

    static X509Certificate subject;
    static X509Certificate issuer;
    static X509Certificate signer;
    static PrivateKey signerKey;

    @Test
    public void putGet() throws Exception {
        Date thisUpdate = new DateTime().plusDays(1).toDate();
        OCSPResp ocsp = OcspTestUtils.createOCSPResponse(subject, issuer,
                signer, signerKey, CertificateStatus.GOOD, thisUpdate, null);

        OcspCache cache = new OcspCache();
        assertNull(cache.put("foo", ocsp));
        assertEquals(ocsp, cache.get("foo"));
    }

    @Test
    public void expiredResponse() throws Exception {
        Date thisUpdate = new DateTime().minusDays(1).toDate();
        OCSPResp ocsp = OcspTestUtils.createOCSPResponse(subject, issuer,
                signer, signerKey, CertificateStatus.GOOD, thisUpdate, null);

        OcspCache cache = new OcspCache();
        assertNull(cache.put("foo", ocsp));
        assertNull(cache.get("foo"));
    }

    @BeforeClass
    public static void loadCerts() throws Exception {
        issuer = TestCertUtil.getCertChainCert("root_ca.p12");
        assertNotNull(issuer);

        signer = issuer;
        signerKey = TestCertUtil.getCertChainKey("root_ca.p12");
        assertNotNull(signerKey);

        subject = TestCertUtil.getCertChainCert("user_0.p12");
        assertNotNull(subject);
    }
}
