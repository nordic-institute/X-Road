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
package org.niis.xroad.securityserver.restapi.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties;
import org.niis.xroad.securityserver.restapi.mail.MailNotificationProperties;
import org.niis.xroad.securityserver.restapi.mail.MailService;
import org.niis.xroad.securityserver.restapi.mail.NotificationConfig;
import org.springframework.context.support.MessageSourceAccessor;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MailNotificationHelperTest {

    private static final String HOSTNAME = "ds.example.org";

    @Mock
    private MailNotificationProperties mailNotificationProperties;
    @Mock
    private NotificationConfig notificationConfig;
    @Mock
    private MessageSourceAccessor notificationMessageSourceAccessor;
    @Mock
    private MailService mailService;
    @Mock
    private AdminServiceProperties adminServiceProperties;
    @Mock
    private AdminServiceProperties.Dataspace dataspace;

    private MailNotificationHelper helper;

    @BeforeEach
    void setUp() {
        lenient().when(adminServiceProperties.getDataspace()).thenReturn(dataspace);
        lenient().when(notificationMessageSourceAccessor.getMessage(anyString(), any(String[].class))).thenReturn("title-or-content");
        lenient().when(notificationMessageSourceAccessor.getMessage(anyString())).thenReturn("title-or-content");
        helper = new MailNotificationHelper(mailNotificationProperties, notificationConfig, notificationMessageSourceAccessor,
                mailService, adminServiceProperties);
    }

    @Test
    void sendDsTlsAcmeSuccessNotificationShouldSendToEveryConfiguredContactWhenEnabled() {
        when(dataspace.isTlsCertificateRenewalSuccessNotificationEnabled()).thenReturn(true);
        when(dataspace.getTlsCertificateNotificationContacts()).thenReturn(List.of("a@example.org", "b@example.org"));

        helper.sendDsTlsAcmeSuccessNotification(HOSTNAME, false);

        verify(mailService).sendMailAsync(eq("a@example.org"), anyString(), anyString());
        verify(mailService).sendMailAsync(eq("b@example.org"), anyString(), anyString());
        verify(notificationMessageSourceAccessor).getMessage(eq("acme_ds_tls_cert_renewal_success_title"), eq(new String[]{"enrollment"}));
        verify(notificationMessageSourceAccessor).getMessage(eq("acme_ds_tls_cert_renewal_success_content"),
                eq(new String[]{HOSTNAME, "enrolled"}));
    }

    @Test
    void sendDsTlsAcmeSuccessNotificationShouldDistinguishARenewalFromAnEnrollment() {
        when(dataspace.isTlsCertificateRenewalSuccessNotificationEnabled()).thenReturn(true);
        when(dataspace.getTlsCertificateNotificationContacts()).thenReturn(List.of("a@example.org"));

        helper.sendDsTlsAcmeSuccessNotification(HOSTNAME, true);

        verify(notificationMessageSourceAccessor).getMessage(eq("acme_ds_tls_cert_renewal_success_title"), eq(new String[]{"renewal"}));
        verify(notificationMessageSourceAccessor).getMessage(eq("acme_ds_tls_cert_renewal_success_content"),
                eq(new String[]{HOSTNAME, "renewed"}));
    }

    @Test
    void sendDsTlsAcmeSuccessNotificationShouldDoNothingWhenDisabled() {
        when(dataspace.isTlsCertificateRenewalSuccessNotificationEnabled()).thenReturn(false);

        helper.sendDsTlsAcmeSuccessNotification(HOSTNAME, false);

        verifyNoInteractions(mailService);
    }

    @Test
    void sendDsTlsAcmeSuccessNotificationShouldDoNothingWhenNoContactsAreConfigured() {
        when(dataspace.isTlsCertificateRenewalSuccessNotificationEnabled()).thenReturn(true);
        when(dataspace.getTlsCertificateNotificationContacts()).thenReturn(null);

        helper.sendDsTlsAcmeSuccessNotification(HOSTNAME, false);

        verifyNoInteractions(mailService);
    }

    @Test
    void sendDsTlsAcmeSuccessNotificationShouldBeIndependentOfTheAuthSignSuccessToggle() {
        // notificationConfig deliberately left unstubbed (defaults to false) and unused by the DS TLS path
        when(dataspace.isTlsCertificateRenewalSuccessNotificationEnabled()).thenReturn(true);
        when(dataspace.getTlsCertificateNotificationContacts()).thenReturn(List.of("a@example.org"));

        helper.sendDsTlsAcmeSuccessNotification(HOSTNAME, false);

        verify(mailService).sendMailAsync(eq("a@example.org"), anyString(), anyString());
        verifyNoInteractions(notificationConfig);
    }

    @Test
    void sendDsTlsAcmeFailureNotificationShouldSendToEveryConfiguredContactWhenEnabled() {
        when(dataspace.isTlsCertificateRenewalFailureNotificationEnabled()).thenReturn(true);
        when(dataspace.getTlsCertificateNotificationContacts()).thenReturn(List.of("a@example.org"));

        helper.sendDsTlsAcmeFailureNotification(HOSTNAME, "CA unreachable");

        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        verify(mailService, times(1)).sendMailAsync(eq("a@example.org"), subjectCaptor.capture(), anyString());
        verify(notificationMessageSourceAccessor).getMessage(eq("acme_ds_tls_cert_renewal_failure_title"));
        verify(notificationMessageSourceAccessor).getMessage(eq("acme_ds_tls_cert_renewal_failure_content"),
                eq(new String[]{HOSTNAME, "CA unreachable"}));
    }

    @Test
    void sendDsTlsAcmeFailureNotificationShouldDoNothingWhenDisabled() {
        when(dataspace.isTlsCertificateRenewalFailureNotificationEnabled()).thenReturn(false);

        helper.sendDsTlsAcmeFailureNotification(HOSTNAME, "CA unreachable");

        verifyNoInteractions(mailService);
    }

    @Test
    void sendDsTlsAcmeFailureNotificationShouldDoNothingWhenNoContactsAreConfigured() {
        when(dataspace.isTlsCertificateRenewalFailureNotificationEnabled()).thenReturn(true);
        when(dataspace.getTlsCertificateNotificationContacts()).thenReturn(List.of());

        helper.sendDsTlsAcmeFailureNotification(HOSTNAME, "CA unreachable");

        verify(mailService, never()).sendMailAsync(any(), any(), any());
    }

    @Test
    void sendDsTlsAcmeFailureNotificationShouldBeIndependentOfTheAuthSignFailureToggle() {
        // notificationConfig deliberately left unstubbed (defaults to false) and unused by the DS TLS path
        when(dataspace.isTlsCertificateRenewalFailureNotificationEnabled()).thenReturn(true);
        when(dataspace.getTlsCertificateNotificationContacts()).thenReturn(List.of("a@example.org"));

        helper.sendDsTlsAcmeFailureNotification(HOSTNAME, "CA unreachable");

        verify(mailService).sendMailAsync(eq("a@example.org"), anyString(), anyString());
        verifyNoInteractions(notificationConfig);
    }
}
