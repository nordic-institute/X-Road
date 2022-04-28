/**
 * The MIT License
 *
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
package org.niis.xroad.centralserver.registrationservice.controller;

import ee.ria.xroad.common.OcspTestUtils;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.TestCertUtil;
import ee.ria.xroad.common.certificateprofile.SignCertificateProfileInfo;
import ee.ria.xroad.common.certificateprofile.impl.EjbcaSignCertificateProfileInfo;
import ee.ria.xroad.common.conf.globalconf.EmptyGlobalConf;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.junit.WireMockRule;
import org.apache.commons.io.IOUtils;
import org.bouncycastle.cert.ocsp.CertificateStatus;
import org.junit.BeforeClass;
import org.junit.ClassRule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.niis.xroad.centralserver.registrationservice.config.RegistrationServiceProperties;
import org.niis.xroad.centralserver.registrationservice.openapi.model.CodeWithDetails;
import org.niis.xroad.centralserver.registrationservice.openapi.model.ErrorInfo;
import org.niis.xroad.centralserver.registrationservice.openapi.model.ManagementRequestInfo;
import org.niis.xroad.centralserver.registrationservice.testutil.TestAuthCertRegRequest;
import org.niis.xroad.centralserver.registrationservice.testutil.TestAuthRegRequestBuilder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.net.URI;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.util.Collections;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
public class RegistrationRequestApiTest {

    public static final String ENDPOINT = "/managementservice";

    @ClassRule
    public static WireMockRule wireMockRule = new WireMockRule(wireMockConfig()
            .keystorePath("./build/resources/test/testconf/ssl/internal.p12")
            .keystoreType("PKCS12")
            .keystorePassword("internal")
            .keyManagerPassword("internal")
            .dynamicHttpsPort());

    @Autowired
    private RegistrationServiceProperties properties;

    @BeforeClass
    public static void setup() {
        System.setProperty(SystemProperties.CONF_PATH, "build/resources/test/testconf");
        GlobalConf.reload(new EmptyGlobalConf() {

            @Override
            public String getInstanceIdentifier() {
                return "TEST";
            }

            @Override
            public int getOcspFreshnessSeconds(boolean smallestValue) {
                return Integer.MAX_VALUE / 2;
            }

            @Override
            public X509Certificate getCaCert(String instanceIdentifier, X509Certificate orgCert) {
                return TestCertUtil.getCaCert();
            }

            @Override
            public SignCertificateProfileInfo getSignCertificateProfileInfo(
                    SignCertificateProfileInfo.Parameters parameters, X509Certificate cert) {
                return new EjbcaSignCertificateProfileInfo(parameters);
            }
        });
    }

    @Autowired
    private MockMvc mvc;

    @Test
    public void shouldRegisterAuthCert() throws Exception {

        properties.setApiBaseUrl(URI.create(String.format("https://127.0.0.1:%d/api/v1", wireMockRule.httpsPort())));
        var response = new ManagementRequestInfo();
        response.setId(42);

        wireMockRule.stubFor(WireMock.post("/api/v1/management-requests")
                .willReturn(WireMock.jsonResponse(response, 202)));

        var req = generateRequest();
        var content = IOUtils.toByteArray(req.getRequestContent());
        mvc.perform(post(ENDPOINT)
                        .contentType(req.getRequestContentType())
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().is2xxSuccessful())
                .andExpect(MockMvcResultMatchers
                        .xpath("//xroad:requestId",
                                Collections.singletonMap("xroad", "http://x-road.eu/xsd/xroad.xsd"))
                        .string("42"));
    }

    @Test
    public void shouldReturnSoapFaultOnError() throws Exception {

        properties.setApiBaseUrl(URI.create(String.format("https://127.0.0.1:%d/api/v1", wireMockRule.httpsPort())));
        var response = new ErrorInfo();
        response.setStatus(409);
        response.setError(new CodeWithDetails().code("error"));

        wireMockRule.stubFor(WireMock.post("/api/v1/management-requests")
                .willReturn(WireMock.jsonResponse(response, 409)));

        var req = generateRequest();
        var content = IOUtils.toByteArray(req.getRequestContent());
        mvc.perform(post(ENDPOINT)
                        .contentType(req.getRequestContentType())
                        .content(content))
                .andExpect(MockMvcResultMatchers.status().is5xxServerError())
                .andExpect(MockMvcResultMatchers
                        .xpath("//soap:Fault",
                                Collections.singletonMap("soap", "http://schemas.xmlsoap.org/soap/envelope/"))
                        .exists());
    }

    private TestAuthCertRegRequest generateRequest() throws Exception {
        var keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(1024);

        var authKeyPair = keyPairGenerator.generateKeyPair();
        var authCert = TestCertUtil.generateAuthCert(authKeyPair.getPublic());

        var serverId = SecurityServerId.create("TEST", "CLASS", "MEMBER", "SS1");
        var receiver = ClientId.create("TEST", "CLASS", "MEMBER", "MANAGEMENT");
        var ownerKeyPair = keyPairGenerator.generateKeyPair();
        var ownerCert = TestCertUtil.generateSignCert(ownerKeyPair.getPublic(), serverId.getOwner());

        var ownerOcsp = OcspTestUtils.createOCSPResponse(ownerCert,
                TestCertUtil.getCaCert(),
                TestCertUtil.getOcspSigner().certChain[0],
                TestCertUtil.getOcspSigner().key,
                CertificateStatus.GOOD);

        var builder = new TestAuthRegRequestBuilder(serverId.getOwner(), receiver);
        var req = builder.buildAuthCertRegRequest(
                serverId,
                "ss1.example.org",
                authCert);

        return new TestAuthCertRegRequest(authCert,
                ownerCert.getEncoded(),
                ownerOcsp.getEncoded(),
                req,
                authKeyPair.getPrivate(),
                ownerKeyPair.getPrivate());
    }
}
