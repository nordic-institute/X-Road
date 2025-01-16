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

import ee.ria.xroad.common.SystemProperties;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.niis.xroad.securityserver.restapi.openapi.model.MailNotificationStatus;
import org.niis.xroad.securityserver.restapi.openapi.model.MailRecipient;
import org.niis.xroad.securityserver.restapi.openapi.model.MailStatus;
import org.niis.xroad.securityserver.restapi.openapi.model.TestMailResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailSendException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.niis.xroad.securityserver.restapi.util.TestUtils.OWNER_SERVER_ID;

/**
 * test mail api
 */
@ActiveProfiles({"test"})
public class MailApiControllerTest extends AbstractApiControllerTestContext {

    @Autowired
    private MailApiController mailApiController;

    @BeforeClass
    public static void beforeClass() {
        System.setProperty(SystemProperties.CONF_PATH, "build/resources/test/");
    }

    @AfterClass
    public static void afterClass() {
        System.clearProperty(SystemProperties.CONF_PATH);

    }

    @Before
    public void before() {
        when(currentSecurityServerId.getServerId()).thenReturn(OWNER_SERVER_ID);
    }

    @Test
    @WithMockUser(authorities = {"DIAGNOSTICS"})
    public void getMailNotificationStatus() {
        ResponseEntity<MailNotificationStatus> mailNotificationStatus = mailApiController.getMailNotificationStatus();
        assertEquals(true, mailNotificationStatus.getBody().getAcmeFailureStatus());
        assertEquals(true, mailNotificationStatus.getBody().getAcmeSuccessStatus());
        assertEquals(true, mailNotificationStatus.getBody().getAuthCertRegisteredStatus());
        assertEquals(true, mailNotificationStatus.getBody().getConfigurationPresent());
        assertEquals(List.of("DEV:COM:1234: member1@example.org"), mailNotificationStatus.getBody().getRecipientsEmails());
    }

    @Test
    @WithMockUser(authorities = {"DIAGNOSTICS"})
    public void testSendMail() {
        ResponseEntity<TestMailResponse> testMailResponse =
                mailApiController.sendTestMail(new MailRecipient("test@mailaddress.org"));
        assertEquals(MailStatus.SUCCESS, testMailResponse.getBody().getStatus());
    }

    @Test
    @WithMockUser(authorities = {"DIAGNOSTICS"})
    public void testSendMailException() {

        doThrow(new MailSendException("Sending failed")).when(mailService).sendTestMail(any(), any(), any());
        ResponseEntity<TestMailResponse> testMailResponse =
                mailApiController.sendTestMail(new MailRecipient("test@mailaddress.org"));
        assertEquals(MailStatus.ERROR, testMailResponse.getBody().getStatus());
    }
}
