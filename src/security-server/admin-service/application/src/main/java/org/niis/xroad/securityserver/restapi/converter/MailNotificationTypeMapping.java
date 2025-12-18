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
package org.niis.xroad.securityserver.restapi.converter;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.securityserver.restapi.mail.MailNotificationType;
import org.niis.xroad.securityserver.restapi.openapi.model.MailNotificationTypeDto;

import java.util.Arrays;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@RequiredArgsConstructor
public enum MailNotificationTypeMapping {

    ACME_FAILURE(MailNotificationType.ACME_FAILURE, MailNotificationTypeDto.ACME_FAILURE),
    ACME_SUCCESS(MailNotificationType.ACME_SUCCESS, MailNotificationTypeDto.ACME_SUCCESS),
    AUTH_CERT_REGISTERED(MailNotificationType.AUTH_CERT_REGISTERED, MailNotificationTypeDto.AUTH_CERT_REGISTERED),
    ACME_CERT_AUTOMATICALLY_ACTIVATED(MailNotificationType.ACME_CERT_AUTOMATICALLY_ACTIVATED,
            MailNotificationTypeDto.ACME_CERT_AUTOMATICALLY_ACTIVATED),
    ACME_CERT_AUTOMATIC_ACTIVATION_FAILURE(MailNotificationType.ACME_CERT_AUTOMATIC_ACTIVATION_FAILURE,
            MailNotificationTypeDto.ACME_CERT_AUTOMATIC_ACTIVATION_FAILURE);

    private final MailNotificationType mailNotificationType;
    private final MailNotificationTypeDto mailNotificationTypeDto;

    public static Optional<MailNotificationTypeDto> map(MailNotificationType mailNotificationType) {
        return getFor(mailNotificationType).map(MailNotificationTypeMapping::getMailNotificationTypeDto);
    }

    public static Set<MailNotificationTypeDto> map(Set<MailNotificationType> mailNotificationTypes) {
        return mailNotificationTypes.stream()
                .map(MailNotificationTypeMapping::map)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toSet());
    }

    public static Optional<MailNotificationTypeMapping> getFor(MailNotificationType mailNotificationType) {
        return Arrays.stream(values())
                .filter(mapping -> mapping.mailNotificationType.equals(mailNotificationType))
                .findFirst();
    }

}
