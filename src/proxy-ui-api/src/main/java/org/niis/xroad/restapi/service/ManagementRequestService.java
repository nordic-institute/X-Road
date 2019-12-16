/**
 * The MIT License
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
package org.niis.xroad.restapi.service;

import ee.ria.xroad.common.conf.serverconf.model.ServerConfType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.request.ManagementRequestSender;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.facade.GlobalConfFacade;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

/**
 * management request service
 */
@Slf4j
@Service
@PreAuthorize("isAuthenticated()")
public class ManagementRequestService {
    private final GlobalConfFacade globalConfFacade;
    private final GlobalConfService globalConfService;
    private final ServerConfService serverConfService;

    @Autowired
    public ManagementRequestService(GlobalConfFacade globalConfFacade, GlobalConfService globalConfService,
            ServerConfService serverConfService) {
        this.globalConfFacade = globalConfFacade;
        this.globalConfService = globalConfService;
        this.serverConfService = serverConfService;
    }

    /**
     * Sends the authentication certificate registration request directly
     * to the central server. The request is sent as a signed mime multipart
     * message.
     * @param securityServer the security server id whose certificate is to be
     * registered
     * @param address the IP address of the security server
     * @param authCert the authentication certificate bytes
     * @return request ID in the central server database (e.g. for audit logs if wanted)
     * @throws ServerConfService.MalformedServerConfException e.g. security server not initialized
     * @throws ManagementRequestException if an error occurs
     */
    public Integer sendAuthCertRegisterRequest(SecurityServerId securityServer, String address, byte[] authCert)
            throws ServerConfService.MalformedServerConfException, ManagementRequestException,
            GlobalConfService.GlobalConfOutdatedException {
        ManagementRequestSender sender = createManagementRequestSender();
        try {
            return sender.sendAuthCertRegRequest(securityServer, address, authCert);
        } catch (Exception e) {
            throw new ManagementRequestException(e);
        }
    }

    /**
     * Sends the authentication certificate deletion request as a normal
     * X-Road message.
     * @param securityServer the security server id whose certificate is to be
     * deleted
     * @param authCert the authentication certificate bytes
     * @return request ID in the central server database (e.g. for audit logs if wanted)
     * @throws ManagementRequestException if an error occurs
     */
    public Integer sendAuthCertDeletionRequest(SecurityServerId securityServer, byte[] authCert)
            throws ServerConfService.MalformedServerConfException, ManagementRequestException,
            GlobalConfService.GlobalConfOutdatedException {
        ManagementRequestSender sender = createManagementRequestSender();
        try {
            return sender.sendAuthCertDeletionRequest(securityServer, authCert);
        } catch (Exception e) {
            throw new ManagementRequestException(e);
        }
    }

    private ManagementRequestSender createManagementRequestSender()
            throws ServerConfService.MalformedServerConfException, GlobalConfService.GlobalConfOutdatedException {
        globalConfService.verifyGlobalConfValidity();
        ServerConfType serverConf = serverConfService.getServerConf();
        ClientId sender = serverConf.getOwner().getIdentifier();
        ClientId receiver = globalConfFacade.getManagementRequestService();
        return new ManagementRequestSender(sender, receiver);
    }

    /**
     * General exception for management requests
     */
    public static class ManagementRequestException extends ServiceException {
        public static final String ERROR_MANAGEMENT_REQUEST = "management_request_failed";

        public ManagementRequestException(Throwable t) {
            super(t, new ErrorDeviation(ERROR_MANAGEMENT_REQUEST));
        }
    }
}
