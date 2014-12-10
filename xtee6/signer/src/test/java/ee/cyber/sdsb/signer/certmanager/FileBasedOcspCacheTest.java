package ee.cyber.sdsb.signer.certmanager;

import java.io.File;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Date;

import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.joda.time.DateTime;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import ee.cyber.sdsb.common.OcspTestUtils;
import ee.cyber.sdsb.common.TestCertUtil;
import ee.cyber.sdsb.common.conf.globalconf.EmptyGlobalConf;
import ee.cyber.sdsb.common.conf.globalconf.GlobalConf;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class FileBasedOcspCacheTest {

    static X509Certificate subject;
    static X509Certificate issuer;
    static X509Certificate signer;
    static PrivateKey signerKey;

    @Test
    public void putGet() throws Exception {
        Date thisUpdate = new DateTime().plusDays(1).toDate();
        OCSPResp ocsp = OcspTestUtils.createOCSPResponse(subject, issuer,
                signer, signerKey, CertificateStatus.GOOD, thisUpdate, null);

        FileBasedOcspCache cache = new FileBasedOcspCache();
        FileBasedOcspCache spy = Mockito.spy(cache);

        Mockito.doNothing().when(spy).saveResponseToFile(
                Mockito.any(File.class), Mockito.any(OCSPResp.class));

        spy.put("foo", ocsp);

        Mockito.verify(spy).saveResponseToFile(Mockito.any(File.class),
                Mockito.any(OCSPResp.class));

        assertNotNull(cache.get("foo"));
    }

    @Test
    public void expiredResponse() throws Exception {
        Date thisUpdate = new DateTime().minusDays(1).toDate();
        OCSPResp ocsp = OcspTestUtils.createOCSPResponse(subject, issuer,
                signer, signerKey, CertificateStatus.GOOD, thisUpdate, null);

        FileBasedOcspCache cache = new FileBasedOcspCache();
        FileBasedOcspCache spy = Mockito.spy(cache);

        Mockito.doNothing().when(spy).saveResponseToFile(
                Mockito.any(File.class), Mockito.any(OCSPResp.class));

        Mockito.doReturn(ocsp).when(spy).loadResponseFromFile(
                Mockito.any(File.class));

        assertNull(spy.put("foo", ocsp));
        assertNull(spy.get("foo"));
    }

    @Test
    public void saveLoadOcspResponseToFile() throws Exception {
        Date thisUpdate = new DateTime().plusDays(1).toDate();
        OCSPResp ocsp = OcspTestUtils.createOCSPResponse(subject, issuer,
                signer, signerKey, CertificateStatus.GOOD, thisUpdate, null);

        FileBasedOcspCache cache = new FileBasedOcspCache();
        FileBasedOcspCache spy = Mockito.spy(cache);

        Mockito.doNothing().when(spy).saveResponseToFile(
                Mockito.any(File.class), Mockito.any(OCSPResp.class));

        spy.put("foo", ocsp);

        Mockito.verify(spy).saveResponseToFile(Mockito.any(File.class),
                Mockito.any(OCSPResp.class));

        spy.clear();

        Mockito.doReturn(ocsp).when(spy).loadResponseFromFile(
                Mockito.any(File.class));

        assertNotNull(spy.get("foo"));
    }

    @Test
    public void readOcspFromEmptyFile() throws Exception {
        FileBasedOcspCache cache = new FileBasedOcspCache();

        File f = mock(File.class);
        when(f.exists()).thenReturn(true);
        when(f.length()).thenReturn(0L);
        when(f.delete()).thenReturn(true);

        assertNull(cache.loadResponseFromFile(f));
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

    @BeforeClass
    public static void beforeClass() {
        GlobalConf.reload(new EmptyGlobalConf());
    }

}
