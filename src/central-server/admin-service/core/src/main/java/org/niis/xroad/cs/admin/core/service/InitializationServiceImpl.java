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
package org.niis.xroad.cs.admin.core.service;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.ErrorCodes;
import ee.ria.xroad.commonui.SignerProxy;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;
import ee.ria.xroad.signer.protocol.dto.TokenStatusInfo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.exception.DataIntegrityException;
import org.niis.xroad.common.exception.ValidationFailureException;
import org.niis.xroad.cs.admin.api.dto.HAConfigStatus;
import org.niis.xroad.cs.admin.api.dto.InitialServerConfDto;
import org.niis.xroad.cs.admin.api.dto.InitializationStatusDto;
import org.niis.xroad.cs.admin.api.dto.TokenInitStatus;
import org.niis.xroad.cs.admin.api.facade.SignerProxyFacade;
import org.niis.xroad.cs.admin.api.service.InitializationService;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;
import org.niis.xroad.cs.admin.api.service.TokenPinValidator;
import org.niis.xroad.cs.admin.core.entity.GlobalGroupEntity;
import org.niis.xroad.cs.admin.core.repository.GlobalGroupRepository;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.RestApiAuditProperty;
import org.niis.xroad.restapi.service.SignerNotReachableException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

import java.util.Optional;

import static ee.ria.xroad.common.ErrorCodes.X_KEY_NOT_FOUND;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.INIT_ALREADY_INITIALIZED;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.INIT_SIGNER_PIN_POLICY_FAILED;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.INIT_SOFTWARE_TOKEN_FAILED;

@SuppressWarnings("checkstyle:TodoComment")
@Slf4j
@Service
@Transactional(rollbackOn = ValidationFailureException.class)
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class InitializationServiceImpl implements InitializationService {
    private final SignerProxyFacade signerProxyFacade;
    private final GlobalGroupRepository globalGroupRepository;
    private final SystemParameterService systemParameterService;
    private final TokenPinValidator tokenPinValidator;
    private final AuditDataHelper auditDataHelper;
    private final HAConfigStatus currentHaConfigStatus;

    @Override
    public InitializationStatusDto getInitializationStatus() {
        TokenInitStatus initStatusInfo = getTokenInitStatusInfo();
        InitializationStatusDto statusDto = new InitializationStatusDto();

        statusDto.setInstanceIdentifier(systemParameterService.getInstanceIdentifier());
        statusDto.setCentralServerAddress(systemParameterService.getCentralServerAddress());
        statusDto.setSoftwareTokenInitStatus(initStatusInfo);
        return statusDto;
    }

    private TokenInitStatus getTokenInitStatusInfo() {
        TokenInitStatus initStatusInfo;
        try {
            if (isSWTokenInitialized()) {
                initStatusInfo = TokenInitStatus.INITIALIZED;
            } else {
                initStatusInfo = TokenInitStatus.NOT_INITIALIZED;
            }
        } catch (SignerNotReachableException notReachableException) {
            log.info("getInitializationStatus - signer was not reachable", notReachableException);
            initStatusInfo = TokenInitStatus.UNKNOWN;
        }
        return initStatusInfo;
    }

    @Override
    public void initialize(InitialServerConfDto configDto) {

        log.debug("initializing server with {}", configDto);

        auditDataHelper.put(RestApiAuditProperty.CENTRAL_SERVER_ADDRESS, configDto.getCentralServerAddress());
        auditDataHelper.put(RestApiAuditProperty.INSTANCE_IDENTIFIER, configDto.getInstanceIdentifier());
        auditDataHelper.put(RestApiAuditProperty.HA_NODE, currentHaConfigStatus.getCurrentHaNodeName());

        final boolean isSWTokenInitialized = TokenInitStatus.INITIALIZED == getTokenInitStatusInfo();
        final boolean isServerAddressInitialized = !systemParameterService.getCentralServerAddress().isEmpty();
        final boolean isInstanceIdentifierInitialized = !systemParameterService.getInstanceIdentifier().isEmpty();
        if (isSWTokenInitialized && isServerAddressInitialized && isInstanceIdentifierInitialized) {
            throw new DataIntegrityException(INIT_ALREADY_INITIALIZED);
        }

        if (!isSWTokenInitialized) {
            tokenPinValidator.validateSoftwareTokenPin(configDto.getSoftwareTokenPin().toCharArray());
        }


        if (!isServerAddressInitialized) {
            systemParameterService.updateOrCreateParameter(
                    SystemParameterService.CENTRAL_SERVER_ADDRESS,
                    configDto.getCentralServerAddress()
            );
        }

        if (!isInstanceIdentifierInitialized) {
            systemParameterService.updateOrCreateParameter(
                    SystemParameterService.INSTANCE_IDENTIFIER,
                    configDto.getInstanceIdentifier()
            );
        }

        initializeGlobalGroupForSecurityServerOwners();

        initializeCsSystemParameters();

        if (!isSWTokenInitialized) {
            try {
                signerProxyFacade.initSoftwareToken(configDto.getSoftwareTokenPin().toCharArray());
            } catch (Exception e) {
                if (e instanceof CodedException
                        && ((CodedException) e).getFaultCode().contains(ErrorCodes.X_TOKEN_PIN_POLICY_FAILURE)) {
                    log.warn("Signer saw Token pin policy failure, remember to restart also the central server after "
                            + "configuring policy enforcement", e);
                    throw new ValidationFailureException(INIT_SIGNER_PIN_POLICY_FAILED);
                }
                log.warn("Software token initialization failed", e);
                throw new DataIntegrityException(INIT_SOFTWARE_TOKEN_FAILED, e);
            }
        }
    }

    private void initializeCsSystemParameters() {
        systemParameterService.updateOrCreateParameter(
                SystemParameterServiceImpl.CONF_SIGN_DIGEST_ALGO_ID,
                SystemParameterServiceImpl.DEFAULT_CONF_SIGN_DIGEST_ALGO_ID
        );
        systemParameterService.updateOrCreateParameter(
                SystemParameterServiceImpl.CONF_HASH_ALGO_URI,
                SystemParameterServiceImpl.DEFAULT_CONF_HASH_ALGO_URI
        );
        systemParameterService.updateOrCreateParameter(
                SystemParameterServiceImpl.CONF_SIGN_CERT_HASH_ALGO_URI,
                SystemParameterServiceImpl.DEFAULT_CONF_HASH_ALGO_URI
        );
        systemParameterService.updateOrCreateParameter(
                SystemParameterServiceImpl.SECURITY_SERVER_OWNERS_GROUP,
                SystemParameterServiceImpl.DEFAULT_SECURITY_SERVER_OWNERS_GROUP
        );
    }

    private void initializeGlobalGroupForSecurityServerOwners() {
        Optional<GlobalGroupEntity> securityServerOwnersGlobalGroup = globalGroupRepository
                .getByGroupCode(SystemParameterServiceImpl.DEFAULT_SECURITY_SERVER_OWNERS_GROUP);
        if (securityServerOwnersGlobalGroup.isEmpty()) {
            var defaultSsOwnersGlobalGroup =
                    new GlobalGroupEntity(SystemParameterServiceImpl.DEFAULT_SECURITY_SERVER_OWNERS_GROUP);
            securityServerOwnersGlobalGroup = Optional.of(defaultSsOwnersGlobalGroup);
        }
        securityServerOwnersGlobalGroup.get()
                .setDescription(SystemParameterServiceImpl.DEFAULT_SECURITY_SERVER_OWNERS_GROUP_DESC);
        globalGroupRepository.save(securityServerOwnersGlobalGroup.get());
    }

    private boolean isSWTokenInitialized() {
        boolean isSWTokenInitialized = false;
        TokenInfo tokenInfo;
        try {
            tokenInfo = signerProxyFacade.getToken(SignerProxy.SSL_TOKEN_ID);
            if (null != tokenInfo) {
                isSWTokenInitialized = tokenInfo.getStatus() != TokenStatusInfo.NOT_INITIALIZED;
            }
        } catch (Exception e) {
            if (!((e instanceof CodedException) && X_KEY_NOT_FOUND.equals(((CodedException) e).getFaultCode()))) {
                throw new SignerNotReachableException("could not list all tokens", e);
            }
        }
        return isSWTokenInitialized;
    }

}

