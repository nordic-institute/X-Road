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
import org.junit.Test;
import org.niis.xroad.centralserver.openapi.model.InitialServerConf;
import org.niis.xroad.centralserver.restapi.util.TestUtils;
import org.niis.xroad.restapi.openapi.model.ErrorInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@Transactional
public class InitializationApiControllerRestTemplateTest extends AbstractApiControllerTestContext {

    private final ObjectMapper testObjectMapper = new ObjectMapper();
    @Autowired
    TestRestTemplate restTemplate;

    @Test
    public void initializationInvalidParamsRespondsCorrectly() {
        // All privileges role api Key added to all TestRestTemplate requests
        TestUtils.addApiKeyAuthorizationHeader(restTemplate);
        InitialServerConf invalidConf = new InitialServerConf()
                .centralServerAddress("123.123.123.123")
                .instanceIdentifier(null)
                .softwareTokenPin("1234-valid");
        ResponseEntity<Object> response = restTemplate.postForEntity(
                "/api/v1/initialization",
                invalidConf,
                Object.class);
        assertEquals(400, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        ErrorInfo errorInfo = testObjectMapper.convertValue(response.getBody(), ErrorInfo.class);
        assertEquals("validation_failure", errorInfo.getError().getCode());
        assertEquals(1, errorInfo.getError().getValidationErrors().size());
        assertNull(errorInfo.getWarnings(), "No warnings requested");
    }

    @Test
    public void nonAuthorizedInitializationShoudFail() {
        InitialServerConf validConf = new InitialServerConf()
                .centralServerAddress("valid.domain.org")
                .instanceIdentifier("VALIDINSTANCE")
                .softwareTokenPin("1234-valid");

        ResponseEntity<Object> response = restTemplate.postForEntity(
                "/api/v1/initialization",
                validConf,
                Object.class);

        assertEquals(HttpStatus.UNAUTHORIZED.value(), response.getStatusCodeValue());

    }

    @Test
    public void correctInitializationOK() {
        InitialServerConf validConf = new InitialServerConf()
                .centralServerAddress("valid.domain.org")
                .instanceIdentifier("VALIDINSTANCE")
                .softwareTokenPin("1234-valid");

        TestUtils.addApiKeyAuthorizationHeader(restTemplate);
        ResponseEntity<Object> response = restTemplate.postForEntity(
                "/api/v1/initialization",
                validConf,
                Object.class);

        assertEquals(HttpStatus.OK.value(), response.getStatusCodeValue());

    }

}
