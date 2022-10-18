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

import ee.ria.xroad.common.TestCertUtil;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.Test;
import org.niis.xroad.centralserver.openapi.model.ApprovedCertificationServiceDto;
import org.niis.xroad.centralserver.openapi.model.CertificationServiceSettingsDto;
import org.niis.xroad.centralserver.openapi.model.OcspResponderDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockPart;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.ResultActions;

import java.time.OffsetDateTime;

import static org.apache.commons.lang3.BooleanUtils.FALSE;
import static org.apache.commons.lang3.BooleanUtils.TRUE;
import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
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
    private static final String ISSUER_DISTINGUISHED_NAME = "CN=R3, O=Let's Encrypt, C=US";
    private static final String SUBJECT_DISTINGUISHED_NAME = "CN=x-road.global";

    private static final String PUBLIC_KEY_MODULUS = "2151337145236360089905485986239099550408653247848020894166069752"
            + "0416378609304765007780222461167023921306298709865053553264571232520963553301724989345282355757634100081"
            + "0258344926231639837331468122241370837604080589551961120675968964630965115354573794618014405466949546724"
            + "4201705186303922225178667308300514801910429150661494168487329789787848962256761338099544609344525660587"
            + "2579759495640701008157064858289660210909976083547958403393634030552367189161406679433747845402338839149"
            + "1440591425939889179496380726624809248875109616596096080404895105177498452149263637853276502776347214179"
            + "55234262167985570855634033219952254891";
    private static final String SIGNATURE = "30c8191a152e0cd78cb531a112600ff7bdbeff2a19e0dcd698c2a9ffcc72ac44fda1c7527"
            + "a552a91b1b93278d5c4cc6ba3d87a20fc660190dd3251f4c29e540d3d759fbb75f2c65041ad5a0c26662826ab762fc38696aee6"
            + "16c3890cdb3dcc407a75a93ddbfdb7e8fb86fb6adc1a27e81534398c78ce73f62a3b2552650386b482d33300d9685d764767f58"
            + "44dee80854f8ce34cbacfc1bb4b8961b47ebbe0af22c0288d97fb20cb671e46643c98ad376e1323605025637468abdbf1db84ed"
            + "aca29fde58ca16d1c073661af7da2b9bd54352979fab2e8c1d6f7ba54b35cb6b1a360ddff7c49589ab4a1d774c3ab00ba176028"
            + "6149d0bc98d77c57d46d57644f6";

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @WithMockUser(authorities = {"VIEW_APPROVED_CAS"})
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
    void getCertificationService() throws Exception {
        String newApprovedCa = callAddCertificationService().andReturn()
                .getResponse().getContentAsString();
        Integer id = objectMapper.readValue(newApprovedCa, ApprovedCertificationServiceDto.class).getId();

        mockMvc.perform(
                        get(commonModuleEndpointPaths.getBasePath() + "/certification-services/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("name", equalTo("x-road.global")))
                .andExpect(jsonPath("tls_auth", equalTo(false)))
                .andExpect(jsonPath("issuer_distinguished_name", equalTo(ISSUER_DISTINGUISHED_NAME)))
                .andExpect(jsonPath("subject_distinguished_name", equalTo(SUBJECT_DISTINGUISHED_NAME)))
                .andExpect(jsonPath("certificate_profile_info", equalTo(BASIC_CERT_PROFILE_INFO_PROVIDER)));
    }

    @Test
    @WithMockUser(authorities = {"ADD_APPROVED_CA", "EDIT_APPROVED_CA"})
    void editCertificationServiceSettings() throws Exception {
        String newApprovedCa = callAddCertificationService().andReturn()
                .getResponse().getContentAsString();
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

    @Test
    @WithMockUser(authorities = "ADD_APPROVED_CA")
    void addCertificationServiceOcspResponder() throws Exception {
        var result = mockMvc.perform(
                        multipart(commonModuleEndpointPaths.getBasePath() + "/certification-services/100/ocsp-responders")
                                .part(new MockPart("url", "http://localhost:1234".getBytes()))
                                .part(new MockPart("certificate", "ocsp.crt",
                                        TestCertUtil.getOcspSigner().certChain[0].getEncoded())))
                .andExpect(status().isCreated())
                .andReturn();
        OcspResponderDto created =
                objectMapper.readValue(result.getResponse().getContentAsString(), OcspResponderDto.class);
        assertThat(created.getId()).isNotNull();
        assertThat(created.getUrl()).isEqualTo("http://localhost:1234");
        assertThat(created.getCreatedAt()).isBefore(OffsetDateTime.now());
        assertThat(created.getUpdatedAt()).isBefore(OffsetDateTime.now());
    }

    @Test
    @WithMockUser(authorities = {"ADD_APPROVED_CA", "VIEW_APPROVED_CA_DETAILS"})
    void getCertificationServiceCertificate() throws Exception {
        String newApprovedCa = callAddCertificationService().andReturn()
                .getResponse().getContentAsString();
        Integer id = objectMapper.readValue(newApprovedCa, ApprovedCertificationServiceDto.class).getId();

        mockMvc.perform(
                        get(commonModuleEndpointPaths.getBasePath() + "/certification-services/{id}/certificate", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("hash", equalTo("A12B13AA70A5249D583708B29DEF0ED1B2385281")))
                .andExpect(jsonPath("issuer_common_name", equalTo("R3")))
                .andExpect(jsonPath("issuer_distinguished_name", equalTo(ISSUER_DISTINGUISHED_NAME)))
                .andExpect(jsonPath("key_usages", containsInAnyOrder("DIGITAL_SIGNATURE", "KEY_ENCIPHERMENT")))
                .andExpect(jsonPath("not_after", equalTo("2022-12-08T05:06:10Z")))
                .andExpect(jsonPath("not_before", equalTo("2022-09-09T05:06:11Z")))
                .andExpect(jsonPath("public_key_algorithm", equalTo("RSA")))
                .andExpect(jsonPath("rsa_public_key_exponent", equalTo(65537)))
                .andExpect(jsonPath("rsa_public_key_modulus", equalTo(PUBLIC_KEY_MODULUS)))
                .andExpect(jsonPath("serial", equalTo("278991236811712207105391797571681003311405")))
                .andExpect(jsonPath("signature", equalTo(SIGNATURE)))
                .andExpect(jsonPath("signature_algorithm", equalTo("SHA256withRSA")))
                .andExpect(jsonPath("subject_alternative_names", equalTo("DNS:x-road.global")))
                .andExpect(jsonPath("subject_common_name", equalTo("x-road.global")))
                .andExpect(jsonPath("subject_distinguished_name", equalTo(SUBJECT_DISTINGUISHED_NAME)))
                .andExpect(jsonPath("version", equalTo(3)));
    }

    private ResultActions callAddCertificationService() throws Exception {
        final String filename = "x-road.global.der";
        return mockMvc.perform(
                        multipart(commonModuleEndpointPaths.getBasePath() + "/certification-services")
                                .part(new MockPart("certificate_profile_info", BASIC_CERT_PROFILE_INFO_PROVIDER.getBytes()))
                                .part(new MockPart("tls_auth", FALSE.getBytes()))
                                .part(new MockPart("certificate", filename, generateMockCertFile(filename))))
                .andExpect(status().isCreated());
    }

    @SneakyThrows
    private byte[] generateMockCertFile(String filename) {
        return IOUtils.toByteArray(this.getClass().getClassLoader().getResourceAsStream(filename));
    }

}
