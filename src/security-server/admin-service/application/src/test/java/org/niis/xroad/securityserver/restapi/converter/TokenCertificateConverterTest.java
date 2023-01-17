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
package org.niis.xroad.securityserver.restapi.converter;

import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;

import org.bouncycastle.asn1.x509.CRLReason;
import org.bouncycastle.cert.ocsp.RevokedStatus;
import org.junit.Before;
import org.junit.Test;
import org.niis.xroad.securityserver.restapi.openapi.model.CertificateOcspStatus;
import org.niis.xroad.securityserver.restapi.openapi.model.PossibleAction;
import org.niis.xroad.securityserver.restapi.openapi.model.TokenCertificate;
import org.niis.xroad.securityserver.restapi.service.PossibleActionEnum;
import org.niis.xroad.securityserver.restapi.util.CertificateTestUtils;
import org.niis.xroad.securityserver.restapi.util.TokenTestUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.security.cert.X509Certificate;
import java.time.LocalDate;
import java.util.Collection;
import java.util.Date;
import java.util.EnumSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

public class TokenCertificateConverterTest extends AbstractConverterTestContext {

    @Autowired
    TokenCertificateConverter tokenCertificateConverter;

    @Before
    public void setup() {
        doReturn(EnumSet.of(PossibleActionEnum.ACTIVATE)).when(possibleActionsRuleEngine)
                .getPossibleCertificateActions(any(), any(), any());
        doReturn(EnumSet.of(PossibleActionEnum.DELETE)).when(possibleActionsRuleEngine)
                .getPossibleCsrActions(any());
    }

    @Test
    public void convertWithPossibleActions() throws Exception {
        CertificateInfo certificateInfo = new CertificateTestUtils.CertificateInfoBuilder().build();
        KeyInfo keyInfo = new TokenTestUtils.KeyInfoBuilder()
                .cert(certificateInfo)
                .build();
        TokenInfo tokenInfo = new TokenTestUtils.TokenInfoBuilder()
                .key(keyInfo)
                .build();
        TokenCertificate certificate = tokenCertificateConverter.convert(certificateInfo, keyInfo, tokenInfo);
        Collection<PossibleAction> actions = certificate.getPossibleActions();
        assertTrue(actions.contains(PossibleAction.ACTIVATE));
        assertEquals(1, actions.size());
    }

    @Test
    public void convert() throws Exception {
        CertificateInfo certificateInfo = new CertificateTestUtils.CertificateInfoBuilder().build();
        TokenCertificate certificate = tokenCertificateConverter.convert(certificateInfo);
        assertEquals(true, certificate.getActive());
        assertEquals("N/A", certificate.getCertificateDetails().getSubjectCommonName());
        assertEquals(2038, certificate.getCertificateDetails().getNotAfter().getYear());
        assertEquals(CertificateOcspStatus.OCSP_RESPONSE_GOOD, certificate.getOcspStatus());
        assertEquals("a:b:c", certificate.getOwnerId());
        assertEquals(true, certificate.getSavedToConfiguration());
        assertEquals(org.niis.xroad.securityserver.restapi.openapi.model.CertificateStatus.REGISTERED,
                certificate.getStatus());
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

        CertificateInfo certificateInfo = new CertificateTestUtils.CertificateInfoBuilder()
                .certificate(cert)
                .build();
        TokenCertificate certificate = tokenCertificateConverter.convert(certificateInfo);
        assertEquals(CertificateOcspStatus.EXPIRED, certificate.getOcspStatus());

        // Not After : Jan  1 00:00:00 2038 GMT
        cert = CertificateTestUtils.getMockCertificate();
        certificateInfo = new CertificateTestUtils.CertificateInfoBuilder()
                .certificate(cert).build();
        certificate = tokenCertificateConverter.convert(certificateInfo);
        assertEquals(CertificateOcspStatus.OCSP_RESPONSE_GOOD, certificate.getOcspStatus());

        RevokedStatus revokedStatus = new RevokedStatus(new Date(), CRLReason.certificateHold);
        certificateInfo = new CertificateTestUtils.CertificateInfoBuilder()
                .certificate(cert)
                .ocspStatus(revokedStatus)
                .build();

        certificate = tokenCertificateConverter.convert(certificateInfo);
        assertEquals(CertificateOcspStatus.OCSP_RESPONSE_SUSPENDED, certificate.getOcspStatus());

        revokedStatus = new RevokedStatus(new Date(), CRLReason.unspecified);
        certificateInfo = new CertificateTestUtils.CertificateInfoBuilder()
                .certificate(cert)
                .ocspStatus(revokedStatus)
                .build();
        certificate = tokenCertificateConverter.convert(certificateInfo);
        assertEquals(CertificateOcspStatus.OCSP_RESPONSE_REVOKED, certificate.getOcspStatus());
    }
}
