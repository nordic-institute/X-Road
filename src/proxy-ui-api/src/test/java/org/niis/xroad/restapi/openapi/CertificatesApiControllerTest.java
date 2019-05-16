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
package org.niis.xroad.restapi.openapi;

import lombok.extern.slf4j.Slf4j;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

/**
 * Test CertificatesApiController
 */
@Ignore
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase
@Transactional
@Slf4j
@SuppressWarnings("checkstyle:TodoComment")
public class CertificatesApiControllerTest {
    /* TODO: CertificatesApiController needs to be implemented before
    @Test
    @WithMockUser(authorities = "VIEW_CLIENT_DETAILS")
    public void getClientCertificateDetails() throws Exception {
        ResponseEntity<List<CertificateDetails>> certificates =
                clientsApiController.getClientCertificates("FI:GOV:M1");
        assertEquals(HttpStatus.OK, certificates.getStatusCode());
        assertEquals(0, certificates.getBody().size());

        CertificateInfo mockCertificate = new CertificateInfo(
                ClientId.create("FI", "GOV", "M1"),
                true, true, CertificateInfo.STATUS_REGISTERED,
                "id", certBytes, null);
        when(tokenRepository.getTokens()).thenReturn(createMockTokenInfos(mockCertificate));
        certificates = clientsApiController.getClientCertificates("FI:GOV:M1");
        assertEquals(HttpStatus.OK, certificates.getStatusCode());
        assertEquals(1, certificates.getBody().size());

        CertificateDetails onlyCertificate = certificates.getBody().get(0);
        assertEquals("N/A", onlyCertificate.getIssuerCommonName());
        assertEquals(OffsetDateTime.parse("1970-01-01T00:00:00Z"), onlyCertificate.getNotBefore());
        assertEquals(OffsetDateTime.parse("2038-01-01T00:00:00Z"), onlyCertificate.getNotAfter());
        assertEquals("1", onlyCertificate.getSerial());
        assertEquals(new Integer(3), onlyCertificate.getVersion());
        assertEquals("SHA512withRSA", onlyCertificate.getSignatureAlgorithm());
        assertEquals("RSA", onlyCertificate.getPublicKeyAlgorithm());
        assertEquals("A2293825AA82A5429EC32803847E2152A303969C", onlyCertificate.getHash());
        assertEquals(org.niis.xroad.restapi.openapi.model.State.IN_USE, onlyCertificate.getState());
        assertTrue(onlyCertificate.getSignature().startsWith("314b7a50a09a9b74322671"));
        assertTrue(onlyCertificate.getRsaPublicKeyModulus().startsWith("9d888fbe089b32a35f58"));
        assertEquals(new Integer(65537), onlyCertificate.getRsaPublicKeyExponent());
        assertEquals(new ArrayList<>(Arrays.asList(org.niis.xroad.restapi.openapi.model.KeyUsage.NON_REPUDIATION)),
                new ArrayList<>(onlyCertificate.getKeyUsages()));

        try {
            certificates = clientsApiController.getClientCertificates("FI:GOV:M2");
            fail("should throw NotFoundException for 404");
        } catch (NotFoundException expected) {
        }
    }
    */
}
