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
import org.junit.After;
import org.junit.Test;
import org.niis.xroad.centralserver.openapi.model.InitialServerConf;
import org.niis.xroad.centralserver.restapi.service.TokenPinValidator;
import org.niis.xroad.centralserver.restapi.util.TestUtils;
import org.niis.xroad.restapi.openapi.model.ErrorInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Transactional
public class InitializationApiControllerRestTemplateTest extends AbstractApiControllerTestContext {

    private final ObjectMapper testObjectMapper = new ObjectMapper();
    @Autowired
    TestRestTemplate restTemplate;

    @Autowired
    private TokenPinValidator tokenPinValidator;

    @After
    public void cleanUp() {
        tokenPinValidator.setTokenPinEnforced(false);
    }

    @Test
    public void initializationInvalidParamsRespondsCorrectly() {
        // All privileges role api Key added to all TestRestTemplate requests
        TestUtils.addApiKeyAuthorizationHeader(restTemplate);
        InitialServerConf invalidConf = new InitialServerConf()
                .centralServerAddress("123.123..invalid..123.x")
                .instanceIdentifier("INSTANCE::::%INVALID")
                .softwareTokenPin("1234-VALID");
        ResponseEntity<Object> response = restTemplate.postForEntity(
                "/api/v1/initialization",
                invalidConf,
                Object.class);
        assertEquals(400, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        ErrorInfo errorInfo = testObjectMapper.convertValue(response.getBody(), ErrorInfo.class);
        assertEquals("validation_failure", errorInfo.getError().getCode());
        assertEquals(2, errorInfo.getError().getValidationErrors().size());
    }

    @Test
    public void initializationMissingParamsRespondsCorrectly() {
        // All privileges role api Key added to all TestRestTemplate requests
        TestUtils.addApiKeyAuthorizationHeader(restTemplate);
        InitialServerConf invalidConf = new InitialServerConf()
                .centralServerAddress("")
                .instanceIdentifier("INSTANCE_VALID")
                .softwareTokenPin("");
        ResponseEntity<Object> response = restTemplate.postForEntity(
                "/api/v1/initialization",
                invalidConf,
                Object.class);
        assertEquals(400, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        ErrorInfo errorInfo = testObjectMapper.convertValue(response.getBody(), ErrorInfo.class);
        assertEquals("validation_failure", errorInfo.getError().getCode());
        assertEquals(2, errorInfo.getError().getValidationErrors().size());
    }

    @Test
    public void initializationWithWeakPin() {
        // All privileges role api Key added to all TestRestTemplate requests
        TestUtils.addApiKeyAuthorizationHeader(restTemplate);

        tokenPinValidator.setTokenPinEnforced(true);

        InitialServerConf tooShortPinConfig = new InitialServerConf()
                .centralServerAddress("123.123.123.123")
                .instanceIdentifier("INSTANCE-VALID")
                .softwareTokenPin("12");
        ResponseEntity<Object> response = restTemplate.postForEntity(
                "/api/v1/initialization",
                tooShortPinConfig,
                Object.class);
        assertEquals(400, response.getStatusCodeValue());
        assertNotNull(response.getBody());
        ErrorInfo errorInfo = testObjectMapper.convertValue(response.getBody(), ErrorInfo.class);
        assertEquals("weak_pin", errorInfo.getError().getCode());
        assertNull("No validation errors from weak pin", errorInfo.getError().getValidationErrors());
        List<String> metadata = errorInfo.getError().getMetadata();
        assertEquals(4, errorInfo.getError().getMetadata().size());
        List<String> expectedMetadata = Arrays.asList("pin_min_length", "10", "pin_min_char_classes_count", "3");
        assertTrue(metadata.containsAll(expectedMetadata));
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
