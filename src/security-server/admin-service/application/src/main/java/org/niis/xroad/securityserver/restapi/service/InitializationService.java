/**
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.conf.serverconf.IsAuthentication;
import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.conf.serverconf.model.ServerConfType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.util.TokenPinPolicy;
import ee.ria.xroad.common.util.process.ExternalProcessRunner;
import ee.ria.xroad.common.util.process.ProcessFailedException;
import ee.ria.xroad.common.util.process.ProcessNotExecutableException;

import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.exceptions.DeviationAwareRuntimeException;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.exceptions.WarningDeviation;
import org.niis.xroad.restapi.service.ServiceException;
import org.niis.xroad.restapi.service.UnhandledWarningsException;
import org.niis.xroad.securityserver.restapi.dto.InitializationStatusDto;
import org.niis.xroad.securityserver.restapi.dto.TokenInitStatusInfo;
import org.niis.xroad.securityserver.restapi.facade.GlobalConfFacade;
import org.niis.xroad.securityserver.restapi.facade.SignerProxyFacade;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OWNER_IDENTIFIER;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.SERVER_CODE;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_GPG_KEY_GENERATION_FAILED;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_INVALID_INIT_PARAMS;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_METADATA_MEMBER_CLASS_EXISTS;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_METADATA_MEMBER_CLASS_NOT_PROVIDED;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_METADATA_MEMBER_CODE_EXISTS;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_METADATA_MEMBER_CODE_NOT_PROVIDED;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_METADATA_PIN_EXISTS;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_METADATA_PIN_NOT_PROVIDED;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_METADATA_SERVERCODE_EXISTS;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_METADATA_SERVERCODE_NOT_PROVIDED;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_SERVER_ALREADY_FULLY_INITIALIZED;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_SOFTWARE_TOKEN_INIT_FAILED;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.WARNING_INIT_SERVER_ID_EXISTS;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.WARNING_INIT_UNREGISTERED_MEMBER;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.WARNING_SERVERCODE_EXISTS;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.WARNING_SERVER_OWNER_EXISTS;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.WARNING_SOFTWARE_TOKEN_INITIALIZED;

/**
 * service for initializing the security server
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class InitializationService {
    private final SystemService systemService;
    private final ServerConfService serverConfService;
    private final TokenService tokenService;
    private final GlobalConfFacade globalConfFacade;
    private final ClientService clientService;
    private final SignerProxyFacade signerProxyFacade;
    private final AuditDataHelper auditDataHelper;
    private final TokenPinValidator tokenPinValidator;
    private final ExternalProcessRunner externalProcessRunner;

    @Setter
    @Value("${script.generate-gpg-keypair.path}")
    private String generateKeypairScriptPath;
    @Setter
    @Value("${gpgkeys.gpghome}")
    private String gpgHome;

    /**
     * Check the whole init status of the Security Server. The init status consists of the following:
     * 1. is anchor imported - whether or not a configuration anchor has been imported
     * 2. is server code initialized - whether or not a server code has been initialized
     * 3. is server owner initialized - whether or not a server owner has been initialized
     * 4. software token initialization status - whether or not a software token exists AND
     * it's status != TokenStatusInfo.NOT_INITIALIZED. If an exception is thrown when querying signer, software token
     * init status will be UNKNOWN
     *
     * @return
     */
    public InitializationStatusDto getSecurityServerInitializationStatus() {
        boolean isAnchorImported = systemService.isAnchorImported();
        boolean isServerCodeInitialized = serverConfService.isServerCodeInitialized();
        boolean isServerOwnerInitialized = serverConfService.isServerOwnerInitialized();
        TokenInitStatusInfo tokenInitStatus = tokenService.getSoftwareTokenInitStatus();
        InitializationStatusDto initializationStatusDto = new InitializationStatusDto();
        initializationStatusDto.setAnchorImported(isAnchorImported);
        initializationStatusDto.setServerCodeInitialized(isServerCodeInitialized);
        initializationStatusDto.setServerOwnerInitialized(isServerOwnerInitialized);
        initializationStatusDto.setSoftwareTokenInitStatusInfo(tokenInitStatus);
        return initializationStatusDto;
    }

    /**
     * Initialize a new Security Server with the provided parameters. Supports partial initialization which means
     * that if e.g. software token has already been initialized, it will not get initialized again, therefore the
     * parameter <code>String softwareTokenPin</code> can be <code>null</code>.
     *
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
     * @throws InvalidCharactersException if the provided pin code does not follow the TokenPinPolicy (if token pin
     * policy is enforced by properties). In other words pin code contains invalid characters (not ascii)
     * @throws SoftwareTokenInitException if something goes wrong with the token init
     * @throws InvalidInitParamsException if empty or missing or redundant parameters are provided
     * @throws ServerAlreadyFullyInitializedException if the server has already been fully initialized
     */
    public void initialize(String securityServerCode, String ownerMemberClass, String ownerMemberCode,
                           String softwareTokenPin, boolean ignoreWarnings) throws AnchorNotFoundException, WeakPinException,
            UnhandledWarningsException, InvalidCharactersException, SoftwareTokenInitException,
            InvalidInitParamsException, ServerAlreadyFullyInitializedException, InterruptedException {
        if (!systemService.isAnchorImported()) {
            throw new AnchorNotFoundException("Configuration anchor was not found.");
        }
        boolean isServerCodeInitialized = serverConfService.isServerCodeInitialized();
        boolean isServerOwnerInitialized = serverConfService.isServerOwnerInitialized();
        boolean isSoftwareTokenInitialized = tokenService.isSoftwareTokenInitialized();
        if (isServerCodeInitialized && isServerOwnerInitialized && isSoftwareTokenInitialized) {
            throw new ServerAlreadyFullyInitializedException("Security server has already been fully initialized");
        }
        verifyInitializationPrerequisites(securityServerCode, ownerMemberClass, ownerMemberCode, softwareTokenPin,
                isServerCodeInitialized, isServerOwnerInitialized, isSoftwareTokenInitialized);
        String instanceIdentifier = globalConfFacade.getInstanceIdentifier();
        ClientId.Conf ownerClientId = null;
        if (isServerOwnerInitialized) {
            ownerClientId = serverConfService.getSecurityServerOwnerId();
        } else {
            ownerClientId = ClientId.Conf.create(instanceIdentifier, ownerMemberClass, ownerMemberCode);
        }
        auditDataHelper.put(OWNER_IDENTIFIER, ownerClientId);
        auditDataHelper.put(SERVER_CODE, securityServerCode);
        if (!ignoreWarnings) {
            checkForWarnings(ownerClientId, securityServerCode);
        }

        // Both software token initialisation and GPG key generation are non transactional
        // when second one fails server server moves to unusable state

        // --- Start the init ---
        ServerConfType serverConf = createInitialServerConf(ownerClientId, securityServerCode);
        if (!isSoftwareTokenInitialized) {
            initializeSoftwareToken(softwareTokenPin);
        }

        // the same algorithm is used in get_security_server_id.sh script
        String keyRealName = ownerClientId.getXRoadInstance() + "/" + ownerClientId.getMemberClass() + "/"
                + ownerClientId.getMemberCode() + "/" + serverConf.getServerCode();
        generateGPGKeyPair(keyRealName);

        serverConfService.saveOrUpdate(serverConf);
    }

    /**
     * Verify that when initializing a new security server, all required parameters are provided.
     * If old values DO NOT exist -> new values must be provided.
     * If old values DO exists -> new values are not allowed
     *
     * @param securityServerCode
     * @param ownerMemberClass
     * @param ownerMemberCode
     * @param softwareTokenPin
     * @param isServerCodeInitialized
     * @param isServerOwnerInitialized
     * @param isSoftwareTokenInitialized
     * @throws InvalidInitParamsException if null, empty or redundant init parameters provided
     */
    @SuppressWarnings("squid:S3776") // cognitive complexity 17/15 (because of IF's and logical AND's)
    private void verifyInitializationPrerequisites(String securityServerCode, String ownerMemberClass,
                                                   String ownerMemberCode, String softwareTokenPin,
                                                   boolean isServerCodeInitialized,
                                                   boolean isServerOwnerInitialized,
                                                   boolean isSoftwareTokenInitialized) throws InvalidInitParamsException {
        List<String> errorMetadata = new ArrayList<>();
        /*
         * Example case:
         * If server code does not exist -> securityServerCode param is required in the request.
         * If server code already exists -> securityServerCode param is not allowed in the request.
         */

        boolean isEmptySecurityServerCode = StringUtils.isEmpty(securityServerCode);
        boolean isEmptyOwnerMemberClass = StringUtils.isEmpty(ownerMemberClass);
        boolean isEmptyOwnerMemberCode = StringUtils.isEmpty(ownerMemberCode);
        boolean isEmptySoftwareTokenPin = StringUtils.isEmpty(softwareTokenPin);

        boolean shouldProvideServerCode = isEmptySecurityServerCode && !isServerCodeInitialized;
        if (shouldProvideServerCode) {
            errorMetadata.add(ERROR_METADATA_SERVERCODE_NOT_PROVIDED);
        }

        boolean shouldNotProvideServerCode = !isEmptySecurityServerCode && isServerCodeInitialized;
        if (shouldNotProvideServerCode) {
            errorMetadata.add(ERROR_METADATA_SERVERCODE_EXISTS);
        }

        boolean shouldProvideOwnerMemberClass = isEmptyOwnerMemberClass && !isServerOwnerInitialized;
        if (shouldProvideOwnerMemberClass) {
            errorMetadata.add(ERROR_METADATA_MEMBER_CLASS_NOT_PROVIDED);
        }

        boolean shouldNotProvideOwnerMemberClass = !isEmptyOwnerMemberClass && isServerOwnerInitialized;
        if (shouldNotProvideOwnerMemberClass) {
            errorMetadata.add(ERROR_METADATA_MEMBER_CLASS_EXISTS);
        }

        boolean shouldProvideOwnerMemberCode = isEmptyOwnerMemberCode && !isServerOwnerInitialized;
        if (shouldProvideOwnerMemberCode) {
            errorMetadata.add(ERROR_METADATA_MEMBER_CODE_NOT_PROVIDED);
        }

        boolean shouldNotProvideOwnerMemberCode = !isEmptyOwnerMemberCode && isServerOwnerInitialized;
        if (shouldNotProvideOwnerMemberCode) {
            errorMetadata.add(ERROR_METADATA_MEMBER_CODE_EXISTS);
        }

        boolean shouldProvideSoftwareTokenPin = isEmptySoftwareTokenPin && !isSoftwareTokenInitialized;
        if (shouldProvideSoftwareTokenPin) {
            errorMetadata.add(ERROR_METADATA_PIN_NOT_PROVIDED);
        }

        boolean shouldNotProvideSoftwareTokenPin = !isEmptySoftwareTokenPin && isSoftwareTokenInitialized;
        if (shouldNotProvideSoftwareTokenPin) {
            errorMetadata.add(ERROR_METADATA_PIN_EXISTS);
        }

        if (!errorMetadata.isEmpty()) {
            throw new InvalidInitParamsException("Empty, missing or redundant parameters provided for initialization",
                    errorMetadata);
        }
    }

    /**
     * Helper to create a software token
     *
     * @param softwareTokenPin the pin of the token
     * @throws InvalidCharactersException if the pin includes characters outside of ascii (range 32 - 126)
     * @throws WeakPinException           if the pin does not meet the requirements set in {@link TokenPinPolicy}
     * @throws SoftwareTokenInitException if token init fails
     */
    private void initializeSoftwareToken(String softwareTokenPin) throws InvalidCharactersException, WeakPinException,
            SoftwareTokenInitException {
        char[] pin = softwareTokenPin.toCharArray();
        tokenPinValidator.validateSoftwareTokenPin(pin);
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
     *
     * @param ownerClientId
     * @param securityServerCode
     * @return ServerConfType
     */
    private ServerConfType createInitialServerConf(ClientId.Conf ownerClientId, String securityServerCode) {
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
     *
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
            SecurityServerId.Conf serverId = SecurityServerId.Conf.create(ownerClientId, securityServerCode);
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
     *
     * @param clientId
     * @return
     */
    private ClientType getInitialClient(ClientId.Conf clientId) {
        ClientType localClient = clientService.getLocalClient(clientId);
        if (localClient == null) {
            localClient = new ClientType();
            localClient.setIdentifier(clientId);
            localClient.setClientStatus(ClientType.STATUS_SAVED);
            localClient.setIsAuthentication(IsAuthentication.SSLAUTH.name());
        }
        return localClient;
    }

    private void generateGPGKeyPair(String nameReal) throws InterruptedException {
        String[] args = new String[]{gpgHome, nameReal};

        try {
            log.info("Generationg GPG keypair with command '"
                    + generateKeypairScriptPath + " " + Arrays.toString(args) + "'");

            ExternalProcessRunner.ProcessResult processResult = externalProcessRunner
                    .executeAndThrowOnFailure(generateKeypairScriptPath, args);

            log.info(" --- Generate GPG keypair script console output - START --- ");
            log.info(String.join("\n", processResult.getProcessOutput()));
            log.info(" --- Generate GPG keypair script console output - END --- ");
        } catch (ProcessNotExecutableException | ProcessFailedException e) {
            throw new DeviationAwareRuntimeException(e, new ErrorDeviation(ERROR_GPG_KEY_GENERATION_FAILED));
        }
        // todo check the keypair is really created? how?
    }

    /**
     * If missing or empty or redundant params are provided for the init
     */
    public static class InvalidInitParamsException extends ServiceException {
        public InvalidInitParamsException(String msg, List<String> metadata) {
            super(msg, new ErrorDeviation(ERROR_INVALID_INIT_PARAMS, metadata));
        }
    }

    /**
     * If the software token init fails
     */
    public static class SoftwareTokenInitException extends ServiceException {
        public SoftwareTokenInitException(String msg, Throwable t) {
            super(msg, t, new ErrorDeviation(ERROR_SOFTWARE_TOKEN_INIT_FAILED));
        }
    }

    /**
     * If the server has already been fully initialized
     */
    public static class ServerAlreadyFullyInitializedException extends ServiceException {
        public ServerAlreadyFullyInitializedException(String msg) {
            super(msg, new ErrorDeviation(ERROR_SERVER_ALREADY_FULLY_INITIALIZED));
        }
    }
}
