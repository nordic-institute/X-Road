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

import org.junit.Before;
import org.junit.Test;
import org.niis.xroad.securityserver.restapi.openapi.model.MailNotificationStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.MailNotificationTypeDto;
import org.niis.xroad.securityserver.restapi.openapi.model.MailRecipientDto;
import org.niis.xroad.securityserver.restapi.openapi.model.MailStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.TestMailResponseDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailSendException;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.niis.xroad.securityserver.restapi.util.TestUtils.OWNER_SERVER_ID;

/**
 * test mail api
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"xroad.conf.path=build/resources/test/"})
public class MailApiControllerTest extends AbstractApiControllerTestContext {

    @Autowired
    private MailApiController mailApiController;

    @Before
    public void before() {
        when(currentSecurityServerId.getServerId()).thenReturn(OWNER_SERVER_ID);
    }

    @Test
    @WithMockUser(authorities = {"DIAGNOSTICS"})
    public void getMailNotificationStatus() {
        ResponseEntity<MailNotificationStatusDto> mailNotificationStatus = mailApiController.getMailNotificationStatus();
        Set<MailNotificationTypeDto> activatedTypes = Set.of(MailNotificationTypeDto.ACME_FAILURE,
                MailNotificationTypeDto.ACME_SUCCESS,
                MailNotificationTypeDto.AUTH_CERT_REGISTERED,
                MailNotificationTypeDto.ACME_CERT_AUTOMATICALLY_ACTIVATED,
                MailNotificationTypeDto.ACME_CERT_AUTOMATIC_ACTIVATION_FAILURE);
        assertEquals(activatedTypes, mailNotificationStatus.getBody().getEnabledNotifications());
        assertEquals(true, mailNotificationStatus.getBody().getConfigurationPresent());
        assertEquals(List.of("DEV:COM:1234: member1@example.org"), mailNotificationStatus.getBody().getRecipientsEmails());
    }

    @Test
    @WithMockUser(authorities = {"DIAGNOSTICS"})
    public void testSendMail() {
        ResponseEntity<TestMailResponseDto> testMailResponse =
                mailApiController.sendTestMail(new MailRecipientDto("test@mailaddress.org"));
        assertEquals(MailStatusDto.SUCCESS, testMailResponse.getBody().getStatus());
    }

    @Test
    @WithMockUser(authorities = {"DIAGNOSTICS"})
    public void testSendMailException() {

        doThrow(new MailSendException("Sending failed")).when(mailService).sendTestMail(any(), any(), any());
        ResponseEntity<TestMailResponseDto> testMailResponse =
                mailApiController.sendTestMail(new MailRecipientDto("test@mailaddress.org"));
        assertEquals(MailStatusDto.ERROR, testMailResponse.getBody().getStatus());
    }
}
