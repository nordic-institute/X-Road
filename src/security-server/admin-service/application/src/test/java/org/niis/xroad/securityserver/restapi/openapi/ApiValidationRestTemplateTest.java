/*
 * The MIT License
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

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.RandomStringUtils;
import org.junit.Before;
import org.junit.Test;
import org.niis.xroad.restapi.openapi.model.ErrorInfo;
import org.niis.xroad.securityserver.restapi.openapi.model.LocalGroupAdd;
import org.niis.xroad.securityserver.restapi.util.TestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Test generic API/JSON validation.
 */
public class ApiValidationRestTemplateTest extends AbstractApiControllerTestContext {

    @Autowired
    TestRestTemplate restTemplate;

    private ObjectMapper testObjectMapper = new ObjectMapper();

    @Before
    public void setup() throws Exception {
        TestUtils.addApiKeyAuthorizationHeader(restTemplate);
    }

    /**
     * Simple test for checking that API validation is functional, by sending a LocalGroupAdd object
     * with too long property value
     * @throws Exception
     */
    @Test
    @WithMockUser(authorities = {"ADD_LOCAL_GROUP"})
    public void validationWorksForAddLocalGroup() throws Exception {
        LocalGroupAdd groupWithTooLongCode = new LocalGroupAdd()
                .code(RandomStringUtils.randomAlphabetic(256))
                .description("foo");
        ResponseEntity<Object> response = restTemplate.postForEntity(
                "/api/v1/clients/FOO:BAR:BAZ:NONEXISTENT-CLIENT/local-groups",
                groupWithTooLongCode, Object.class);

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

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
        ErrorInfo errorResponse = testObjectMapper.convertValue(response.getBody(), ErrorInfo.class);
        assertNotNull(errorResponse);
        assertEquals("validation_failure", errorResponse.getError().getCode());
        assertEquals(1, errorResponse.getError().getValidationErrors().size());
        String localGroupAddCodeError = "localGroupAdd.code";
        assertTrue(errorResponse.getError().getValidationErrors().containsKey(localGroupAddCodeError));
        assertEquals(1, errorResponse.getError().getValidationErrors().get(localGroupAddCodeError).size());
        assertEquals("Size", errorResponse.getError().getValidationErrors().get("localGroupAdd.code").get(0));
    }
}
