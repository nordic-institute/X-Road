/**
 * The MIT License
 * <p>
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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.niis.xroad.centralserver.openapi.model.InitialServerConf;
import org.niis.xroad.restapi.config.audit.MockableAuditEventLoggingFacade;
import org.niis.xroad.restapi.config.audit.RestApiAuditEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.niis.xroad.centralserver.restapi.util.TestUtils.addApiKeyAuthorizationHeader;

@ActiveProfiles({"test", "audit-test"})
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class AuditLoggingRestTemplateTest extends AbstractApiRestTemplateTestContext {
    @Autowired
    TestRestTemplate restTemplate;

    @SpyBean
    MockableAuditEventLoggingFacade auditEventLoggingFacade;


    @Before
    public void setup() {
        addApiKeyAuthorizationHeader(restTemplate);
    }

    @Test
    public void initializationSuccessAuditLog() {
        InitialServerConf validConf = new InitialServerConf()
                .centralServerAddress("valid.audit.domain.org")
                .instanceIdentifier("VALIDINSTANCEFORAUDITTEST")
                .softwareTokenPin("1234-validVALID");

        ResponseEntity<Object> response = restTemplate.postForEntity(
                "/api/v1/initialization",
                validConf,
                Object.class);

        verify(auditEventLoggingFacade, times(1)).auditLogSuccess();
        ArgumentCaptor<RestApiAuditEvent> eventCaptor = ArgumentCaptor.forClass(RestApiAuditEvent.class);
        ArgumentCaptor<String> userNameCaptor = ArgumentCaptor.forClass(String.class);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> dataCaptor
                = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<String> authCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditEventLoggingFacade, times(1)).callAuditLoggerLogSuccess(
                eventCaptor.capture(),
                userNameCaptor.capture(),
                dataCaptor.capture(),
                authCaptor.capture(),
                urlCaptor.capture());
        Assert.assertEquals(RestApiAuditEvent.INIT_CENTRAL_SERVER, eventCaptor.getValue());
        assertEquals("api-key-1", userNameCaptor.getValue());
        Map<String, Object> data = dataCaptor.getValue();
        assertEquals(3, data.size());
        assertTrue(data.containsKey("centralServerAddress"));
        assertTrue(data.containsKey("instanceIdentifier"));
        assertTrue(data.containsKey("haNode"));
        assertEquals("node_0", data.get("haNode"));
        assertEquals("valid.audit.domain.org", data.get("centralServerAddress"));
        assertEquals("ApiKey", authCaptor.getValue());
        Assert.assertEquals("/api/v1/initialization", urlCaptor.getValue());
        verifyNoMoreInteractions(auditEventLoggingFacade);
    }

    @Test
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    public void initializationFailureAuditLog() {
        InitialServerConf validConf = new InitialServerConf()
                .centralServerAddress("invalid...address")
                .instanceIdentifier("INVALID&&&:INSTANCE")
                .softwareTokenPin("1234-valid");

        ResponseEntity<Object> response = restTemplate.postForEntity(
                "/api/v1/initialization",
                validConf,
                Object.class);

        ArgumentCaptor<RestApiAuditEvent> eventCaptor = ArgumentCaptor.forClass(RestApiAuditEvent.class);
        ArgumentCaptor<String> userNameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<String> authCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditEventLoggingFacade, times(1)).callAuditLoggerLogFailure(
                eventCaptor.capture(),
                userNameCaptor.capture(),
                reasonCaptor.capture(),
                dataCaptor.capture(),
                authCaptor.capture(),
                urlCaptor.capture()
        );
        Assert.assertEquals(RestApiAuditEvent.INIT_CENTRAL_SERVER, eventCaptor.getValue());
        assertEquals("api-key-1", userNameCaptor.getValue());
        assertTrue(reasonCaptor.getValue().startsWith(
                "Validation failed for"));
        // in 7.x this has to be, for some reason:
        //        assertTrue(reasonCaptor.getValue().startsWith("ClientNotFoundException"));
        Map<String, Object> data = dataCaptor.getValue();
        assertEquals(0, data.size());
        assertEquals("ApiKey", authCaptor.getValue());
        assertEquals("/api/v1/initialization", urlCaptor.getValue());
    }


}
