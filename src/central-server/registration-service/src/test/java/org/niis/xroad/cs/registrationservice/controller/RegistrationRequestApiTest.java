/**
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
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.identifier.SecurityServerId;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.runner.RunWith;
import org.niis.xroad.common.managemenetrequest.test.TestAuthRegTypeRequest;
import org.niis.xroad.common.managemenetrequest.test.TestBaseManagementRequest;
import org.niis.xroad.common.managemenetrequest.test.TestManagementRequestBuilder;
import org.niis.xroad.cs.openapi.model.CodeWithDetailsDto;
import org.niis.xroad.cs.openapi.model.ErrorInfoDto;
import org.niis.xroad.cs.openapi.model.ManagementRequestDto;
import org.niis.xroad.cs.registrationservice.config.RegistrationServiceProperties;
import org.niis.xroad.cs.registrationservice.testutil.TestGlobalConf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.net.URI;
import java.security.KeyPairGenerator;
import java.util.Collections;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.niis.xroad.cs.openapi.model.ManagementRequestTypeDto.AUTH_CERT_REGISTRATION_REQUEST;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@RunWith(SpringRunner.class)
@SpringBootTest(properties = "spring.main.lazy-initialization=true")
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
class RegistrationRequestApiTest {

    public static final String ENDPOINT = "/managementservice";

    @RegisterExtension
    static WireMockExtension wireMockRule = WireMockExtension.newInstance()
            .options(wireMockConfig()
                    .keystorePath("./build/resources/test/testconf/ssl/center-admin-service.p12")
                    .keystoreType("PKCS12")
                    .keystorePassword("center-admin-service")
                    .keyManagerPassword("center-admin-service")
                    .httpDisabled(true)
                    .dynamicHttpsPort())
            .build();

    @Autowired
    private RegistrationServiceProperties properties;

    @BeforeAll
    public static void setup() {
        System.setProperty(SystemProperties.CONF_PATH, "build/resources/test/testconf");
        GlobalConf.reload(new TestGlobalConf());
    }

    @Autowired
    private MockMvc mvc;

    @Test
    void shouldRegisterAuthCert() throws Exception {

        properties.setApiBaseUrl(URI.create(String.format("https://127.0.0.1:%d/api/v1", wireMockRule.getHttpsPort())));
        var response = new ManagementRequestDto();
        response.setId(42);
        response.setType(AUTH_CERT_REGISTRATION_REQUEST);

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

        properties.setApiBaseUrl(URI.create(String.format("https://127.0.0.1:%d/api/v1", wireMockRule.getHttpsPort())));
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

    private TestBaseManagementRequest generateRequest() throws Exception {
        var keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(1024);

        var authKeyPair = keyPairGenerator.generateKeyPair();
        var authCert = TestCertUtil.generateAuthCert(authKeyPair.getPublic());

        var serverId = SecurityServerId.Conf.create(GlobalConf.getInstanceIdentifier(), "CLASS", "MEMBER", "SS1");
        var receiver = GlobalConf.getManagementRequestService();
        var ownerKeyPair = keyPairGenerator.generateKeyPair();
        var ownerCert = TestCertUtil.generateSignCert(ownerKeyPair.getPublic(), serverId.getOwner());

        var ownerOcsp = OcspTestUtils.createOCSPResponse(ownerCert,
                TestCertUtil.getCaCert(),
                TestCertUtil.getOcspSigner().certChain[0],
                TestCertUtil.getOcspSigner().key,
                CertificateStatus.GOOD);

        var builder = new TestManagementRequestBuilder(serverId.getOwner(), receiver);
        var req = builder.buildAuthCertRegRequest(
                serverId,
                "ss1.example.org",
                authCert);

        return new TestAuthRegTypeRequest(authCert,
                ownerCert.getEncoded(),
                ownerOcsp.getEncoded(),
                req,
                authKeyPair.getPrivate(),
                ownerKeyPair.getPrivate());
    }
}
