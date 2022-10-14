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

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.niis.xroad.centralserver.openapi.model.ApprovedCertificationServiceDto;
import org.niis.xroad.centralserver.openapi.model.CertificationServiceSettingsDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockPart;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.ResultActions;

import static org.apache.commons.lang3.BooleanUtils.FALSE;
import static org.apache.commons.lang3.BooleanUtils.TRUE;
import static org.hamcrest.Matchers.equalTo;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


class CertificationServicesApiTest extends AbstractApiControllerTest {

    private static final String BASIC_CERT_PROFILE_INFO_PROVIDER
            = "ee.ria.xroad.common.certificateprofile.impl.BasicCertificateProfileInfoProvider";
    private static final String FIVRK_CERT_PROFILE_INFO_PROVIDER
            = "ee.ria.xroad.common.certificateprofile.impl.FiVRKCertificateProfileInfoProvider";
    private static final String ISSUER_NAME = "CN=Google Internet Authority G3, O=Google Trust Services, C=US";
    private static final String SUBJECT_NAME = "CN=*.google.com, O=Google LLC, L=Mountain View, ST=California, C=US";

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(authorities = { "VIEW_APPROVED_CAS"})
    void getCertificationServices() throws Exception {

        mockMvc.perform(
                get(commonModuleEndpointPaths.getBasePath() + "/certification-services"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].id", equalTo(100)))
                .andExpect(jsonPath("$[0].name", equalTo("X-Road Test CA CN")));
    }

    @Test
    @WithMockUser(authorities = "ADD_APPROVED_CA")
    void addCertificationService() throws Exception {
        callAddCertificationService();
    }

    @Test
    @WithMockUser(authorities = {"ADD_APPROVED_CA", "VIEW_APPROVED_CA_DETAILS"})
    void getCertificateDetails() throws Exception {
        String newApprovedCa = callAddCertificationService().andReturn().getResponse().getContentAsString();
        Integer id = objectMapper.readValue(newApprovedCa, ApprovedCertificationServiceDto.class).getId();

        mockMvc.perform(
                get(commonModuleEndpointPaths.getBasePath() + "/certification-services/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("name", equalTo("*.google.com")))
                .andExpect(jsonPath("tls_auth", equalTo(false)))
                .andExpect(jsonPath("issuer_distinguished_name", equalTo(ISSUER_NAME)))
                .andExpect(jsonPath("subject_distinguished_name", equalTo(SUBJECT_NAME)))
                .andExpect(jsonPath("certificate_profile_info", equalTo(BASIC_CERT_PROFILE_INFO_PROVIDER)));
    }

    @Test
    @WithMockUser(authorities = {"ADD_APPROVED_CA", "EDIT_APPROVED_CA"})
    void editCertificationServiceSettings() throws Exception {
        String newApprovedCa = callAddCertificationService().andReturn().getResponse().getContentAsString();
        Integer id = objectMapper.readValue(newApprovedCa, ApprovedCertificationServiceDto.class).getId();
        CertificationServiceSettingsDto newSettings = new CertificationServiceSettingsDto()
                .tlsAuth(TRUE)
                .certificateProfileInfo(FIVRK_CERT_PROFILE_INFO_PROVIDER);

        mockMvc.perform(
                        patch(commonModuleEndpointPaths.getBasePath() + "/certification-services/{id}", id)
                                .contentType(APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(newSettings)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("tls_auth", equalTo(true)))
                .andExpect(jsonPath("certificate_profile_info", equalTo(FIVRK_CERT_PROFILE_INFO_PROVIDER)));
    }

    private ResultActions callAddCertificationService() throws Exception {
        return mockMvc.perform(
                        multipart(commonModuleEndpointPaths.getBasePath() + "/certification-services")
                                .part(new MockPart("certificate_profile_info", BASIC_CERT_PROFILE_INFO_PROVIDER.getBytes()))
                                .part(new MockPart("tls_auth", FALSE.getBytes()))
                                .part(new MockPart("certificate", "*.google.com", generateMockCertFile())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("name").value("*.google.com"));
    }

    @SneakyThrows
    private byte[] generateMockCertFile() {
        return IOUtils.toByteArray(this.getClass().getClassLoader().getResourceAsStream("google-cert.der"));
    }

}
