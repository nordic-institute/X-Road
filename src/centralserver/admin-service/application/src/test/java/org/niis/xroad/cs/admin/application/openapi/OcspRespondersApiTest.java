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

import ee.ria.xroad.common.TestCertUtil;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockPart;
import org.springframework.security.test.context.support.WithMockUser;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.springframework.http.HttpMethod.PATCH;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OcspRespondersApiTest extends AbstractApiControllerTest {

    @Test
    @WithMockUser(authorities = {"EDIT_APPROVED_CA", "ADD_APPROVED_CA"})
    void updateOcspResponder() throws Exception {
        final byte[] cert = TestCertUtil.getOcspSigner().certChain[0].getEncoded();

        final Integer id = addOcspResponder(100, cert); // certification service 100 from initial data set

        final String newUrl = "https://new.url";
        final byte[] newCert = TestCertUtil.generateAuthCert();

        mockMvc.perform(multipart(PATCH, commonModuleEndpointPaths.getBasePath() + "/ocsp-responders/{id}", id)
                        .file("certificate", newCert)
                        .part(new MockPart("url", newUrl.getBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("url", equalTo(newUrl)));
    }

    @Test
    @WithMockUser(authorities = {"EDIT_APPROVED_CA", "ADD_APPROVED_CA"})
    void updateOcspResponderOnlyUrlUpdate() throws Exception {
        final byte[] cert = TestCertUtil.getOcspSigner().certChain[0].getEncoded();

        final Integer id = addOcspResponder(100, cert); // certification service 100 from initial data set

        final String newUrl = "https://new.url";

        mockMvc.perform(multipart(PATCH, commonModuleEndpointPaths.getBasePath() + "/ocsp-responders/{id}", id)
                        .part(new MockPart("url", newUrl.getBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("url", equalTo(newUrl)));
    }

    @Test
    @WithMockUser(authorities = {"EDIT_APPROVED_CA", "ADD_APPROVED_CA"})
    void deleteOcspResponder() throws Exception {
        final byte[] cert = TestCertUtil.getOcspSigner().certChain[0].getEncoded();

        final Integer id = addOcspResponder(100, cert); // certification service 100 from initial data set

        mockMvc.perform(delete(commonModuleEndpointPaths.getBasePath() + "/ocsp-responders/{id}", id))
                .andExpect(status().isNoContent());
    }

    private Integer addOcspResponder(Integer certServiceId, byte[] cert) throws Exception {
        var result = mockMvc.perform(
                        multipart(commonModuleEndpointPaths.getBasePath() + "/certification-services/"
                                + certServiceId + "/ocsp-responders")
                                .file("certificate", cert)
                                .part(new MockPart("url", "http://localhost:1234".getBytes())))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        return JsonPath.parse(result).read("id", Integer.class);
    }
}
