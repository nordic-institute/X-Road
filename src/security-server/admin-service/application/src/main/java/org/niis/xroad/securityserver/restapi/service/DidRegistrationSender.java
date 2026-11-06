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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.managementrequest.ManagementRequestSender;
import org.niis.xroad.common.managementrequest.model.MemberDidRegResponse;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.serverconf.impl.ownserver.OwnSecurityServerResolver;
import org.springframework.stereotype.Component;
import org.springframework.util.function.ThrowingBiFunction;

import java.util.Map;
import java.util.function.Function;

import static org.niis.xroad.securityserver.restapi.service.ManagementRequestSenderService.MANAGEMENT_REQUEST_SENDING_FAILED_ERROR;

/**
 * Sends the DID registration requests of this security server to the central server. Not guarded by
 * method security, so the scheduled publication job, which runs without an authentication, can call it.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DidRegistrationSender {
    private final ManagementRequestSenderFactory managementRequestSenderFactory;
    private final GlobalConfProvider globalConfProvider;
    private final OwnSecurityServerResolver ownSecurityServerResolver;

    /**
     * Sends the SYSTEM DID registration request directly to the central server
     * as a signed mime multipart message, for this security server.
     *
     * @param systemDid  the SYSTEM DID of this security server
     * @param dspBaseUrl the DSP base URL of this security server
     * @param authCert   the authentication certificate bytes
     * @return {@code STORED} with the central server request id, the conflict or not resolved outcome with its reason,
     * or {@code SEND_FAILURE} with the cause
     * @throws XrdRuntimeException if the global configuration is outdated or invalid or the owner is not set, before anything is sent
     */
    public SystemDidRegistrationSendOutcome sendSystemDidRegisterRequest(String systemDid, String dspBaseUrl, byte[] authCert) {
        return send((sender, serverId) -> {
            Integer requestId = sender.sendSystemDidRegRequest(serverId, systemDid, dspBaseUrl, authCert);
            if (requestId == null) {
                return missingRequestId(SystemDidRegistrationSendOutcome::sendFailure);
            }
            return SystemDidRegistrationSendOutcome.stored(requestId);
        }, DidRegistrationSender::systemDidFaultOutcome, SystemDidRegistrationSendOutcome::sendFailure);
    }

    /**
     * Sends the member DID registration request for this security server through the proxy
     * to the management service, as an owner-signed X-Road message.
     *
     * @param memberDids the DID of each served member, in the order the results are expected
     * @return {@code ANSWERED} with the central server request id and one record per entry,
     * {@code REQUEST_TOO_LARGE} when the central server refused the whole batch as too large,
     * or {@code SEND_FAILURE} with the cause and its code when the cause carries one
     * @throws XrdRuntimeException if the global configuration is outdated or invalid or the owner is not set, before anything is sent
     */
    public MemberDidRegistrationSendOutcome sendMemberDidRegisterRequest(Map<ClientId.Conf, String> memberDids) {
        return send((sender, serverId) -> {
            MemberDidRegResponse response = sender.sendMemberDidRegRequest(serverId, memberDids);
            if (response.requestId() == null) {
                return missingRequestId(MemberDidRegistrationSendOutcome::sendFailure);
            }
            var answered = response.results().stream().map(r -> r.member().asEncodedId()).toList();
            var sent = memberDids.keySet().stream().map(ClientId::asEncodedId).toList();
            if (!answered.equals(sent)) {
                return MemberDidRegistrationSendOutcome.sendFailure(
                        new IllegalStateException("Response results do not cover the sent members in order"));
            }
            var entries = response.results().stream()
                    .map(r -> new MemberDidRegistrationSendOutcome.Entry(r.member(), r.status(), r.reason()))
                    .toList();
            return MemberDidRegistrationSendOutcome.answered(response.requestId(), entries);
        }, fault -> memberDidFaultOutcome(fault, memberDids.size()), MemberDidRegistrationSendOutcome::sendFailure);
    }

    private <T> T send(ThrowingBiFunction<ManagementRequestSender, SecurityServerId.Conf, T> call,
                       Function<XrdRuntimeException, T> faultOutcome, Function<Throwable, T> failure) {
        globalConfProvider.verifyValidity();
        var serverId = ownSecurityServerResolver.identity().requireServerId();
        ManagementRequestSender sender = managementRequestSenderFactory.createFor(serverId.getOwner());
        try {
            return call.applyWithException(sender, serverId);
        } catch (XrdRuntimeException e) {
            return faultOutcome.apply(e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error(MANAGEMENT_REQUEST_SENDING_FAILED_ERROR, e);
            return failure.apply(e);
        }
    }

    private <T> T missingRequestId(Function<Throwable, T> failure) {
        var missingId = new IllegalStateException("Response carries no request id");
        log.error(MANAGEMENT_REQUEST_SENDING_FAILED_ERROR, missingId);
        return failure.apply(missingId);
    }

    private static MemberDidRegistrationSendOutcome memberDidFaultOutcome(XrdRuntimeException fault, int entryCount) {
        if (fault.isCausedBy(ErrorCode.REQUEST_TOO_LARGE)) {
            log.warn("Central server refused a member DID registration request of {} entries as too large: {}",
                    entryCount, fault.getDetails());
            return MemberDidRegistrationSendOutcome.requestTooLarge();
        }
        log.error(MANAGEMENT_REQUEST_SENDING_FAILED_ERROR, fault);
        return MemberDidRegistrationSendOutcome.sendFailure(fault);
    }

    private static String reasonOf(XrdRuntimeException fault, ErrorCode outcomeClass) {
        var details = fault.getDetails();
        return details == null || details.isBlank() ? outcomeClass.code() : details;
    }

    private static SystemDidRegistrationSendOutcome systemDidFaultOutcome(XrdRuntimeException fault) {
        if (fault.isCausedBy(ErrorCode.SYSTEM_DID_FINAL_CONFLICT)) {
            return SystemDidRegistrationSendOutcome.finalConflict(reasonOf(fault, ErrorCode.SYSTEM_DID_FINAL_CONFLICT));
        }
        if (fault.isCausedBy(ErrorCode.SYSTEM_DID_FIXABLE_CONFLICT)) {
            return SystemDidRegistrationSendOutcome.fixableConflict(reasonOf(fault, ErrorCode.SYSTEM_DID_FIXABLE_CONFLICT));
        }
        if (fault.isCausedBy(ErrorCode.SYSTEM_DID_NOT_RESOLVED)) {
            return SystemDidRegistrationSendOutcome.notResolved(reasonOf(fault, ErrorCode.SYSTEM_DID_NOT_RESOLVED));
        }
        log.error(MANAGEMENT_REQUEST_SENDING_FAILED_ERROR, fault);
        return SystemDidRegistrationSendOutcome.sendFailure(fault);
    }
}
