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
package org.niis.xroad.common.mail;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.class)
public class MailServiceTest {

    private MailService mailService;

    @Mock
    private JavaMailSender mailSender;

    @Before
    public void setUp() {
        MailNotificationProperties mailNotificationConfiguration = new MailNotificationProperties();
        mailNotificationConfiguration.setHost("localhost");
        mailNotificationConfiguration.setPort(587);
        mailNotificationConfiguration.setUsername("admin");
        mailNotificationConfiguration.setPassword("secret");
        mailNotificationConfiguration.setContacts(Map.of("TestMember", "myMail@example.org"));
        mailService = new MailService(mailNotificationConfiguration, mailSender);
    }

    @Test
    public void getMailNotificationStatus() {
        MailService.MailNotificationStatus mailNotificationStatus = mailService.getMailNotificationStatus();
        assertTrue(mailNotificationStatus.configurationPresent());
        assertTrue(mailNotificationStatus.enabledNotifications().contains(MailNotificationType.ACME_FAILURE));
        assertTrue(mailNotificationStatus.enabledNotifications().contains(MailNotificationType.ACME_SUCCESS));
        assertTrue(mailNotificationStatus.enabledNotifications().contains(MailNotificationType.AUTH_CERT_REGISTERED));
        assertTrue(mailNotificationStatus.enabledNotifications().contains(MailNotificationType.ACME_CERT_AUTOMATICALLY_ACTIVATED));
        assertTrue(mailNotificationStatus.enabledNotifications().contains(MailNotificationType.ACME_CERT_AUTOMATIC_ACTIVATION_FAILURE));
        assertEquals(List.of("TestMember: myMail@example.org"), mailNotificationStatus.recipientsEmails());
    }

    @Test
    public void sendTestMailThrows() {
        doThrow(new MailSendException("Fatal error!")).when(mailSender).send((SimpleMailMessage) any());
        assertThrows(MailSendException.class, () -> mailService.sendTestMail("recipient", "subject", "body"));
        verify(mailSender).send((SimpleMailMessage) any());
    }

    @Test
    public void sendMailAsyncDoesntThrows() {
        doThrow(new MailSendException("Fatal error!")).when(mailSender).send((SimpleMailMessage) any());
        assertDoesNotThrow(() -> mailService.sendMailAsync("recipient", "subject", "body"));
        verify(mailSender).send((SimpleMailMessage) any());
    }

}
