/*
 * The MIT License
 * <p>
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
package org.niis.xroad.cs.registrationservice.controller;

import ee.ria.xroad.common.OcspTestUtils;
import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.message.SoapMessageImpl;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.runner.RunWith;
import org.niis.xroad.common.managemenetrequest.test.TestAuthRegTypeRequest;
import org.niis.xroad.common.managemenetrequest.test.TestBaseManagementRequest;
import org.niis.xroad.common.managemenetrequest.test.TestManagementRequestBuilder;
import org.niis.xroad.common.properties.config.XRoadConfigOverrides;
import org.niis.xroad.cs.openapi.model.AuthenticationCertificateRegistrationRequestDto;
import org.niis.xroad.cs.openapi.model.CodeWithDetailsDto;
import org.niis.xroad.cs.openapi.model.ErrorInfoDto;
import org.niis.xroad.cs.openapi.model.ManagementRequestTypeDto;
import org.niis.xroad.cs.registrationservice.testutil.TestGlobalConf;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.security.KeyPairGenerator;
import java.util.Collections;
import java.util.Map;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@RunWith(SpringRunner.class)
@SpringBootTest(properties = "spring.main.lazy-initialization=true")
@AutoConfigureMockMvc
class RegistrationRequestApiTest {

    public static final String ENDPOINT = "/managementservice";
    private static final String SYSTEM_DID = "did:web:ss1.example.org%3A7183:v1:system";
    private static final String DSP_BASE_URL = "https://ss1.example.org:8183/api/dsp";

    @RegisterExtension
    static WireMockExtension wireMockRule = WireMockExtension.newInstance()
            .options(wireMockConfig()
                    .dynamicPort())
            .build();

    @Autowired
    private GlobalConfProvider globalConfProvider;

    @TestConfiguration
    static class TestGlobalConfConfiguration {
        @Bean
        @Primary
        GlobalConfProvider testGlobalConfProvider() {
            return new TestGlobalConf();
        }

        @Bean
        @Primary
        XRoadConfigOverrides testXRoadConfigOverrides() {
            return new XRoadConfigOverrides(Map.of(
                    "xroad.registration-service.api-base-url",
                    "http://127.0.0.1:%d/api/v1".formatted(wireMockRule.getPort())));
        }
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void shouldRegisterAuthCert() throws Exception {

        var response = new AuthenticationCertificateRegistrationRequestDto();
        response.setId(42);
        response.setType(ManagementRequestTypeDto.AUTH_CERT_REGISTRATION_REQUEST);

        wireMockRule.stubFor(WireMock.post("/api/v1/management-requests")
                .willReturn(WireMock.jsonResponse(response, 202)));

        var payload = generateRequest().createPayload();
        mvc.perform(post(ENDPOINT)
                        .contentType(payload.getContentType())
                        .content(payload.getPayload()))
                .andExpect(MockMvcResultMatchers.status().is2xxSuccessful())
                .andExpect(MockMvcResultMatchers
                        .xpath("//xroad:requestId",
                                Collections.singletonMap("xroad", "http://x-road.eu/xsd/xroad.xsd"))
                        .string("42"));
    }

    @Test
    void shouldReturnSoapFaultOnApiError() throws Exception {

        var response = new ErrorInfoDto();
        response.setStatus(409);
        response.setError(new CodeWithDetailsDto().code("error"));

        wireMockRule.stubFor(WireMock.post("/api/v1/management-requests")
                .willReturn(WireMock.jsonResponse(response, 409)));

        var payload = generateRequest().createPayload();
        mvc.perform(post(ENDPOINT)
                        .contentType(payload.getContentType())
                        .content(payload.getPayload()))
                .andExpect(MockMvcResultMatchers.status().is5xxServerError())
                .andExpect(MockMvcResultMatchers
                        .xpath("//soap:Fault",
                                Collections.singletonMap("soap", "http://schemas.xmlsoap.org/soap/envelope/"))
                        .exists());
    }

    @ParameterizedTest
    @CsvSource({
            "management_request_did_final_conflict, system_did_final_conflict, 409, CERTIFICATE_OF_ANOTHER_SERVER",
            "management_request_did_fixable_conflict, system_did_fixable_conflict, 409, OWNER_CHANGED",
            "management_request_did_not_resolved, system_did_not_resolved, 400, RESOLVER_UNAVAILABLE"
    })
    void shouldCarryOutcomeClassAndReasonInSoapFault(String adminCode, String faultCode, int status, String reason)
            throws Exception {
        stubAdminError(status, adminCode, reason);

        var payload = generateSystemDidRequest().createPayload();
        mvc.perform(post(ENDPOINT)
                        .contentType(payload.getContentType())
                        .content(payload.getPayload()))
                .andExpect(MockMvcResultMatchers.status().is5xxServerError())
                .andExpect(MockMvcResultMatchers.xpath("//faultcode").string(faultCode))
                .andExpect(MockMvcResultMatchers.xpath("//faultstring").string(reason));
    }

    @Test
    void shouldKeepInternalErrorFaultForUnrelatedAdminErrorOnSystemDidRequest() throws Exception {
        stubAdminError(409, "some_other_error", "OWNER_CHANGED");

        var payload = generateSystemDidRequest().createPayload();
        mvc.perform(post(ENDPOINT)
                        .contentType(payload.getContentType())
                        .content(payload.getPayload()))
                .andExpect(MockMvcResultMatchers.status().is5xxServerError())
                .andExpect(MockMvcResultMatchers.xpath("//faultcode").string("internal_error"))
                .andExpect(MockMvcResultMatchers.xpath("//faultstring")
                        .string(containsString("some_other_error with statusCode 409")));
    }

    @Test
    void shouldAnswerInternalErrorFaultWhenAdminErrorBodyIsNotJson() throws Exception {
        wireMockRule.stubFor(WireMock.post("/api/v1/management-requests")
                .willReturn(WireMock.aResponse()
                        .withStatus(502)
                        .withHeader("Content-Type", "text/html")
                        .withBody("<html>Bad Gateway</html>")));

        var payload = generateSystemDidRequest().createPayload();
        mvc.perform(post(ENDPOINT)
                        .contentType(payload.getContentType())
                        .content(payload.getPayload()))
                .andExpect(MockMvcResultMatchers.status().is5xxServerError())
                .andExpect(MockMvcResultMatchers.xpath("//faultcode").string("internal_error"))
                .andExpect(MockMvcResultMatchers.xpath("//faultstring").string(containsString("Management request failed")));
    }

    private void stubAdminError(int status, String adminCode, String reason) {
        var response = new ErrorInfoDto();
        response.setStatus(status);
        response.setError(new CodeWithDetailsDto().code(adminCode).addMetadataItem(reason));

        wireMockRule.stubFor(WireMock.post("/api/v1/management-requests")
                .willReturn(WireMock.jsonResponse(response, status)));
    }

    private TestBaseManagementRequest generateSystemDidRequest() throws Exception {
        return generateRequest((builder, serverId, authCert) ->
                builder.buildSystemDidRegRequest(serverId, SYSTEM_DID, DSP_BASE_URL));
    }

    private TestBaseManagementRequest generateRequest() throws Exception {
        return generateRequest((builder, serverId, authCert) ->
                builder.buildAuthCertRegRequest(serverId, "ss1.example.org", authCert));
    }

    private TestBaseManagementRequest generateRequest(
            RequestFactory requestFactory) throws Exception {
        var keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(1024);

        var authKeyPair = keyPairGenerator.generateKeyPair();
        var authCert = TestCertUtil.generateAuthCert(authKeyPair.getPublic());

        var serverId = SecurityServerId.Conf.create(globalConfProvider.getInstanceIdentifier(), "CLASS", "MEMBER", "SS1");
        var receiver = globalConfProvider.getManagementRequestService();
        var ownerKeyPair = keyPairGenerator.generateKeyPair();
        var ownerCert = TestCertUtil.generateSignCert(ownerKeyPair.getPublic(), serverId.getOwner());

        var ownerOcsp = OcspTestUtils.createOCSPResponse(ownerCert,
                TestCertUtil.getCaCert(),
                TestCertUtil.getOcspSigner().certChain[0],
                TestCertUtil.getOcspSigner().key,
                CertificateStatus.GOOD);

        var builder = new TestManagementRequestBuilder(serverId.getOwner(), receiver);
        var req = requestFactory.create(builder, serverId, authCert);

        return new TestAuthRegTypeRequest(authCert,
                ownerCert.getEncoded(),
                ownerOcsp.getEncoded(),
                req,
                authKeyPair.getPrivate(),
                ownerKeyPair.getPrivate());
    }

    @FunctionalInterface
    private interface RequestFactory {
        SoapMessageImpl create(TestManagementRequestBuilder builder, SecurityServerId.Conf serverId, byte[] authCert);
    }
}
