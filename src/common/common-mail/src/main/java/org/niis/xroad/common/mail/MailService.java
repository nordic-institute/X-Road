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

import ee.ria.xroad.common.SystemProperties;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MailService {

    private final MailNotificationProperties mailNotificationProperties;
    private final JavaMailSender mailSender;

    public MailNotificationStatus getMailNotificationStatus() {
        boolean successStatus = SystemProperties.getAcmeRenewalSuccessNotificationEnabled();
        boolean failureStatus = SystemProperties.getAcmeRenewalFailureNotificationEnabled();
        boolean configurationPresent = mailNotificationProperties.isMailNotificationConfigurationPresent();
        List<String> recipientsEmails = null;
        if (mailNotificationProperties.getContacts() != null) {
            recipientsEmails = mailNotificationProperties.getContacts()
                    .entrySet()
                    .stream()
                    .map(contact -> StringUtils.joinWith(": ", contact.getKey(), contact.getValue()))
                    .toList();
        }
        return new MailNotificationStatus(successStatus, failureStatus, configurationPresent, recipientsEmails);
    }

    public void sendMail(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }

    public record MailNotificationStatus(boolean successStatus,
                                         boolean failureStatus,
                                         boolean configurationPresent,
                                         List<String> recipientsEmails) {
    }
}
