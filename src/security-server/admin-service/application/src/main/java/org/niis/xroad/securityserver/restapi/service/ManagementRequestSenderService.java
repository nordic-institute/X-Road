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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;
import ee.ria.xroad.common.identifier.ClientId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.managementrequest.ManagementRequestSender;
import org.niis.xroad.common.managementrequest.model.MemberDidRegResponse;
import org.niis.xroad.common.rpc.VaultKeyProvider;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.securityserver.restapi.cache.CurrentSecurityServerId;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.niis.xroad.signer.client.SignerSignClient;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * management request service
 */
@Slf4j
@Service
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class ManagementRequestSenderService {
    private final SignerRpcClient signerRpcClient;
    private final SignerSignClient signerSignClient;
    private final GlobalConfProvider globalConfProvider;
    private final GlobalConfService globalConfService;
    private final CurrentSecurityServerId currentSecurityServerId;
    private final VaultKeyProvider vaultKeyProvider;
    private final AdminServiceProperties adminServiceProperties;

    private static final String MANAGEMENT_REQUEST_SENDING_FAILED_ERROR = "Sending management request failed";

    /**
     * Sends the authentication certificate registration request directly
     * to the central server. The request is sent as a signed mime multipart
     * message.
     * <p>
     * Request is sent for this securityserver (ManagementRequestSender
     * call's SecurityServerId = this security server's id)
     *
     * @param address  the IP address of the security server
     * @param authCert the authentication certificate bytes
     * @param dryRun if true, the request is only validated by the central server, not processed
     * @return request ID in the central server database (e.g. for audit logs if wanted)
     */
    public Integer sendAuthCertRegisterRequest(String address, byte[] authCert, boolean dryRun) throws GlobalConfOutdatedException {
        ManagementRequestSender sender = createManagementRequestSender();
        try {
            return sender.sendAuthCertRegRequest(currentSecurityServerId.getServerId(), address, authCert, dryRun);
        } catch (Exception e) {
            if (!dryRun) {
                log.error(MANAGEMENT_REQUEST_SENDING_FAILED_ERROR, e);
            }
            if (e instanceof XrdRuntimeException xrdRuntimeException) {
                throw xrdRuntimeException;
            }
            throw XrdRuntimeException.systemException(e);
        }
    }

    /**
     * Sends the SYSTEM DID registration request directly to the central server
     * as a signed mime multipart message, for this security server.
     *
     * @param systemDid  the SYSTEM DID of this security server
     * @param dspBaseUrl the DSP base URL of this security server
     * @param authCert   the authentication certificate bytes
     * @return {@code STORED} with the central server request id, the conflict or not resolved outcome with its reason,
     * or {@code SEND_FAILURE} with the cause
     */
    public SystemDidRegistrationSendOutcome sendSystemDidRegisterRequest(String systemDid, String dspBaseUrl, byte[] authCert)
            throws GlobalConfOutdatedException {
        ManagementRequestSender sender = createManagementRequestSender();
        try {
            Integer requestId = sender.sendSystemDidRegRequest(currentSecurityServerId.getServerId(), systemDid, dspBaseUrl,
                    authCert);
            if (requestId == null) {
                return SystemDidRegistrationSendOutcome.sendFailure(
                        new IllegalStateException("Response carries no request id"));
            }
            return SystemDidRegistrationSendOutcome.stored(requestId);
        } catch (XrdRuntimeException e) {
            return systemDidFaultOutcome(e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error(MANAGEMENT_REQUEST_SENDING_FAILED_ERROR, e);
            return SystemDidRegistrationSendOutcome.sendFailure(e);
        }
    }

    /**
     * Sends the member DID registration request for this security server through the proxy
     * to the management service, as an owner-signed X-Road message.
     *
     * @param memberDids the DID of each served member, in the order the results are expected
     * @return {@code ANSWERED} with the central server request id and one record per entry,
     * {@code REQUEST_TOO_LARGE} when the central server refused the whole batch as too large,
     * or {@code SEND_FAILURE} with the cause and, for a fault, its code
     */
    public MemberDidRegistrationSendOutcome sendMemberDidRegisterRequest(Map<ClientId.Conf, String> memberDids)
            throws GlobalConfOutdatedException {
        ManagementRequestSender sender = createManagementRequestSender();
        try {
            MemberDidRegResponse response = sender.sendMemberDidRegRequest(currentSecurityServerId.getServerId(), memberDids);
            if (response.requestId() == null) {
                return MemberDidRegistrationSendOutcome.sendFailure(
                        new IllegalStateException("Response carries no request id"));
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
        } catch (XrdRuntimeException e) {
            if (e.isCausedBy(ErrorCode.REQUEST_TOO_LARGE)) {
                log.warn("Central server refused a member DID registration request of {} entries as too large: {}",
                        memberDids.size(), e.getDetails());
                return MemberDidRegistrationSendOutcome.requestTooLarge();
            }
            log.error(MANAGEMENT_REQUEST_SENDING_FAILED_ERROR, e);
            return MemberDidRegistrationSendOutcome.sendFailure(e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error(MANAGEMENT_REQUEST_SENDING_FAILED_ERROR, e);
            return MemberDidRegistrationSendOutcome.sendFailure(e);
        }
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

    /**
     * Sends the authentication certificate deletion request as a normal
     * X-Road message.
     * <p>
     * Request is sent for this securityserver (ManagementRequestSender
     * call's SecurityServerId = this security server's id)
     *
     * @param authCert the authentication certificate bytes
     * @return request ID in the central server database (e.g. for audit logs if wanted)
     * @throws GlobalConfOutdatedException
     * @throws ManagementRequestSendingFailedException if there is a problem sending the message
     */
    public Integer sendAuthCertDeletionRequest(byte[] authCert) throws
            GlobalConfOutdatedException, ManagementRequestSendingFailedException {
        ManagementRequestSender sender = createManagementRequestSender();
        try {
            return sender.sendAuthCertDeletionRequest(currentSecurityServerId.getServerId(), authCert);
        } catch (Exception e) {
            log.error(MANAGEMENT_REQUEST_SENDING_FAILED_ERROR, e);
            throw new ManagementRequestSendingFailedException(e);
        }
    }

    /**
     * Sends the Security Server address change request as a normal X-Road message.
     * <p>
     * Request is sent for this securityserver (ManagementRequestSender
     * call's SecurityServerId = this security server's id)
     *
     * @param newAddress the new Security Server address
     * @return request ID in the central server database (e.g. for audit logs if wanted)
     * @throws GlobalConfOutdatedException
     * @throws ManagementRequestSendingFailedException if there is a problem sending the message
     */
    Integer sendAddressChangeRequest(String newAddress) throws GlobalConfOutdatedException, ManagementRequestSendingFailedException {
        ManagementRequestSender sender = createManagementRequestSender();
        try {
            return sender.sendAddressChangeRequest(currentSecurityServerId.getServerId(), newAddress);
        } catch (Exception e) {
            log.error(MANAGEMENT_REQUEST_SENDING_FAILED_ERROR, e);
            throw new ManagementRequestSendingFailedException(e);
        }
    }

    /**
     * Sends a client register request as a normal X-Road message
     *
     * @param clientId      the client id that will be registered
     * @param subsystemName subsystem name (in case of a subsystem)
     * @return request ID in the central server database
     * @throws GlobalConfOutdatedException
     * @throws ManagementRequestSendingFailedException if there is a problem sending the message
     */
    public Integer sendClientRegisterRequest(ClientId.Conf clientId, String subsystemName)
            throws GlobalConfOutdatedException, ManagementRequestSendingFailedException {
        ManagementRequestSender sender = createManagementRequestSender();
        try {
            return sender.sendClientRegRequest(currentSecurityServerId.getServerId(), clientId, subsystemName);
        } catch (XrdRuntimeException ce) {
            log.error(MANAGEMENT_REQUEST_SENDING_FAILED_ERROR, ce);
            throw ce;
        } catch (Exception e) {
            log.error(MANAGEMENT_REQUEST_SENDING_FAILED_ERROR, e);
            throw new ManagementRequestSendingFailedException(e);
        }
    }

    /**
     * Sends a client unregister request as a normal X-Road message
     *
     * @param clientId the client id that will be unregistered
     * @return request ID in the central server database
     * @throws GlobalConfOutdatedException
     * @throws ManagementRequestSendingFailedException if there is a problem sending the message
     */
    Integer sendClientUnregisterRequest(ClientId.Conf clientId)
            throws GlobalConfOutdatedException, ManagementRequestSendingFailedException {
        ManagementRequestSender sender = createManagementRequestSender();
        try {
            return sender.sendClientDeletionRequest(currentSecurityServerId.getServerId(), clientId);
        } catch (XrdRuntimeException ce) {
            log.error(MANAGEMENT_REQUEST_SENDING_FAILED_ERROR, ce);
            throw ce;
        } catch (Exception e) {
            log.error(MANAGEMENT_REQUEST_SENDING_FAILED_ERROR, e);
            throw new ManagementRequestSendingFailedException(e);
        }
    }

    /**
     * Sends an owner change request as a normal X-Road message
     *
     * @param clientId the client id that will be set as a new  owner
     * @return request ID in the central server database
     * @throws GlobalConfOutdatedException
     * @throws ManagementRequestSendingFailedException if there is a problem sending the message
     */
    public Integer sendOwnerChangeRequest(ClientId.Conf clientId)
            throws GlobalConfOutdatedException, ManagementRequestSendingFailedException {
        ManagementRequestSender sender = createManagementRequestSender();
        try {
            return sender.sendOwnerChangeRequest(currentSecurityServerId.getServerId(), clientId);
        } catch (XrdRuntimeException ce) {
            log.error(MANAGEMENT_REQUEST_SENDING_FAILED_ERROR, ce);
            throw ce;
        } catch (Exception e) {
            log.error(MANAGEMENT_REQUEST_SENDING_FAILED_ERROR, e);
            throw new ManagementRequestSendingFailedException(e);
        }
    }

    public Integer sendClientDisableRequest(ClientId.Conf clientId)
            throws GlobalConfOutdatedException, ManagementRequestSendingFailedException {
        ManagementRequestSender sender = createManagementRequestSender();
        try {
            return sender.sendClientDisableRequest(currentSecurityServerId.getServerId(), clientId);
        } catch (Exception e) {
            log.error(MANAGEMENT_REQUEST_SENDING_FAILED_ERROR, e);
            throw new ManagementRequestSendingFailedException(e);
        }
    }

    public Integer sendClientEnableRequest(ClientId.Conf clientId)
            throws GlobalConfOutdatedException, ManagementRequestSendingFailedException {
        ManagementRequestSender sender = createManagementRequestSender();
        try {
            return sender.sendClientEnableRequest(currentSecurityServerId.getServerId(), clientId);
        } catch (Exception e) {
            log.error(MANAGEMENT_REQUEST_SENDING_FAILED_ERROR, e);
            throw new ManagementRequestSendingFailedException(e);
        }
    }

    public Integer sendClientRenameRequest(ClientId.Conf clientId, String subsystemName)
            throws GlobalConfOutdatedException, ManagementRequestSendingFailedException {
        ManagementRequestSender sender = createManagementRequestSender();
        try {
            return sender.sendClientRenameRequest(currentSecurityServerId.getServerId(), clientId, subsystemName);
        } catch (Exception e) {
            log.error(MANAGEMENT_REQUEST_SENDING_FAILED_ERROR, e);
            throw new ManagementRequestSendingFailedException(e);
        }
    }

    ManagementRequestSender createManagementRequestSender()
            throws GlobalConfOutdatedException {
        globalConfService.verifyGlobalConfValidity();
        ClientId sender = currentSecurityServerId.getServerId().getOwner();
        ClientId receiver = globalConfProvider.getManagementRequestService();
        return new ManagementRequestSender(vaultKeyProvider, globalConfProvider, signerRpcClient,
                signerSignClient, sender, receiver, adminServiceProperties.getProxyServerUrl(),
                DigestAlgorithm.ofName(adminServiceProperties.getAuthCertRegSignatureDigestAlgorithmId()),
                adminServiceProperties.getProxyServerConnectTimeout(),
                adminServiceProperties.getProxyServerSocketTimeout(),
                adminServiceProperties.isProxyServerEnableConnectionReuse());
    }

    public Integer sendMaintenanceModeEnableRequest(String message) {
        ManagementRequestSender sender = createManagementRequestSender();
        try {
            return sender.sendMaintenanceModeEnableRequest(currentSecurityServerId.getServerId(), message);
        } catch (Exception e) {
            log.error(MANAGEMENT_REQUEST_SENDING_FAILED_ERROR, e);
            throw new ManagementRequestSendingFailedException(e);
        }
    }

    public Integer sendMaintenanceModeDisableRequest() {
        ManagementRequestSender sender = createManagementRequestSender();
        try {
            return sender.sendMaintenanceModeDisableRequest(currentSecurityServerId.getServerId());
        } catch (Exception e) {
            log.error(MANAGEMENT_REQUEST_SENDING_FAILED_ERROR, e);
            throw new ManagementRequestSendingFailedException(e);
        }
    }
}
