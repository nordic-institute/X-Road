/**
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.centralserver.restapi.openapi;

import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.niis.xroad.centralserver.openapi.model.ApprovedCertificationServiceDto;
import org.niis.xroad.centralserver.openapi.model.ApprovedCertificationServiceListItemDto;
import org.niis.xroad.centralserver.openapi.model.CertificateDetailsDto;
import org.niis.xroad.centralserver.restapi.util.TestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.niis.xroad.centralserver.openapi.model.KeyUsageDto.DIGITAL_SIGNATURE;
import static org.niis.xroad.centralserver.openapi.model.KeyUsageDto.KEY_ENCIPHERMENT;
import static org.springframework.http.HttpStatus.OK;

class CertificationServicesApiTest extends AbstractApiRestTemplateTestContext {

    private static final String CERT_PROFILE_INFO_PROVIDER
            = "ee.ria.xroad.common.certificateprofile.impl.BasicCertificateProfileInfoProvider";

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    void getCertificationServices() {
        TestUtils.addApiKeyAuthorizationHeader(restTemplate);

        var response = restTemplate.getForEntity(
                "/api/v1/certification-services",
                ApprovedCertificationServiceListItemDto[].class);

        assertNotNull(response);
        assertEquals(OK, response.getStatusCode());
        assertThat(response.getBody().length).isGreaterThanOrEqualTo(1);
    }

    @Test
    void getCertificationService() {
        TestUtils.addApiKeyAuthorizationHeader(restTemplate);

        final var savedCertificateId = saveTestCertificate("google-cert.der")
                .getBody()
                .getId();

        final var certificateDetailsResponse =
                restTemplate.getForEntity("/api/v1/certification-services/{id}", ApprovedCertificationServiceDto.class, savedCertificateId);

        assertEquals(OK, certificateDetailsResponse.getStatusCode());

        final var cert = certificateDetailsResponse.getBody();

        assertEquals(CERT_PROFILE_INFO_PROVIDER, cert.getCertificateProfileInfo());
        assertFalse(cert.getTlsAuth());

        assertEquals(OffsetDateTime.of(2019, 3, 26, 13, 35, 42, 0, ZoneOffset.UTC),
                cert.getNotBefore());
        assertEquals(OffsetDateTime.of(2019, 6, 18, 13, 24, 0, 0, ZoneOffset.UTC),
                cert.getNotAfter());
        assertEquals("CN=*.google.com, O=Google LLC, L=Mountain View, ST=California, C=US",
                cert.getSubjectDistinguishedName());
        assertEquals("CN=Google Internet Authority G3, O=Google Trust Services, C=US",
                cert.getIssuerDistinguishedName());
        assertEquals("*.google.com", cert.getName());
    }

    @Test
    void getCertificationServiceCertificate() {
        TestUtils.addApiKeyAuthorizationHeader(restTemplate);

        final var savedCertificateId = saveTestCertificate("x-road.eu.der")
                .getBody()
                .getId();

        final var certificateDetailsResponse =
                restTemplate.getForEntity("/api/v1/certification-services/{id}/certificate",
                        CertificateDetailsDto.class, savedCertificateId);

        assertEquals(OK, certificateDetailsResponse.getStatusCode());

        final CertificateDetailsDto dto = certificateDetailsResponse.getBody();

        assertEquals("B694C9C3F3F649E504F79F368E920D96300DB72C", dto.getHash());
        assertEquals("Amazon", dto.getIssuerCommonName());
        assertEquals("CN=Amazon, OU=Server CA 1B, O=Amazon, C=US", dto.getIssuerDistinguishedName());
        assertEquals(OffsetDateTime.of(2022, 7, 18, 0, 0, 0, 0, ZoneOffset.UTC),
                dto.getNotBefore());
        assertEquals(OffsetDateTime.of(2023, 8, 16, 23, 59, 59, 0, ZoneOffset.UTC),
                dto.getNotAfter());
        assertThat(dto.getKeyUsages()).containsOnly(DIGITAL_SIGNATURE, KEY_ENCIPHERMENT);
        assertEquals("RSA", dto.getPublicKeyAlgorithm());
        assertEquals(65537, dto.getRsaPublicKeyExponent());
        assertEquals("254923285501000706125917744295421941578372003092722288098662504175387356872123035"
                + "0208505824989707850506356696522581870466956175621470524720659396205955437946760922224407235755"
                + "9686476183169710010429758545420451621851337407731458898780940190464029648668736087824569725387"
                + "2357235671054084098401741130745015469834981241175990577882601866126506192691542720487040954743"
                + "2341886107078754668084321040712353726106503686603632628664089621141530461878631976413690129783"
                + "5997146123537273938418225446272879610921670008893430056854210111980254198522997393784619581193"
                + "442721751018490355248727954997434888050744234974839974312312228333", dto.getRsaPublicKeyModulus());
        assertEquals("10071564069023040388847475153470947751", dto.getSerial());
        assertEquals("53572b1bd79d3fe83cf2f829f8d2e64a395e5399fe15eb9f12fc0480eb5cfe05671694b675d2e542d"
                + "1d56cc58e43c8ecdc9ee2898d7c143dd28d6be19b8e910619897d162fd73b97484992569ba853cebf99bd7c092a0b1"
                + "cfb155ba5319453f175aeaaf3cca66ee199534d7d2f8df8a103fc43a3dd625be81f5dd7689a652d078c353b60674a2"
                + "4ef4b2c64fa967be0689022419ef708331e8e5bedd56b44f615ed6d0ce090590c15e20032e131d247e3b4ab02d97b8"
                + "2c8095dbacdd04f3f2cc7d537b22c4be7bc47a7f58bdd8ed063de0ac442488f269f00d974d5a6adb54c09c54ff2885"
                + "b72ac9a914dc6396c20e24b1b74e821940b02d126871fb3973e3714", dto.getSignature());
        assertEquals("SHA256withRSA", dto.getSignatureAlgorithm());
        assertEquals("DNS:x-road.eu, DNS:www.x-road.eu", dto.getSubjectAlternativeNames());
        assertEquals("x-road.eu", dto.getSubjectCommonName());
        assertEquals("CN=x-road.eu", dto.getSubjectDistinguishedName());
        assertEquals(3, dto.getVersion());
    }

    @Test
    void addCertificationService() {
        TestUtils.addApiKeyAuthorizationHeader(restTemplate);

        ResponseEntity<ApprovedCertificationServiceDto> response = saveTestCertificate("google-cert.der");

        assertNotNull(response);
        assertEquals(OK, response.getStatusCode());
        assertEquals("*.google.com", response.getBody().getName());
        assertNotNull(response.getBody().getNotBefore());
        assertNotNull(response.getBody().getNotAfter());
    }

    private ResponseEntity<ApprovedCertificationServiceDto> saveTestCertificate(String filename) {
        return restTemplate.postForEntity(
                "/api/v1/certification-services",
                prepareAddCertificationServiceRequest(filename),
                ApprovedCertificationServiceDto.class);
    }

    private HttpEntity<MultiValueMap<String, Object>> prepareAddCertificationServiceRequest(String filename) {
        MultiValueMap<String, Object> request = new LinkedMultiValueMap<>();
        request.add("certificate", generateMockCertFile(filename));
        request.add("tls_auth", Boolean.FALSE);
        request.add("certificate_profile_info", CERT_PROFILE_INFO_PROVIDER);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        return new HttpEntity<>(request, headers);
    }

    @SneakyThrows
    private ByteArrayResource generateMockCertFile(String filename) {
        return new ByteArrayResource(
                IOUtils.toByteArray(this.getClass().getClassLoader().getResourceAsStream(filename))) {
            @Override
            public String getFilename() {
                return filename;
            }
        };
    }

}
