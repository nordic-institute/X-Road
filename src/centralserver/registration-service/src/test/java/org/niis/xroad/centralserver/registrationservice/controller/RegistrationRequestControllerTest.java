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

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.certificateprofile.SignCertificateProfileInfo;
import ee.ria.xroad.common.certificateprofile.impl.EjbcaSignCertificateProfileInfo;
import ee.ria.xroad.common.conf.globalconf.EmptyGlobalConf;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.util.CryptoUtils;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.niis.xroad.centralserver.registrationservice.service.AdminApiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.cert.X509Certificate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
public class RegistrationRequestControllerTest {

    public static final String CONTENT_TYPE
            = "multipart/related; type=\"application/octet-stream\"; charset=UTF-8; boundary=jetty977554054l1bu2no0";

    @TestConfiguration
    static class TestConfig {
        @Bean
        AdminApiService adminApiServiceImpl(Environment env) {

            return (serverId, address, certificate) -> 0;
        }
    }

    @BeforeClass
    public static void setup() {
        System.setProperty(SystemProperties.CONFIGURATION_PATH, "build/resources/test/testconf");
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
                return CryptoUtils.readCertificate(
                        RegistrationRequestControllerTest.class.getResourceAsStream("/testconf/ca.cert.pem"));
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
        mvc.perform(post("/managementrequest")
                        .contentType(CONTENT_TYPE)
                        .content(Files.readAllBytes(Paths.get("build/resources/test/testauthregrequest.msg"))))
                .andExpect(MockMvcResultMatchers.status().is2xxSuccessful());
    }

    @Test
    public void shouldFailInvalidRequest() throws Exception {
        mvc.perform(post("/managementrequest")
                        .contentType(CONTENT_TYPE)
                        .content(Files.readAllBytes(Paths.get("build/resources/test/invalidauthregrequest.msg"))))
                .andExpect(MockMvcResultMatchers.status().is5xxServerError());
    }
}
