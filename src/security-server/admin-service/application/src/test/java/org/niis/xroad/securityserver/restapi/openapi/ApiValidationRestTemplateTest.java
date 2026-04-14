/*
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
package org.niis.xroad.securityserver.restapi.openapi;

import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;
import org.niis.xroad.restapi.openapi.model.ErrorInfo;
import org.niis.xroad.securityserver.restapi.openapi.model.LocalGroupAddDto;
import org.niis.xroad.securityserver.restapi.util.TestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.reactive.server.WebTestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Test generic API/JSON validation.
 */
public class ApiValidationRestTemplateTest extends AbstractApiControllerTestContext {

    @Autowired
    WebTestClient webTestClient;

    private WebTestClient client;

    private ObjectMapper testObjectMapper = JsonMapper.builder().build();

    @Before
    public void setup() throws Exception {
        client = TestUtils.addApiKeyAuthorizationHeader(webTestClient);
    }

    /**
     * Simple test for checking that API validation is functional, by sending a LocalGroupAdd object
     * with too long property value
     * @throws Exception
     */
    @Test
    @WithMockUser(authorities = {"ADD_LOCAL_GROUP"})
    public void validationWorksForAddLocalGroup() throws Exception {
        LocalGroupAddDto groupWithTooLongCode = new LocalGroupAddDto()
                .code(RandomStringUtils.secure().nextAlphabetic(256))
                .description("foo");

        Map responseBody = client.post()
                .uri("/api/v1/clients/FOO:BAR:BAZ:NONEXISTENT-CLIENT/local-groups")
                .bodyValue(groupWithTooLongCode)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();

        /**
         * Expecting this response
         * {
         *   "status": 400,
         *   "error": {
         *     "code": "validation_failure",
         *     "validation_errors": {
         *       "localGroupAdd.code": [
         *         "Size"
         *       ]
         *     }
         *   }
         * }
         */

        ErrorInfo errorResponse = testObjectMapper.convertValue(responseBody, ErrorInfo.class);
        assertNotNull(errorResponse);
        assertEquals("validation_failure", errorResponse.getError().getCode());
        assertEquals(1, errorResponse.getError().getValidationErrors().size());
        String localGroupAddCodeError = "localGroupAddDto.code";
        assertTrue(errorResponse.getError().getValidationErrors().containsKey(localGroupAddCodeError));
        assertEquals(1, errorResponse.getError().getValidationErrors().get(localGroupAddCodeError).size());
        assertEquals("Size", errorResponse.getError().getValidationErrors().get("localGroupAddDto.code").get(0));
    }

    /**
     * Simple test for checking that API validation is functional, by sending a LocalGroupAdd object
     * with too long property value
     * @throws Exception
     */
    @Test
    @WithMockUser(authorities = {"ADD_LOCAL_GROUP"})
    public void validationWorksForAddLocalGroupWithInvalidDescription() throws Exception {
        LocalGroupAddDto groupWithInvalidDescription = new LocalGroupAddDto()
                .code(RandomStringUtils.secure().nextAlphabetic(10))
                .description("foo\u00a3$");

        Map responseBody = client.post()
                .uri("/api/v1/clients/FOO:BAR:BAZ:NONEXISTENT-CLIENT/local-groups")
                .bodyValue(groupWithInvalidDescription)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody(Map.class)
                .returnResult()
                .getResponseBody();

        ErrorInfo errorResponse = testObjectMapper.convertValue(responseBody, ErrorInfo.class);
        assertNotNull(errorResponse);
        assertEquals("validation_failure", errorResponse.getError().getCode());
        assertEquals(1, errorResponse.getError().getValidationErrors().size());
        String localGroupAddDescriptionError = "localGroupAddDto.description";
        assertTrue(errorResponse.getError().getValidationErrors().containsKey(localGroupAddDescriptionError));
        assertEquals(1, errorResponse.getError().getValidationErrors().get(localGroupAddDescriptionError).size());
        assertEquals("Pattern", errorResponse.getError().getValidationErrors().get("localGroupAddDto.description").get(0));
    }
}
