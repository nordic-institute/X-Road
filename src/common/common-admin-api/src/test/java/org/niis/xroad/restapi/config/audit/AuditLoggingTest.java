/*
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
package org.niis.xroad.restapi.config.audit;

import lombok.RequiredArgsConstructor;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.niis.xroad.common.exception.ValidationFailureException;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.stereotype.Controller;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.niis.xroad.common.exception.util.CommonDeviationMessage.INTERNAL_ERROR;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@SpringBootTest(classes = {
        AuditLoggingTest.TestAuditController.class},
        webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ComponentScan({
        "org.niis.xroad.restapi.config.audit",
        "org.niis.xroad.restapi.exceptions",
        "org.niis.xroad.restapi.util"})
@EnableAutoConfiguration(excludeName = {"org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"})
@ActiveProfiles("audit-test")
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class AuditLoggingTest {
    private static final String URL = ControllerUtil.API_V1_PREFIX + "/test-audit/{var1}/{var2}";
    private static final String URL_VAR1 = "var1";
    private static final String URL_VAR2 = "var2";
    private static final String BAD_VALUE = "bad-value";

    @SpyBean
    AuditEventLoggingFacadeImpl auditEventLoggingFacade;

    @Autowired
    protected MockMvc mockMvc;

    @Test
    @WithMockUser(username = "api-key-1", authorities = {"INIT_CONFIG"})
    void initializationSuccessAuditLog() throws Exception {
        mockMvc.perform(get(URL, "123", "random-str")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isOk());
        verify(auditEventLoggingFacade, times(1)).auditLogSuccess();
        ArgumentCaptor<RestApiAuditEvent> eventCaptor = ArgumentCaptor.forClass(RestApiAuditEvent.class);
        ArgumentCaptor<String> userNameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> ipAddressCaptor = ArgumentCaptor.forClass(String.class);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> dataCaptor
                = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<String> authCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(auditEventLoggingFacade, times(1)).callAuditLoggerLogSuccess(
                eventCaptor.capture(),
                userNameCaptor.capture(),
                ipAddressCaptor.capture(),
                dataCaptor.capture(),
                authCaptor.capture(), urlCaptor.capture());
        assertEquals(RestApiAuditEvent.INIT_CENTRAL_SERVER, eventCaptor.getValue());

        var data = dataCaptor.getValue();
        assertEquals(2, data.size());

        assertEquals("123", data.get("id"));
        assertEquals("random-str", data.get("certId"));
        assertEquals("Session", authCaptor.getValue());
        assertEquals("/api/v1/test-audit/123/random-str", urlCaptor.getValue());
        Mockito.verifyNoMoreInteractions(auditEventLoggingFacade);
    }

    @Test
    @WithMockUser(username = "api-key-1", authorities = {"INIT_CONFIG"})
    void initializationFailureAuditLog() throws Exception {
        mockMvc.perform(get(URL, BAD_VALUE, "random-str")
                        .contentType(MediaType.APPLICATION_JSON)
                        .with(csrf()))
                .andExpect(status().isBadRequest());

        ArgumentCaptor<RestApiAuditEvent> eventCaptor = ArgumentCaptor.forClass(RestApiAuditEvent.class);
        ArgumentCaptor<String> userNameCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> ipAddressCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> reasonCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> dataCaptor = ArgumentCaptor.forClass(Map.class);
        ArgumentCaptor<String> authCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);

        verify(auditEventLoggingFacade, times(1)).callAuditLoggerLogFailure(
                eventCaptor.capture(),
                userNameCaptor.capture(),
                ipAddressCaptor.capture(),
                reasonCaptor.capture(),
                dataCaptor.capture(),
                authCaptor.capture(),
                urlCaptor.capture());

        assertEquals(RestApiAuditEvent.INIT_CENTRAL_SERVER, eventCaptor.getValue());
        assertEquals(INTERNAL_ERROR.getDescription(), reasonCaptor.getValue());

        Map<String, Object> data = dataCaptor.getValue();
        assertEquals(0, data.size());
        assertEquals("Session", authCaptor.getValue());
        assertEquals("/api/v1/test-audit/bad-value/random-str", urlCaptor.getValue());
    }

    @Controller
    @RequestMapping(ControllerUtil.API_V1_PREFIX)
    @PreAuthorize("denyAll")
    @RequiredArgsConstructor
    static class TestAuditController {
        private final AuditDataHelper auditDataHelper;

        @GetMapping(value = "/test-audit/{var1}/{var2}",
                produces = {MediaType.APPLICATION_JSON_VALUE},
                consumes = {MediaType.APPLICATION_JSON_VALUE})
        @PreAuthorize("hasAuthority('INIT_CONFIG')")
        @AuditEventMethod(event = RestApiAuditEvent.INIT_CENTRAL_SERVER)
        ResponseEntity<Void> initCentralServer(
                @PathVariable(URL_VAR1) String var1,
                @PathVariable(URL_VAR2) String var2) {
            if (BAD_VALUE.equals(var1)) {
                throw new ValidationFailureException(INTERNAL_ERROR);
            }
            auditDataHelper.put(RestApiAuditProperty.ID, var1);
            auditDataHelper.put(RestApiAuditProperty.CERT_ID, var2);

            return ResponseEntity.ok().build();
        }
    }
}
