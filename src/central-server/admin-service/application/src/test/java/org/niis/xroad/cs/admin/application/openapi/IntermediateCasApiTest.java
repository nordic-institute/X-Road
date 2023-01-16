/*
 * The MIT License
 *
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

package org.niis.xroad.cs.admin.application.openapi;

import ee.ria.xroad.common.util.CertUtils;

import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MvcResult;

import java.security.cert.X509Certificate;

import static com.jayway.jsonpath.JsonPath.parse;
import static ee.ria.xroad.common.TestCertUtil.generateAuthCert;
import static ee.ria.xroad.common.util.CertUtils.getSubjectCommonName;
import static ee.ria.xroad.common.util.CryptoUtils.calculateCertHexHash;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class IntermediateCasApiTest extends AbstractApiControllerTest {

    @Test
    @WithMockUser(authorities = {"ADD_APPROVED_CA", "VIEW_APPROVED_CA_DETAILS"})
    void getIntermediateCa() throws Exception {
        final byte[] certBytes = generateAuthCert();
        final X509Certificate x509Certificate = CertUtils.readCertificateChain(certBytes)[0];
        final String certHash = calculateCertHexHash(certBytes).toUpperCase();

        final Integer id = addIntermediateCaToCertificationService(100, certBytes); // CA id from initial dataset

        mockMvc.perform(
                        get(commonModuleEndpointPaths.getBasePath() + "/intermediate-cas/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("id", equalTo(id)))
                .andExpect(jsonPath("ca_certificate.hash", equalTo(certHash)))
                .andExpect(jsonPath("ca_certificate.issuer_distinguished_name", equalTo(x509Certificate.getIssuerDN().getName())))
                .andExpect(jsonPath("ca_certificate.subject_distinguished_name", equalTo(x509Certificate.getSubjectDN().getName())))
                .andExpect(jsonPath("ca_certificate.subject_common_name", equalTo(getSubjectCommonName(x509Certificate))))
                .andExpect(jsonPath("ca_certificate.not_before", notNullValue()))
                .andExpect(jsonPath("ca_certificate.not_after", notNullValue()));
    }

    @Test
    @WithMockUser(authorities = {"ADD_APPROVED_CA", "VIEW_APPROVED_CA_DETAILS"})
    void deleteIntermediateCa() throws Exception {
        final byte[] certBytes = generateAuthCert();

        final Integer id = addIntermediateCaToCertificationService(100, certBytes); // CA id from initial dataset

        mockMvc.perform(
                        delete(commonModuleEndpointPaths.getBasePath() + "/intermediate-cas/{id}", id))
                .andExpect(status().isNoContent());

        // verify is deleted
        mockMvc.perform(
                        get(commonModuleEndpointPaths.getBasePath() + "/intermediate-cas/{id}", id))
                .andExpect(status().isNotFound());
    }

    private Integer addIntermediateCaToCertificationService(Integer certificationServiceId, byte[] certBytes) throws Exception {
        final String certHash = calculateCertHexHash(certBytes).toUpperCase();

        final MvcResult mvcResult = mockMvc.perform(
                        multipart(commonModuleEndpointPaths.getBasePath()
                                + "/certification-services/{id}/intermediate-cas", certificationServiceId)
                                .file("certificate", certBytes))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("id", notNullValue()))
                .andExpect(jsonPath("ca_certificate.hash", equalTo(certHash)))
                .andReturn();

        final String response = mvcResult.getResponse().getContentAsString();
        final Integer id = parse(response).read("id");

        return id;
    }

}
