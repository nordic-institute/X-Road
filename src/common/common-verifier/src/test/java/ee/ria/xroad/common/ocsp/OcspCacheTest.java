/**
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package ee.ria.xroad.common.ocsp;

import ee.ria.xroad.common.OcspTestUtils;
import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.conf.globalconf.EmptyGlobalConf;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;

import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.junit.BeforeClass;
import org.junit.Test;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

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
        Date thisUpdate = Date.from(Instant.now().plus(1, ChronoUnit.DAYS));
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
        Date thisUpdate = Date.from(Instant.now().minus(1, ChronoUnit.DAYS));
        OCSPResp ocsp = OcspTestUtils.createOCSPResponse(subject, issuer,
                signer, signerKey, CertificateStatus.GOOD, thisUpdate, null);

        OcspCache cache = new OcspCache();
        assertNull(cache.put("foo", ocsp));
        assertNull(cache.get("foo"));
    }
}
