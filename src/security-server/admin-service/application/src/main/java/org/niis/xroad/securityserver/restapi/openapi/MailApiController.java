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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.mail.MailService;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.niis.xroad.securityserver.restapi.cache.CurrentSecurityServerId;
import org.niis.xroad.securityserver.restapi.openapi.model.MailNotificationStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.MailRecipientDto;
import org.niis.xroad.securityserver.restapi.openapi.model.MailStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.TestMailResponseDto;
import org.niis.xroad.securityserver.restapi.util.MailNotificationHelper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mail.MailException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * mail api controller
 */
@Controller
@RequestMapping(ControllerUtil.API_V1_PREFIX)
@Slf4j
@PreAuthorize("denyAll")
@RequiredArgsConstructor
public class MailApiController implements MailApi {

    private final CurrentSecurityServerId currentSecurityServerId;
    private final MailService mailService;
    private final MailNotificationHelper mailNotificationHelper;

    @Override
    @PreAuthorize("hasAuthority('DIAGNOSTICS')")
    public ResponseEntity<MailNotificationStatusDto> getMailNotificationStatus() {
        MailService.MailNotificationStatus mailNotificationStatus = mailService.getMailNotificationStatus();
        MailNotificationStatusDto mailNotificationStatusDto = new MailNotificationStatusDto();
        mailNotificationStatusDto.acmeSuccessStatus(mailNotificationStatus.acmeSuccessStatus());
        mailNotificationStatusDto.acmeFailureStatus(mailNotificationStatus.acmeFailureStatus());
        mailNotificationStatusDto.authCertRegisteredStatus(mailNotificationStatus.authCertRegisteredStatus());
        mailNotificationStatusDto.configurationPresent(mailNotificationStatus.configurationPresent());
        if (mailNotificationStatus.recipientsEmails() != null) {
            mailNotificationStatusDto.recipientsEmails(mailNotificationStatus.recipientsEmails());
        }
        return new ResponseEntity<>(mailNotificationStatusDto, HttpStatus.OK);
    }

    @Override
    @PreAuthorize("hasAuthority('DIAGNOSTICS')")
    public ResponseEntity<TestMailResponseDto> sendTestMail(MailRecipientDto mailRecipientDto) {
        try {
            mailNotificationHelper.sendTestMail(mailRecipientDto.getMailAddress(), currentSecurityServerId.getServerId().asEncodedId());
        } catch (MailException e) {
            log.error("Failed to send test mail", e);
            return new ResponseEntity<>(new TestMailResponseDto(MailStatusDto.ERROR, "Error: " + e.getMessage()), HttpStatus.OK);
        }
        return new ResponseEntity<>(new TestMailResponseDto(MailStatusDto.SUCCESS, "Success!"), HttpStatus.OK);
    }

}
