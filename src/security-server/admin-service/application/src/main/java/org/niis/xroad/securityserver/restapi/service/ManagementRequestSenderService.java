/**
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

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.identifier.ClientId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.managementrequest.ManagementRequestSender;
import org.niis.xroad.securityserver.restapi.cache.CurrentSecurityServerId;
import org.niis.xroad.securityserver.restapi.facade.GlobalConfFacade;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * management request service
 */
@Slf4j
@Service
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class ManagementRequestSenderService {

    private final GlobalConfFacade globalConfFacade;
    private final GlobalConfService globalConfService;
    private final CurrentSecurityServerId currentSecurityServerId;

    private static final String MANAGEMENT_REQUEST_SENDING_FAILED_ERROR = "Sending management request failed";

    /**
     * Sends the authentication certificate registration request directly
     * to the central server. The request is sent as a signed mime multipart
     * message.
     *
     * Request is sent for this securityserver (ManagementRequestSender
     * call's SecurityServerId = this security server's id)
     * @param address the IP address of the security server
     * @param authCert the authentication certificate bytes
     * @return request ID in the central server database (e.g. for audit logs if wanted)
     * @throws GlobalConfOutdatedException
     */
    Integer sendAuthCertRegisterRequest(String address, byte[] authCert)
            throws GlobalConfOutdatedException {
        ManagementRequestSender sender = createManagementRequestSender();
        try {
            return sender.sendAuthCertRegRequest(currentSecurityServerId.getServerId(), address, authCert);
        } catch (Exception e) {
            log.error(MANAGEMENT_REQUEST_SENDING_FAILED_ERROR, e);
            if (e instanceof CodedException) {
                throw (CodedException) e;
            }
            throw new RuntimeException(e);
        }
    }

    /**
     * Sends the authentication certificate deletion request as a normal
     * X-Road message.
     *
     * Request is sent for this securityserver (ManagementRequestSender
     * call's SecurityServerId = this security server's id)
     * @param authCert the authentication certificate bytes
     * @return request ID in the central server database (e.g. for audit logs if wanted)
     * @throws GlobalConfOutdatedException
     * @throws ManagementRequestSendingFailedException if there is a problem sending the message
     */
    Integer sendAuthCertDeletionRequest(byte[] authCert) throws
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
     * Sends a client register request as a normal X-Road message
     * @param clientId the client id that will be registered
     * @return request ID in the central server database
     * @throws GlobalConfOutdatedException
     * @throws ManagementRequestSendingFailedException if there is a problem sending the message
     */
    public Integer sendClientRegisterRequest(ClientId.Conf clientId)
            throws GlobalConfOutdatedException, ManagementRequestSendingFailedException {
        ManagementRequestSender sender = createManagementRequestSender();
        try {
            return sender.sendClientRegRequest(currentSecurityServerId.getServerId(), clientId);
        } catch (CodedException ce) {
            log.error(MANAGEMENT_REQUEST_SENDING_FAILED_ERROR, ce);
            throw ce;
        } catch (Exception e) {
            log.error(MANAGEMENT_REQUEST_SENDING_FAILED_ERROR, e);
            throw new ManagementRequestSendingFailedException(e);
        }
    }

    /**
     * Sends a client unregister request as a normal X-Road message
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
        } catch (CodedException ce) {
            log.error(MANAGEMENT_REQUEST_SENDING_FAILED_ERROR, ce);
            throw ce;
        } catch (Exception e) {
            log.error(MANAGEMENT_REQUEST_SENDING_FAILED_ERROR, e);
            throw new ManagementRequestSendingFailedException(e);
        }
    }

    /**
     * Sends an owner change request request as a normal X-Road message
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
        } catch (CodedException ce) {
            log.error(MANAGEMENT_REQUEST_SENDING_FAILED_ERROR, ce);
            throw ce;
        } catch (Exception e) {
            log.error(MANAGEMENT_REQUEST_SENDING_FAILED_ERROR, e);
            throw new ManagementRequestSendingFailedException(e);
        }
    }

    private ManagementRequestSender createManagementRequestSender()
            throws GlobalConfOutdatedException {
        globalConfService.verifyGlobalConfValidity();
        ClientId sender = currentSecurityServerId.getServerId().getOwner();
        ClientId receiver = globalConfFacade.getManagementRequestService();
        return new ManagementRequestSender(sender, receiver);
    }
}
