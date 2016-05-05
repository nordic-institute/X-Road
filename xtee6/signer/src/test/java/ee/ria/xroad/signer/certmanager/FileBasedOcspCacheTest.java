/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.signer.certmanager;

import ee.ria.xroad.common.OcspTestUtils;
import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.conf.globalconf.EmptyGlobalConf;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.joda.time.DateTime;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Date;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests the file based OCSP cache.
 */
public class FileBasedOcspCacheTest {

    static X509Certificate subject;
    static X509Certificate issuer;
    static X509Certificate signer;
    static PrivateKey signerKey;

    /**
     * Test.
     * @throws Exception if an error occurs
     */
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

    /**
     * Test.
     * @throws Exception if an error occurs
     */
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

    /**
     * Test.
     * @throws Exception if an error occurs
     */
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

    /**
     * Test.
     * @throws Exception if an error occurs
     */
    @Test
    public void readOcspFromEmptyFile() throws Exception {
        FileBasedOcspCache cache = new FileBasedOcspCache();

        File f = mock(File.class);
        when(f.exists()).thenReturn(true);
        when(f.length()).thenReturn(0L);
        when(f.delete()).thenReturn(true);

        assertNull(cache.loadResponseFromFile(f));
    }

    /**
     * Loads test certificates.
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
}
