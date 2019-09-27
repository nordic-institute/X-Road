/**
 * The MIT License
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
package org.niis.xroad.restapi.converter;

import ee.ria.xroad.common.OcspTestUtils;
import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;

import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.cert.ocsp.RevokedStatus;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.niis.xroad.restapi.openapi.model.CertificateOcspStatus;
import org.niis.xroad.restapi.openapi.model.TokenCertificate;
import org.niis.xroad.restapi.util.CertificateTestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

@RunWith(SpringRunner.class)
@SpringBootTest
public class TokenCertificateConverterTest {

    @Autowired
    private TokenCertificateConverter tokenCertificateConverter;

    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void convert() throws Exception {
        X509Certificate cert = CertificateTestUtils.getCertificate(CertificateTestUtils.getMockCertificateBytes());
        List<OCSPResp> ocsp = generateOcspResponses(
                Arrays.asList(cert),
                CertificateStatus.GOOD);
        CertificateInfo certificateInfo = new CertificateInfo(ClientId.create("a", "b", "c"),
                true, true,
                CertificateInfo.STATUS_REGISTERED,
                "1", cert.getEncoded(), ocsp.iterator().next().getEncoded());
        TokenCertificate certificate = tokenCertificateConverter.convert(certificateInfo);
        assertEquals(true, certificate.getActive());
        assertEquals("N/A", certificate.getCertificateDetails().getSubjectCommonName());
        assertEquals(2038, certificate.getCertificateDetails().getNotAfter().getYear());
        assertEquals(CertificateOcspStatus.OCSP_RESPONSE_GOOD, certificate.getOcspStatus());
        assertEquals("a:b:c", certificate.getOwnerId());
        assertEquals(true, certificate.getSavedToConfiguration());
        assertEquals(org.niis.xroad.restapi.openapi.model.CertificateStatus.REGISTERED, certificate.getStatus());
    }

    @Test
    public void handleOcspResponses() throws Exception {
        // test bot expired and non-expired certs
        int currentYear = LocalDate.now().getYear();
        if (currentYear < 2014 || currentYear > 2037) {
            fail("test data (used certificates) only works correctly between years 2014 and 2037");
        }

        // Not After : Sep 14 11:57:16 2013 GMT
        X509Certificate cert = TestCertUtil.getCertChainCert("user_1.p12");

        List<OCSPResp> ocsp = generateOcspResponses(
                Arrays.asList(cert),
                CertificateStatus.GOOD);

        CertificateInfo certificateInfo = new CertificateInfo(ClientId.create("a", "b", "c"),
                true, true, CertificateInfo.STATUS_REGISTERED,
                "1", cert.getEncoded(), ocsp.iterator().next().getEncoded());
        TokenCertificate certificate = tokenCertificateConverter.convert(certificateInfo);
        assertEquals(CertificateOcspStatus.EXPIRED, certificate.getOcspStatus());

        // Not After : Jan  1 00:00:00 2038 GMT
        cert = CertificateTestUtils.getCertificate(CertificateTestUtils.getMockCertificateBytes());
        ocsp = generateOcspResponses(
                Arrays.asList(cert),
                CertificateStatus.GOOD);
        certificateInfo = new CertificateInfo(ClientId.create("a", "b", "c"),
                true, true, CertificateInfo.STATUS_REGISTERED,
                "1", cert.getEncoded(), ocsp.iterator().next().getEncoded());
        certificate = tokenCertificateConverter.convert(certificateInfo);
        assertEquals(CertificateOcspStatus.OCSP_RESPONSE_GOOD, certificate.getOcspStatus());

        RevokedStatus revokedStatus = new RevokedStatus(new Date(), CRLReason.certificateHold);
        ocsp = generateOcspResponses(
                Arrays.asList(cert),
                revokedStatus);
        certificateInfo = new CertificateInfo(ClientId.create("a", "b", "c"),
                true, true, CertificateInfo.STATUS_REGISTERED,
                "1", cert.getEncoded(), ocsp.iterator().next().getEncoded());
        certificate = tokenCertificateConverter.convert(certificateInfo);
        assertEquals(CertificateOcspStatus.OCSP_RESPONSE_SUSPENDED, certificate.getOcspStatus());

        revokedStatus = new RevokedStatus(new Date(), CRLReason.unspecified);
        ocsp = generateOcspResponses(
                Arrays.asList(cert),
                revokedStatus);
        certificateInfo = new CertificateInfo(ClientId.create("a", "b", "c"),
                true, true, CertificateInfo.STATUS_REGISTERED,
                "1", cert.getEncoded(), ocsp.iterator().next().getEncoded());
        certificate = tokenCertificateConverter.convert(certificateInfo);
        assertEquals(CertificateOcspStatus.OCSP_RESPONSE_REVOKED, certificate.getOcspStatus());
    }


    private static List<OCSPResp> generateOcspResponses(
            List<X509Certificate> certs, CertificateStatus status)
            throws Exception {
        List<OCSPResp> responses = new ArrayList<>();
        for (X509Certificate cert : certs) {
            responses.add(OcspTestUtils.createOCSPResponse(cert,
                    getIssuerCert(cert, certs),
                    TestCertUtil.getOcspSigner().certChain[0],
                    TestCertUtil.getOcspSigner().key,
                    status));
        }
        return responses;
    }

    private static X509Certificate getIssuerCert(X509Certificate subject,
            List<X509Certificate> certs) throws Exception {
        for (X509Certificate cert : certs) {
            if (cert.getSubjectX500Principal().equals(
                    subject.getIssuerX500Principal())) {
                return cert;
            }
        }

        return TestCertUtil.getCertChainCert("root_ca.p12");
    }
}
