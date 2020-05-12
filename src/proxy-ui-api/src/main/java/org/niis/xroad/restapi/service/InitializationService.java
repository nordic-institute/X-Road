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
    public static final String WARNING_SERVERCODE_EXISTS = "init_serverconf_exists";
    public static final String WARNING_SERVER_OWNER_EXISTS = "init_server_owner_exists";
    public static final String WARNING_SOFTWARE_TOKEN_INITIALIZED = "init_software_token_initialized";
    public static final String METADATA_PIN_MIN_LENGTH = "pin_min_length";
    public static final String METADATA_PIN_MIN_CHAR_CLASSES = "pin_min_char_classes_count";

    public static final String ERROR_METADATA_SERVERCODE_BLANK = "server_code_blank";
    public static final String ERROR_METADATA_MEMBER_CLASS_BLANK = "member_class_blank";
    public static final String ERROR_METADATA_MEMBER_CODE_BLANK = "member_code_blank";
    public static final String ERROR_METADATA_PIN_BLANK = "pin_code_blank";

    private final SystemService systemService;
    private final ServerConfService serverConfService;
    private final TokenService tokenService;
    private final GlobalConfFacade globalConfFacade;
    private final ClientService clientService;
    private final SignerProxyFacade signerProxyFacade;

    @Setter
    private boolean isTokenPinEnforced = SystemProperties.shouldEnforceTokenPinPolicy();

    @Autowired
    public InitializationService(SystemService systemService, ServerConfService serverConfService,
            TokenService tokenService, GlobalConfFacade globalConfFacade, ClientService clientService,
            SignerProxyFacade signerProxyFacade) {
        this.systemService = systemService;
        this.serverConfService = serverConfService;
        this.tokenService = tokenService;
        this.globalConfFacade = globalConfFacade;
        this.clientService = clientService;
        this.signerProxyFacade = signerProxyFacade;
    }

    /**
     * Check the whole init status of the Security Server. The init status consists of the following:
     * 1. is anchor imported - whether or not a configuration anchor has been imported
     * 2. is server code initialized - whether or not a server code has been initialized
     * 3. is server owner initialized - whether or not a server owner has been initialized
     * 4. is software token initialized - whether or not a software token exists AND
     * it's status != TokenStatusInfo.NOT_INITIALIZED
     * @return
     */
    public InitializationStatusDto getSecurityServerInitializationStatus() {
        boolean isAnchorImported = systemService.isAnchorImported();
        boolean isServerCodeInitialized = serverConfService.isServerCodeInitialized();
        boolean isServerOwnerInitialized = serverConfService.isServerOwnerInitialized();
        boolean isSoftwareTokenInitialized = tokenService.isSoftwareTokenInitialized();
        InitializationStatusDto initializationStatusDto = new InitializationStatusDto();
        initializationStatusDto.setAnchorImported(isAnchorImported);
        initializationStatusDto.setServerCodeInitialized(isServerCodeInitialized);
        initializationStatusDto.setServerOwnerInitialized(isServerOwnerInitialized);
        initializationStatusDto.setSoftwareTokenInitialized(isSoftwareTokenInitialized);
        return initializationStatusDto;
    }

    /**
     * Initialize a new Security Server with the provided parameters
     * @param securityServerCode server code for the new Security Server
     * @param ownerMemberClass member class of the new owner member
     * @param ownerMemberCode member code of the new owner member
     * @param softwareTokenPin pin code for the initial software token (softToken-0)
     * @param ignoreWarnings whether to skip initialization warnings all in all
     * @throws AnchorNotFoundException if an anchor has not been imported
     * @throws UnhandledWarningsException if a server code already initialized
     * OR server owner already initialized
     * OR if a software token has already been initialized
     * OR trying to set unregistered member as an owner
     * OR provided server code already in use
     * @throws WeakPinException if the pin does not meet the length and complexity requirements (if token pin policy is
     * enforced by properties)
     * @throws InvalidPinException if the provided pin code does not follow the TokenPinPolicy (if token pin policy is
     * enforced by properties)
     * @throws SoftwareTokenInitException if something goes wrong with the token init
     * @throws MissingInitParamsException if empty or missing parameters are provided
     */
    public void initialize(String securityServerCode, String ownerMemberClass, String ownerMemberCode,
            String softwareTokenPin, boolean ignoreWarnings) throws AnchorNotFoundException, WeakPinException,
            UnhandledWarningsException, InvalidCharactersException, SoftwareTokenInitException,
            MissingInitParamsException {
        if (!systemService.isAnchorImported()) {
            throw new AnchorNotFoundException("Configuration anchor was not found.");
        }
        verifyInitializationPrerequisites(securityServerCode, ownerMemberClass, ownerMemberCode, softwareTokenPin);
        String instanceIdentifier = globalConfFacade.getInstanceIdentifier();
        ClientId ownerClientId = null;
        if (serverConfService.isServerOwnerInitialized()) {
            ownerClientId = serverConfService.getSecurityServerOwnerId();
        } else {
            // if owner does not exist, assert new owner parameters
            List<String> errorMetadata = new ArrayList<>();
            if (StringUtils.isEmpty(ownerMemberClass)) {
                errorMetadata.add(ERROR_METADATA_MEMBER_CLASS_BLANK);
            }
            if (StringUtils.isEmpty(ownerMemberCode)) {
                errorMetadata.add(ERROR_METADATA_MEMBER_CODE_BLANK);
            }
            if (!errorMetadata.isEmpty()) {
                throw new MissingInitParamsException("Empty or missing member parameters provided for initialization",
                        errorMetadata);
            }
            ownerClientId = ClientId.create(instanceIdentifier, ownerMemberClass, ownerMemberCode);
        }
        if (!ignoreWarnings) {
            checkForWarnings(ownerClientId, securityServerCode);
        }
        // --- Start the init ---
        ServerConfType serverConf = createInitialServerConf(ownerClientId, securityServerCode);
        if (!tokenService.isSoftwareTokenInitialized()) {
            initializeSoftwareToken(softwareTokenPin);
        }
        serverConfService.saveOrUpdate(serverConf);
    }

    /**
     * If old values do not exist -> new values must be provided
     * @param securityServerCode
     * @param ownerMemberClass
     * @param ownerMemberCode
     * @param softwareTokenPin
     * @throws MissingInitParamsException if null or empty init parameters provided
     */
    private void verifyInitializationPrerequisites(String securityServerCode, String ownerMemberClass,
            String ownerMemberCode, String softwareTokenPin) throws MissingInitParamsException {
        List<String> errorMetadata = new ArrayList<>();
        if (!serverConfService.isServerCodeInitialized()) {
            if (StringUtils.isEmpty(securityServerCode)) {
                errorMetadata.add(ERROR_METADATA_SERVERCODE_BLANK);
            }
        }
        if (!serverConfService.isServerOwnerInitialized()) {
            if (StringUtils.isEmpty(ownerMemberClass)) {
                errorMetadata.add(ERROR_METADATA_MEMBER_CLASS_BLANK);
            }
            if (StringUtils.isEmpty(ownerMemberCode)) {
                errorMetadata.add(ERROR_METADATA_MEMBER_CODE_BLANK);
            }
        }
        if (!tokenService.isSoftwareTokenInitialized()) {
            if (StringUtils.isEmpty(softwareTokenPin)) {
                errorMetadata.add(ERROR_METADATA_PIN_BLANK);
            }
        }
        if (!errorMetadata.isEmpty()) {
            throw new MissingInitParamsException("Empty or missing parameters provided for initialization",
                    errorMetadata);
        }
    }

    /**
     * Helper to create a software token
     * @param softwareTokenPin the pin of the token
     * @throws InvalidCharactersException if the pin includes characters outside of ascii (range 32 - 126)
     * @throws WeakPinException if the pin does not meet the requirements set in {@link TokenPinPolicy}
     * @throws SoftwareTokenInitException if token init fails
     */
    private void initializeSoftwareToken(String softwareTokenPin) throws InvalidCharactersException, WeakPinException,
            SoftwareTokenInitException {
        char[] pin = softwareTokenPin.toCharArray();
        if (isTokenPinEnforced) {
            TokenPinPolicy.Description description = TokenPinPolicy.describe(pin);
            if (!description.isValid()) {
                if (description.hasInvalidCharacters()) {
                    throw new InvalidCharactersException("The provided pin code contains invalid characters");
                }
                List<String> metadata = new ArrayList<>();
                metadata.add(METADATA_PIN_MIN_LENGTH);
                metadata.add(String.valueOf(TokenPinPolicy.MIN_PASSWORD_LENGTH));
                metadata.add(METADATA_PIN_MIN_CHAR_CLASSES);
                metadata.add(String.valueOf(TokenPinPolicy.MIN_CHARACTER_CLASS_COUNT));
                throw new WeakPinException("The provided pin code was too weak", metadata);
            }
        }
        try {
            signerProxyFacade.initSoftwareToken(pin);
        } catch (Exception e) {
            // not good
            throw new SoftwareTokenInitException("Error initializing software token", e);
        }
    }

    /**
     * Helper to create the initial server conf with a new server code and owner. If an existing server conf is found
     * and it already has a server code or an owner -> the existing values will not be overridden
     * @param ownerClientId
     * @param securityServerCode
     * @return ServerConfType
     */
    private ServerConfType createInitialServerConf(ClientId ownerClientId, String securityServerCode) {
        ServerConfType serverConf = serverConfService.getOrCreateServerConf();

        if (StringUtils.isEmpty(serverConf.getServerCode())) {
            serverConf.setServerCode(securityServerCode);
        }

        if (serverConf.getOwner() == null) {
            ClientType ownerClient = getInitialClient(ownerClientId);
            ownerClient.setConf(serverConf);
            if (!serverConf.getClient().contains(ownerClient)) {
                serverConf.getClient().add(ownerClient);
            }
            serverConf.setOwner(ownerClient);
        }
        return serverConf;
    }

    /**
     * Check for warnings. Warnings include:
     * - if server code has already been initialized
     * - if server owner has already been initialized
     * - if software token has already been initialized
     * - if trying to add unregistered member as an owner
     * - if the provided server id already exists in global conf
     * @param ownerClientId
     * @param securityServerCode
     * @throws UnhandledWarningsException
     */
    private void checkForWarnings(ClientId ownerClientId, String securityServerCode)
            throws UnhandledWarningsException {
        boolean isServerCodeInitialized = serverConfService.isServerCodeInitialized();
        boolean isServerOwnerInitialized = serverConfService.isServerOwnerInitialized();
        boolean isSoftwareTokenInitialized = tokenService.isSoftwareTokenInitialized();
        String ownerMemberName = globalConfFacade.getMemberName(ownerClientId);
        List<WarningDeviation> warnings = new ArrayList<>();
        if (isServerCodeInitialized) {
            warnings.add(new WarningDeviation(WARNING_SERVERCODE_EXISTS));
        }
        if (isServerOwnerInitialized) {
            warnings.add(new WarningDeviation(WARNING_SERVER_OWNER_EXISTS));
        }
        if (isSoftwareTokenInitialized) {
            warnings.add(new WarningDeviation(WARNING_SOFTWARE_TOKEN_INITIALIZED));
        }
        if (!isServerOwnerInitialized && StringUtils.isEmpty(ownerMemberName)) {
            WarningDeviation memberWarning = new WarningDeviation(WARNING_INIT_UNREGISTERED_MEMBER,
                    ownerClientId.toShortString());
            warnings.add(memberWarning);
        }
        if (!isServerCodeInitialized) {
            SecurityServerId serverId = SecurityServerId.create(ownerClientId, securityServerCode);
            if (globalConfFacade.existsSecurityServer(serverId)) {
                WarningDeviation memberWarning = new WarningDeviation(WARNING_INIT_SERVER_ID_EXISTS,
                        serverId.toShortString());
                warnings.add(memberWarning);
            }
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
     * If missing or empty params are provided for the init
     */
    public static class MissingInitParamsException extends ServiceException {
        public static final String INIT_PARAMS_MISSING = "init_params_missing";

        public MissingInitParamsException(String msg, List<String> metadata) {
            super(msg, new ErrorDeviation(INIT_PARAMS_MISSING, metadata));
        }
    }

    /**
     * If the provided pin code contains invalid characters
     */
    public static class InvalidCharactersException extends ServiceException {
        public static final String INVALID_CHARACTERS_PIN = "invalid_characters_pin";

        public InvalidCharactersException(String msg) {
            super(msg, new ErrorDeviation(INVALID_CHARACTERS_PIN));
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
