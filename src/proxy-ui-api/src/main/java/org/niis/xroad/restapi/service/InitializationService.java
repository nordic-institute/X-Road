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

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.serverconf.IsAuthentication;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.conf.serverconf.model.ServerConfType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.util.TokenPinPolicy;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.dto.InitializationStatusDto;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.exceptions.WarningDeviation;
import org.niis.xroad.restapi.facade.GlobalConfFacade;
import org.niis.xroad.restapi.facade.SignerProxyFacade;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * service for initializing the security server
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
public class InitializationService {
    public static final String WARNING_INIT_UNREGISTERED_MEMBER = "init_unregistered_member";
    public static final String WARNING_INIT_SERVER_ID_EXISTS = "init_server_id_exists";
    public static final String METADATA_SERVERCONF_EXISTS = "init_serverconf_exists";
    public static final String METADATA_SOFTWARE_TOKEN_INITIALIZED = "init_software_token_initialized";
    public static final String METADATA_PIN_MIN_LENGTH = "pin_min_length";
    public static final String METADATA_PIN_MIN_CHAR_CLASSES = "pin_min_char_classes_count";

    private final SystemService systemService;
    private final ServerConfService serverConfService;
    private final TokenService tokenService;
    private final GlobalConfFacade globalConfFacade;
    private final IdentifierService identifierService;
    private final ClientService clientService;
    private final SignerProxyFacade signerProxyFacade;

    @Setter
    private boolean isTokenPinEnforced = SystemProperties.shouldEnforceTokenPinPolicy();

    @Autowired
    public InitializationService(SystemService systemService, ServerConfService serverConfService,
            TokenService tokenService, GlobalConfFacade globalConfFacade,
            IdentifierService identifierService, ClientService clientService,
            SignerProxyFacade signerProxyFacade) {
        this.systemService = systemService;
        this.serverConfService = serverConfService;
        this.tokenService = tokenService;
        this.globalConfFacade = globalConfFacade;
        this.identifierService = identifierService;
        this.clientService = clientService;
        this.signerProxyFacade = signerProxyFacade;
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

    /**
     * Initialize a new Security Server with the provided parameters
     * @param securityServerCode server code for the new Security Server
     * @param ownerMemberClass member class of the new owner member
     * @param ownerMemberCode member code of the new owner member
     * @param softwareTokenPin pin code for the initial software token (softToken-0)
     * @param ignoreWarnings
     * @throws AnchorNotFoundException if an anchor has not been imported
     * @throws InitializationException if prerequisite check fails: if a server conf already exists OR if a
     * software token has already been initialized
     * @throws UnhandledWarningsException
     * @throws WeakPinException if the pin does not meet the length and complexity requirements (if token pin policy is
     * enforced by properties)
     * @throws InvalidPinException if the provided pin code does not follow the TokenPinPolicy (if token pin policy is
     * enforced by properties)
     * @throws SoftwareTokenInitException if something goes wrong with the token init
     */
    public void initialize(String securityServerCode, String ownerMemberClass, String ownerMemberCode,
            String softwareTokenPin, boolean ignoreWarnings) throws AnchorNotFoundException, InitializationException,
            UnhandledWarningsException, WeakPinException, InvalidPinException, SoftwareTokenInitException {
        if (!systemService.isAnchorImported()) {
            throw new AnchorNotFoundException("Configuration anchor was not found.");
        }
        /*
          NOTE: verifyInitializationPrerequisites prevents the user from initing the Security Server for a second time.
          This could be moved into the checkForWarnings method in the future
         */
        verifyInitializationPrerequisites();
        String instanceIdentifier = globalConfFacade.getInstanceIdentifier();
        // get id from db if exists - this is for partial init support since no client ids should yet exist
        ClientId ownerClientId = clientService.getPossiblyManagedEntity(ClientId.create(instanceIdentifier,
                ownerMemberClass, ownerMemberCode));
        if (!ignoreWarnings) {
            checkForWarnings(ownerClientId, securityServerCode);
        }
        // --- Start the init ---
        ServerConfType serverConf = createInitialServerConf(ownerClientId, securityServerCode);
        initializeSoftwareToken(softwareTokenPin);
        serverConfService.saveOrUpdate(serverConf);
    }

    /**
     * Helper to create a software token
     * @param softwareTokenPin the pin of the token
     * @throws InvalidPinException
     * @throws WeakPinException
     * @throws SoftwareTokenInitException
     */
    private void initializeSoftwareToken(String softwareTokenPin) throws InvalidPinException, WeakPinException,
            SoftwareTokenInitException {
        char[] pin = softwareTokenPin.toCharArray();
        if (isTokenPinEnforced) {
            TokenPinPolicy.Description description = TokenPinPolicy.describe(pin);
            if (!description.isValid()) {
                throw new InvalidPinException("The provided pin code does not match with the pin code policy");
            }
            List<String> metadata = new ArrayList<>();
            metadata.add(METADATA_PIN_MIN_LENGTH);
            metadata.add(String.valueOf(TokenPinPolicy.MIN_PASSWORD_LENGTH));
            metadata.add(METADATA_PIN_MIN_CHAR_CLASSES);
            metadata.add(String.valueOf(TokenPinPolicy.MIN_CHARACTER_CLASS_COUNT));
            throw new WeakPinException("The provided pin code was too weak", metadata);
        }
        try {
            signerProxyFacade.initSoftwareToken(pin);
        } catch (Exception e) {
            // not good
            throw new SoftwareTokenInitException("Error initializing software token", e);
        }
    }

    /**
     * Helper to create the initial server conf
     * @param ownerClientId
     * @param securityServerCode
     * @return ServerConfType
     */
    private ServerConfType createInitialServerConf(ClientId ownerClientId, String securityServerCode) {
        /* get serverconf from db if exists
           - this is for partial init support since no server confs should yet exist
         */
        ServerConfType serverConf = serverConfService.getOrCreateServerConf();
        ClientType ownerClient = null;
        // get client from db if exists - this is for partial init support since no clients should yet exist
        Optional<ClientType> foundClient = serverConf.getClient().stream()
                .filter(clientType -> clientType.getIdentifier().equals(ownerClientId))
                .findFirst();
        ownerClient = foundClient.orElse(getInitialClient(ownerClientId));
        ownerClient.setConf(serverConf);
        // again for partial init support: if the client already exists there is no reason to add it again
        if (!serverConf.getClient().contains(ownerClient)) {
            serverConf.getClient().add(ownerClient);
        }
        serverConf.setOwner(ownerClient);
        serverConf.setServerCode(securityServerCode);
        return serverConf;
    }

    /**
     * Helper to check for warnings
     * @param ownerClientId
     * @param securityServerCode
     * @throws UnhandledWarningsException
     */
    private void checkForWarnings(ClientId ownerClientId, String securityServerCode)
            throws UnhandledWarningsException {
        String ownerMemberName = globalConfFacade.getMemberName(ownerClientId);
        SecurityServerId serverId = SecurityServerId.create(ownerClientId, securityServerCode);
        List<WarningDeviation> warnings = new ArrayList<>();
        if (StringUtils.isEmpty(ownerMemberName)) {
            WarningDeviation memberWarning = new WarningDeviation(WARNING_INIT_UNREGISTERED_MEMBER,
                    ownerClientId.toShortString());
            warnings.add(memberWarning);
        }
        if (globalConfFacade.existsSecurityServer(serverId)) {
            WarningDeviation memberWarning = new WarningDeviation(WARNING_INIT_SERVER_ID_EXISTS,
                    serverId.toShortString());
            warnings.add(memberWarning);
        }
        if (!warnings.isEmpty()) {
            throw new UnhandledWarningsException(warnings);
        }
    }

    /**
     * Helper to create an initial client
     * @param clientId
     * @return
     */
    private ClientType getInitialClient(ClientId clientId) {
        ClientType localClient = clientService.getLocalClient(clientId);
        if (localClient == null) {
            localClient = new ClientType();
            localClient.setIdentifier(clientId);
            localClient.setClientStatus(ClientType.STATUS_SAVED);
            localClient.setIsAuthentication(IsAuthentication.SSLAUTH.name());
        }
        return localClient;
    }

    /**
     * Verify that the initialization process can proceed and that the security server has not already been
     * initialized. This means verifying that an anchor has been imported,
     * server conf does not exist and a software token has not yet been initialized
     * @throws InitializationException if server conf exists OR software token is already initialized
     */
    private void verifyInitializationPrerequisites() throws InitializationException {
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
     * If the provided pin code is not valid
     */
    public static class InvalidPinException extends ServiceException {
        public static final String INVALID_PIN = "invalid_pin";

        public InvalidPinException(String msg) {
            super(msg, new ErrorDeviation(INVALID_PIN));
        }
    }

    /**
     * If the provided pin code is too weak
     */
    public static class WeakPinException extends ServiceException {
        public static final String WEAK_PIN = "weak_pin";

        public WeakPinException(String msg, List<String> metadata) {
            super(msg, new ErrorDeviation(WEAK_PIN, metadata));
        }
    }

    /**
     * If the software token init fails
     */
    public static class SoftwareTokenInitException extends ServiceException {
        public static final String SOFTWARE_TOKEN_INIT_FAILED = "software_token_init_failed";

        public SoftwareTokenInitException(String msg, Throwable t) {
            super(msg, t, new ErrorDeviation(SOFTWARE_TOKEN_INIT_FAILED));
        }
    }
}
