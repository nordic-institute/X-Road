/**
 * The MIT License
 * <p>
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
package org.niis.xroad.centralserver.restapi.service;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.commonui.SignerProxy;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;
import ee.ria.xroad.signer.protocol.dto.TokenStatusInfo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.centralserver.restapi.config.HAConfigStatus;
import org.niis.xroad.centralserver.restapi.dto.InitializationConfigDto;
import org.niis.xroad.centralserver.restapi.dto.InitializationStatusDto;
import org.niis.xroad.centralserver.restapi.dto.TokenInitStatusInfo;
import org.niis.xroad.centralserver.restapi.entity.GlobalGroup;
import org.niis.xroad.centralserver.restapi.repository.GlobalGroupRepository;
import org.niis.xroad.centralserver.restapi.service.exception.InvalidCharactersException;
import org.niis.xroad.centralserver.restapi.service.exception.InvalidInitParamsException;
import org.niis.xroad.centralserver.restapi.service.exception.ServerAlreadyFullyInitializedException;
import org.niis.xroad.centralserver.restapi.service.exception.SoftwareTokenInitException;
import org.niis.xroad.centralserver.restapi.service.exception.WeakPinException;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;
import org.niis.xroad.restapi.service.SignerNotReachableException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static ee.ria.xroad.common.ErrorCodes.X_KEY_NOT_FOUND;
import static org.niis.xroad.centralserver.restapi.service.SystemParameterService.CENTRAL_SERVER_ADDRESS;
import static org.niis.xroad.centralserver.restapi.service.SystemParameterService.CONF_HASH_ALGO_URI;
import static org.niis.xroad.centralserver.restapi.service.SystemParameterService.CONF_SIGN_CERT_HASH_ALGO_URI;
import static org.niis.xroad.centralserver.restapi.service.SystemParameterService.CONF_SIGN_DIGEST_ALGO_ID;
import static org.niis.xroad.centralserver.restapi.service.SystemParameterService.DEFAULT_CONF_HASH_ALGO_URI;
import static org.niis.xroad.centralserver.restapi.service.SystemParameterService.DEFAULT_CONF_SIGN_DIGEST_ALGO_ID;
import static org.niis.xroad.centralserver.restapi.service.SystemParameterService.DEFAULT_SECURITY_SERVER_OWNERS_GROUP;
import static org.niis.xroad.centralserver.restapi.service.SystemParameterService.DEFAULT_SECURITY_SERVER_OWNERS_GROUP_DESC;
import static org.niis.xroad.centralserver.restapi.service.SystemParameterService.INSTANCE_IDENTIFIER;
import static org.niis.xroad.centralserver.restapi.service.SystemParameterService.SECURITY_SERVER_OWNERS_GROUP;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_METADATA_INSTANCE_IDENTIFIER_EXISTS;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_METADATA_INSTANCE_IDENTIFIER_NOT_PROVIDED;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_METADATA_PIN_EXISTS;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_METADATA_PIN_NOT_PROVIDED;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_METADATA_SERVER_ADDRESS_EXISTS;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_METADATA_SERVER_ADDRESS_NOT_PROVIDED;

@SuppressWarnings("checkstyle:TodoComment")
@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class InitializationService {

    private final SignerProxyService signerProxyService;
    private final GlobalGroupRepository globalGroupRepository;
    private final SystemParameterService systemParameterService;
    private final TokenPinValidator tokenPinValidator;
    private final AuditDataHelper auditDataHelper;
    private final HAConfigStatus currentHaConfigStatus;


    public InitializationStatusDto getInitializationStatus() {
        TokenInitStatusInfo initStatusInfo;
        initStatusInfo = getTokenInitStatusInfo();
        InitializationStatusDto statusDto = new InitializationStatusDto();

        statusDto.setInstanceIdentifier(getStoredInstanceIdentifier());
        statusDto.setCentralServerAddress(getStoredCentralServerAddress());
        statusDto.setTokenInitStatus(initStatusInfo);
        return statusDto;
    }

    private TokenInitStatusInfo getTokenInitStatusInfo() {
        TokenInitStatusInfo initStatusInfo;
        try {
            if (isSWTokenInitialized()) {
                initStatusInfo = TokenInitStatusInfo.INITIALIZED;
            } else {
                initStatusInfo = TokenInitStatusInfo.NOT_INITIALIZED;
            }
        } catch (SignerNotReachableException notReachableException) {
            log.info("getInitializationStatus - signer was not reachable", notReachableException);
            initStatusInfo = TokenInitStatusInfo.UNKNOWN;
        }
        return initStatusInfo;
    }


    public void initialize(InitializationConfigDto configDto)
            throws ServerAlreadyFullyInitializedException, SoftwareTokenInitException, InvalidCharactersException,
            WeakPinException, InvalidInitParamsException {

        log.debug("initializing server with {}", configDto);

        auditDataHelper.put(RestApiAuditProperty.CENTRAL_SERVER_ADDRESS, configDto.getCentralServerAddress());
        auditDataHelper.put(RestApiAuditProperty.INSTANCE_IDENTIFIER, configDto.getInstanceIdentifier());
        auditDataHelper.put(RestApiAuditProperty.HA_NODE, currentHaConfigStatus.getCurrentHaNodeName());

        if (null == configDto.getSoftwareTokenPin()) {
            configDto.setSoftwareTokenPin("");
        }
        if (null == configDto.getCentralServerAddress()) {
            configDto.setCentralServerAddress("");
        }
        if (null == configDto.getInstanceIdentifier()) {
            configDto.setInstanceIdentifier("");
        }

        if (null != configDto.getSoftwareTokenPin()) {
            tokenPinValidator.validateSoftwareTokenPin(configDto.getSoftwareTokenPin().toCharArray());
        }

        final boolean isSWTokenInitialized = TokenInitStatusInfo.INITIALIZED == getTokenInitStatusInfo();
        final boolean isServerAddressInitialized = !getStoredCentralServerAddress().isEmpty();
        final boolean isInstanceIdentifierInitialized = !getStoredInstanceIdentifier().isEmpty();
        validateConfigParameters(configDto, isSWTokenInitialized, isServerAddressInitialized,
                isInstanceIdentifierInitialized);

        if (!isServerAddressInitialized) {
            systemParameterService.updateOrCreateParameter(
                    CENTRAL_SERVER_ADDRESS,
                    configDto.getCentralServerAddress()
            );
        }

        if (!isInstanceIdentifierInitialized) {
            systemParameterService.updateOrCreateParameter(
                    INSTANCE_IDENTIFIER,
                    configDto.getInstanceIdentifier()
            );
        }

        initializeGlobalGroupForSecurityServerOwners();

        initializeCsSystemParameters();

        if (!isSWTokenInitialized) {
            try {
                signerProxyService.initSoftwareToken(configDto.getSoftwareTokenPin().toCharArray());
            } catch (Exception e) {
                log.warn("Software token initialization failed", e);
                throw new SoftwareTokenInitException("Software token initialization failed", e);
            }
        }
    }

    private void validateConfigParameters(InitializationConfigDto configDto, boolean isSWTokenInitialized,
                                          boolean isServerAddressInitialized, boolean isInstanceIdentifierInitialized)
            throws ServerAlreadyFullyInitializedException, InvalidInitParamsException {


        if (isSWTokenInitialized && isServerAddressInitialized && isInstanceIdentifierInitialized) {
            throw new ServerAlreadyFullyInitializedException(
                    "Central server Initialization failed, already fully initialized"
            );
        }
        List<String> errorMetadata = new ArrayList<>();
        if (isSWTokenInitialized && !configDto.getSoftwareTokenPin().isEmpty()) {
            errorMetadata.add(ERROR_METADATA_PIN_EXISTS);
        }
        if (!isSWTokenInitialized && configDto.getSoftwareTokenPin().isEmpty()) {
            errorMetadata.add(ERROR_METADATA_PIN_NOT_PROVIDED);
        }
        if (isServerAddressInitialized && !configDto.getCentralServerAddress().isEmpty()) {
            errorMetadata.add(ERROR_METADATA_SERVER_ADDRESS_EXISTS);
        }
        if (!isServerAddressInitialized && configDto.getCentralServerAddress().isEmpty()) {
            errorMetadata.add(ERROR_METADATA_SERVER_ADDRESS_NOT_PROVIDED);
        }
        if (isInstanceIdentifierInitialized && !configDto.getInstanceIdentifier().isEmpty()) {
            errorMetadata.add(ERROR_METADATA_INSTANCE_IDENTIFIER_EXISTS);
        }
        if (!isInstanceIdentifierInitialized && configDto.getInstanceIdentifier().isEmpty()) {
            errorMetadata.add(ERROR_METADATA_INSTANCE_IDENTIFIER_NOT_PROVIDED);
        }
        if (!errorMetadata.isEmpty()) {
            log.debug("collected errors {}", String.join(", ", errorMetadata));
            throw new InvalidInitParamsException("Empty, missing or redundant parameters provided for initialization",
                    errorMetadata);
        }
    }

    private void initializeCsSystemParameters() {
        systemParameterService.updateOrCreateParameter(
                CONF_SIGN_DIGEST_ALGO_ID,
                DEFAULT_CONF_SIGN_DIGEST_ALGO_ID
        );
        systemParameterService.updateOrCreateParameter(
                CONF_HASH_ALGO_URI,
                DEFAULT_CONF_HASH_ALGO_URI
        );
        systemParameterService.updateOrCreateParameter(
                CONF_SIGN_CERT_HASH_ALGO_URI,
                DEFAULT_CONF_HASH_ALGO_URI
        );
        systemParameterService.updateOrCreateParameter(
                SECURITY_SERVER_OWNERS_GROUP,
                DEFAULT_SECURITY_SERVER_OWNERS_GROUP
        );
    }

    private void initializeGlobalGroupForSecurityServerOwners() {
        Optional<GlobalGroup> securityServerOwnersGlobalGroup = globalGroupRepository
                .getByGroupCode(DEFAULT_SECURITY_SERVER_OWNERS_GROUP);
        if (securityServerOwnersGlobalGroup.isEmpty()) {
            GlobalGroup defaultSsOwnersGlobalGroup = new GlobalGroup();
            defaultSsOwnersGlobalGroup.setGroupCode(DEFAULT_SECURITY_SERVER_OWNERS_GROUP);
            securityServerOwnersGlobalGroup = Optional.of(defaultSsOwnersGlobalGroup);
        }
        securityServerOwnersGlobalGroup.get().setDescription(DEFAULT_SECURITY_SERVER_OWNERS_GROUP_DESC);
        globalGroupRepository.save(securityServerOwnersGlobalGroup.get());
    }

    private boolean isSWTokenInitialized() {
        boolean isSWTokenInitialized = false;
        TokenInfo tokenInfo;
        try {
            tokenInfo = signerProxyService.getToken(SignerProxy.SSL_TOKEN_ID);
            if (null != tokenInfo) {
                isSWTokenInitialized = tokenInfo.getStatus() != TokenStatusInfo.NOT_INITIALIZED;
            }
        } catch (Exception e) {
            if (!(e instanceof CodedException
                    && X_KEY_NOT_FOUND.equals(((CodedException) e).getFaultCode())
            )) {
                throw new SignerNotReachableException("could not list all tokens", e);
            }
        }
        return isSWTokenInitialized;
    }


    private String getStoredInstanceIdentifier() {
        return systemParameterService.getParameterValue(
                INSTANCE_IDENTIFIER,
                ""
        );
    }

    private String getStoredCentralServerAddress() {
        return systemParameterService.getParameterValue(
                CENTRAL_SERVER_ADDRESS,
                "");
    }

}

