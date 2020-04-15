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

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.dto.InitializationStatusDto;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.exceptions.WarningDeviation;
import org.niis.xroad.restapi.facade.GlobalConfFacade;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * service for initializing the security server
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
public class InitializationService {
    public static final String WARNING_INIT_UNREGISTERED_MEMBER = "init_unregistered_member";
    public static final String METADATA_SERVERCONF_EXISTS = "Serverconf exists";
    public static final String METADATA_SOFTWARE_TOKEN_INITIALIZED = "Software token already initialized";

    private static final String OWNER_MEMBER_SEPARATOR = ":";

    private final SystemService systemService;
    private final ServerConfService serverConfService;
    private final TokenService tokenService;
    private final GlobalConfFacade globalConfFacade;
    private final IdentifierService identifierService;

    @Autowired
    public InitializationService(SystemService systemService, ServerConfService serverConfService,
            TokenService tokenService, GlobalConfFacade globalConfFacade,
            IdentifierService identifierService) {
        this.systemService = systemService;
        this.serverConfService = serverConfService;
        this.tokenService = tokenService;
        this.globalConfFacade = globalConfFacade;
        this.identifierService = identifierService;
    }

    /**
     * Check the whole init status of the Security Server. The init status consists of the following:
     * 1. is anchor imported - whether or not a configuration anchor has been imported
     * 2. is server conf initialized - whether or not a server conf exists
     * 3. is software token initialized - whether or not a software token exists AND
     * it's status != TokenStatusInfo.NOT_INITIALIZED
     * @return
     */
    public InitializationStatusDto isSecurityServerInitialized() {
        boolean isAnchorImported = systemService.isAnchorImported();
        boolean isServerConfInitialized = serverConfService.isServerConfInitialized();
        boolean isSoftwareTokenInitialized = tokenService.isSoftwareTokenInitialized();
        InitializationStatusDto initializationStatusDto = new InitializationStatusDto();
        initializationStatusDto.setAnchorImported(isAnchorImported);
        initializationStatusDto.setInitialized(isServerConfInitialized && isSoftwareTokenInitialized);
        return initializationStatusDto;
    }

    public void initialize(String securityServerCode, String ownerMemberClass, String ownerMemberCode,
            String softwareTokenPin, boolean ignoreWarnings) throws AnchorNotFoundException, InitializationException,
            UnhandledWarningsException {
        verifyInitializationPrerequisites();
        String instanceIdentifier = globalConfFacade.getInstanceIdentifier();
        ClientId ownerClientId = ClientId.create(instanceIdentifier, ownerMemberClass, ownerMemberCode);
        String ownerMemberName = globalConfFacade.getMemberName(ownerClientId);
        if (!ignoreWarnings && StringUtils.isEmpty(ownerMemberName)) {
            WarningDeviation warning = new WarningDeviation(WARNING_INIT_UNREGISTERED_MEMBER,
                    ownerMemberClass + OWNER_MEMBER_SEPARATOR + ownerMemberCode);
            throw new UnhandledWarningsException(warning);
        }
        // --- Start the init ---
        ServerConfType serverConfType = new ServerConfType();
        try {
            initNewOwner(serverConfType, ownerClientId);
        } catch (ServerConfOwnerExistsException e) {
            // This exception cannot happen since we just created the new ServerConfType
        }
    }

    /**
     * Initialize a new owner for the provided ServerConf
     * @param serverConfType
     * @param newOwnerClientId
     * @throws ServerConfOwnerExistsException if the provided <code>serverConfType</code> already has an owner
     */
    private void initNewOwner(ServerConfType serverConfType, ClientId newOwnerClientId) throws
            ServerConfOwnerExistsException {
        if (serverConfType.getOwner() != null) {
            throw new ServerConfOwnerExistsException("Cannot initialize a new owner for an existing ServerConf that " +
                    "already has an owner");
        }
        newOwnerClientId = identifierService.getOrPersistClientId(newOwnerClientId);
    }

    /**
     * Verify that the initialization process can proceed. This means verifying that an anchor has been imported,
     * server conf does not exists and a software token has not yet been initialized
     * @throws AnchorNotFoundException if anchor has not been imported
     * @throws InitializationException if server conf exists OR software token is already initialized
     */
    private void verifyInitializationPrerequisites() throws AnchorNotFoundException, InitializationException {
        if (!systemService.isAnchorImported()) {
            throw new AnchorNotFoundException("Configuration anchor was not found.");
        }
        boolean isServerConfInitialized = serverConfService.isServerConfInitialized();
        boolean isSoftwareTokenInitialized = tokenService.isSoftwareTokenInitialized();
        List<String> metadata = new ArrayList<>();
        if (isServerConfInitialized) {
            metadata.add(METADATA_SERVERCONF_EXISTS);
        }
        if (isSoftwareTokenInitialized) {
            metadata.add(METADATA_SOFTWARE_TOKEN_INITIALIZED);
        }
        if (!metadata.isEmpty()) {
            throw new InitializationException("Error initializing security server", metadata);
        }
    }

    /**
     * If something goes south with the initialization
     */
    public static class InitializationException extends ServiceException {
        public static final String INITIALIZATION_FAILED = "initialization_failed";

        public InitializationException(String msg, List<String> metadata) {
            super(msg, new ErrorDeviation(INITIALIZATION_FAILED, metadata));
        }
    }

    /**
     * If initializing a new owner when one already exists
     */
    public static class ServerConfOwnerExistsException extends ServiceException {
        public static final String ERROR_OWNER_EXISTS = "serverconf_owner_exists";

        public ServerConfOwnerExistsException(String msg) {
            super(msg, new ErrorDeviation(ERROR_OWNER_EXISTS));
        }
    }
}
