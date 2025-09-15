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

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.mail.MailNotificationProperties;
import org.niis.xroad.common.mail.MailService;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Component;

import java.util.Optional;

import static ee.ria.xroad.common.util.CertUtils.isSigningCert;
import static ee.ria.xroad.common.util.CryptoUtils.readCertificate;

@RequiredArgsConstructor
@Component
public class MailNotificationHelper {

    private final MailNotificationProperties mailNotificationProperties;
    private final MessageSourceAccessor notificationMessageSourceAccessor;
    private final MailService mailService;
    private final AdminServiceProperties adminServiceProperties;

    public void sendSuccessNotification(ClientId memberId,
                                        SecurityServerId.Conf securityServerId,
                                        CertificateInfo newCertInfo,
                                        KeyUsageInfo keyUsageInfo) {
        if (SystemProperties.getAcmeRenewalSuccessNotificationEnabled()) {
            String authTitle =
                    notificationMessageSourceAccessor.getMessage("acme_auth_cert_renewal_success_title");
            String signTitle =
                    notificationMessageSourceAccessor.getMessage("acme_sign_cert_renewal_success_title");
            String title = KeyUsageInfo.AUTHENTICATION.equals(keyUsageInfo) ? authTitle : signTitle;
            String authCertContent =
                    notificationMessageSourceAccessor.getMessage("acme_auth_cert_renewal_success_content",
                            new String[]{securityServerId.asEncodedId(), newCertInfo.getCertificateDisplayName()});
            if (!adminServiceProperties.isAutomaticActivateAuthCertificate()) {
                authCertContent += " " + notificationMessageSourceAccessor.getMessage("acme_auth_cert_renewal_success_content_activate");
            }
            String signCertContent =
                    notificationMessageSourceAccessor.getMessage("acme_sign_cert_renewal_success_content",
                            new String[]{newCertInfo.getCertificateDisplayName(), memberId.asEncodedId(), securityServerId.asEncodedId()});
            if (!SystemProperties.getAutomaticActivateAcmeSignCertificate()) {
                signCertContent += " " + notificationMessageSourceAccessor.getMessage("acme_sign_cert_renewal_success_content_activate");
            }
            String content = KeyUsageInfo.AUTHENTICATION.equals(keyUsageInfo) ? authCertContent : signCertContent;
            Optional.ofNullable(mailNotificationProperties.getContacts())
                    .map(contacts -> contacts.get(memberId.asEncodedId()))
                    .ifPresent(address -> mailService.sendMailAsync(address, title, content));
        }
    }

    public void sendFailureNotification(String memberId,
                                        CertificateInfo certInfo,
                                        SecurityServerId.Conf securityServerId,
                                        String errorDescription) {
        if (SystemProperties.getAcmeRenewalFailureNotificationEnabled()) {
            boolean isSignCert = isSigningCert(readCertificate(certInfo.getCertificateBytes()));
            String authCertTitle =
                    notificationMessageSourceAccessor.getMessage("acme_auth_cert_renewal_failure_title",
                            new String[]{certInfo.getCertificateDisplayName()});
            String signCertTitle =
                    notificationMessageSourceAccessor.getMessage("acme_sign_cert_renewal_failure_title",
                            new String[]{certInfo.getCertificateDisplayName()});
            String title = isSignCert ? signCertTitle : authCertTitle;
            String authCertContent =
                    notificationMessageSourceAccessor.getMessage("acme_auth_cert_renewal_failure_content",
                            new String[]{certInfo.getCertificateDisplayName(), securityServerId.asEncodedId(), errorDescription});
            String signCertContent =
                    notificationMessageSourceAccessor.getMessage("acme_sign_cert_renewal_failure_content",
                            new String[]{certInfo.getCertificateDisplayName(), memberId, securityServerId.asEncodedId(), errorDescription});
            String content = isSignCert ? signCertContent : authCertContent;
            Optional.ofNullable(mailNotificationProperties.getContacts())
                    .map(contacts -> contacts.get(memberId))
                    .ifPresent(address -> mailService.sendMailAsync(address, title, content));
        }
    }

    public void sendAuthCertRegisteredNotification(SecurityServerId securityServerId, CertificateInfo certInfo) {
        if (SystemProperties.getAuthCertRegisteredNotificationEnabled()) {
            String title =
                    notificationMessageSourceAccessor.getMessage("auth_cert_registration_success_title");
            String baseContent =
                    notificationMessageSourceAccessor.getMessage("auth_cert_registration_success_content",
                            new String[]{certInfo.getCertificateDisplayName(), securityServerId.getServerCode()});
            String contentWhitManualActivation =
                    baseContent + " " + notificationMessageSourceAccessor.getMessage("auth_cert_registration_success_content_activate");
            String content = adminServiceProperties.isAutomaticActivateAuthCertificate() ? baseContent : contentWhitManualActivation;
            Optional.ofNullable(mailNotificationProperties.getContacts())
                    .map(contacts -> contacts.get(securityServerId.getOwner().asEncodedId()))
                    .ifPresent(address -> mailService.sendMailAsync(address, title, content));
        }
    }

    public void sendCertActivatedNotification(String memberId,
                                              SecurityServerId securityServerId,
                                              CertificateInfo certInfo,
                                              KeyUsageInfo keyUsageInfo) {
        if (SystemProperties.getAcmeCertAutomaticallyActivatedNotificationEnabled()) {
            String authCertTitle =
                    notificationMessageSourceAccessor.getMessage("auth_cert_automatic_activation_title");
            String signCertTitle =
                    notificationMessageSourceAccessor.getMessage("sign_cert_automatic_activation_title");
            String title = KeyUsageInfo.AUTHENTICATION.equals(keyUsageInfo) ? authCertTitle : signCertTitle;
            String authCertContent =
                    notificationMessageSourceAccessor.getMessage("auth_cert_automatic_activation_content",
                            new String[]{certInfo.getCertificateDisplayName(), securityServerId.getServerCode()});
            String signCertContent =
                    notificationMessageSourceAccessor.getMessage("sign_cert_automatic_activation_content",
                            new String[]{certInfo.getCertificateDisplayName(), securityServerId.getServerCode()});
            String content = KeyUsageInfo.AUTHENTICATION.equals(keyUsageInfo) ? authCertContent : signCertContent;
            Optional.ofNullable(mailNotificationProperties.getContacts())
                    .map(contacts -> contacts.get(memberId))
                    .ifPresent(address -> mailService.sendMailAsync(address, title, content));
        }
    }


    public void sendCertActivationFailureNotification(String memberId,
                                                      String certDisplayName,
                                                      SecurityServerId.Conf securityServerId,
                                                      KeyUsageInfo keyUsageInfo,
                                                      String errorDescription) {
        if (SystemProperties.getAcmeCertAutomaticActivationFailureNotificationEnabled()) {
            boolean isSignCert = keyUsageInfo == KeyUsageInfo.SIGNING;
            String authCertTitle =
                    notificationMessageSourceAccessor.getMessage("auth_cert_automatic_activation_failure_title");
            String signCertTitle =
                    notificationMessageSourceAccessor.getMessage("sign_cert_automatic_activation_failure_title");
            String title = isSignCert ? signCertTitle : authCertTitle;
            String authCertContent =
                    notificationMessageSourceAccessor.getMessage("auth_cert_automatic_activation_failure_content",
                            new String[]{certDisplayName, securityServerId.asEncodedId(), errorDescription});
            String signCertContent =
                    notificationMessageSourceAccessor.getMessage("sign_cert_automatic_activation_failure_content",
                            new String[]{certDisplayName, memberId, securityServerId.asEncodedId(), errorDescription});
            String content = isSignCert ? signCertContent : authCertContent;
            Optional.ofNullable(mailNotificationProperties.getContacts())
                    .map(contacts -> contacts.get(memberId))
                    .ifPresent(address -> mailService.sendMailAsync(address, title, content));
        }
    }

    public void sendTestMail(String recipientAddress, String securityServerId) {
        mailService.sendTestMail(recipientAddress,
                notificationMessageSourceAccessor.getMessage("test_mail_title", new String[]{securityServerId}),
                notificationMessageSourceAccessor.getMessage("test_mail_content", new String[]{securityServerId}));
    }

}
