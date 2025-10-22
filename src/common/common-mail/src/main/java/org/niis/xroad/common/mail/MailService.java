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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Slf4j
@Service
@RequiredArgsConstructor
public class MailService {

    private final MailNotificationProperties mailNotificationProperties;
    private final NotificationConfig notificationConfig;
    private final JavaMailSender mailSender;

    public MailNotificationStatus getMailNotificationStatus() {
        Set<MailNotificationType> enabledMailNotifications = getEnabledMailNotifications();
        boolean configurationPresent = mailNotificationProperties.isMailNotificationConfigurationPresent();
        List<String> recipientsEmails = null;
        if (mailNotificationProperties.getContacts() != null) {
            recipientsEmails = mailNotificationProperties.getContacts()
                    .entrySet()
                    .stream()
                    .map(contact -> StringUtils.joinWith(": ", contact.getKey(), contact.getValue()))
                    .toList();
        }
        return new MailNotificationStatus(enabledMailNotifications, configurationPresent, recipientsEmails);
    }

    private Set<MailNotificationType> getEnabledMailNotifications() {
        Set<MailNotificationType> enabledNotifications = new HashSet<>();
        if (notificationConfig.isAcmeRenewalSuccessNotificationEnabled()) {
            enabledNotifications.add(MailNotificationType.ACME_SUCCESS);
        }
        if (notificationConfig.isAcmeRenewalFailureNotificationEnabled()) {
            enabledNotifications.add(MailNotificationType.ACME_FAILURE);
        }
        if (notificationConfig.isAuthCertRegisteredNotificationEnabled()) {
            enabledNotifications.add(MailNotificationType.AUTH_CERT_REGISTERED);
        }
        if (notificationConfig.isCertAutoActivationNotificationEnabled()) {
            enabledNotifications.add(MailNotificationType.ACME_CERT_AUTOMATICALLY_ACTIVATED);
        }
        if (notificationConfig.isCertAutoActivationFailureNotificationEnabled()) {
            enabledNotifications.add(MailNotificationType.ACME_CERT_AUTOMATIC_ACTIVATION_FAILURE);
        }
        return enabledNotifications;
    }

    /** Sends mail notification without stopping the flow in case of error */
    public void sendMailAsync(String to, String subject, String text) {
        if (!mailNotificationProperties.isMailNotificationConfigurationPresent()) {
            log.error("Attempted to send mail notification, but configuration is incomplete. Message wasn't sent!");
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        try (ExecutorService executorService = Executors.newSingleThreadExecutor()) {
            executorService.execute(() -> sendMail(message));
        }
    }

    private void sendMail(SimpleMailMessage message) {
        try {
            mailSender.send(message);
        } catch (Exception e) {
            log.error("Failed to send mail notification", e);
        }
    }

    /** NB! Meant only for test notifications!
     * For regular notification sending use {@link #sendMailAsync(String, String, String)} which is non-blocking */
    public void sendTestMail(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }

    public record MailNotificationStatus(Set<MailNotificationType> enabledNotifications,
                                         boolean configurationPresent,
                                         List<String> recipientsEmails) {
    }
}
