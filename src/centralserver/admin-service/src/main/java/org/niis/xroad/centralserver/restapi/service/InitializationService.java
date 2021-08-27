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

import ee.ria.xroad.commonui.SignerProxy;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;
import ee.ria.xroad.signer.protocol.dto.TokenStatusInfo;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.centralserver.restapi.config.HAConfigStatus;
import org.niis.xroad.centralserver.restapi.dto.InitializationConfigDto;
import org.niis.xroad.centralserver.restapi.dto.InitializationStatusDto;
import org.niis.xroad.centralserver.restapi.dto.TokenInitStatusInfo;
import org.niis.xroad.centralserver.restapi.entity.SystemParameter;
import org.niis.xroad.centralserver.restapi.facade.SignerProxyFacade;
import org.niis.xroad.centralserver.restapi.repository.SystemParameterRepository;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.service.ServiceException;
import org.niis.xroad.restapi.service.SignerNotReachableException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.niis.xroad.centralserver.restapi.service.CentralServerSystemParameterService.CENTRAL_SERVER_ADDRESS;
import static org.niis.xroad.centralserver.restapi.service.CentralServerSystemParameterService.CONF_HASH_ALGO_URI;
import static org.niis.xroad.centralserver.restapi.service.CentralServerSystemParameterService.CONF_SIGN_CERT_HASH_ALGO_URI;
import static org.niis.xroad.centralserver.restapi.service.CentralServerSystemParameterService.CONF_SIGN_DIGEST_ALGO_ID;
import static org.niis.xroad.centralserver.restapi.service.CentralServerSystemParameterService.DEFAULT_CONF_HASH_ALGO_URI;
import static org.niis.xroad.centralserver.restapi.service.CentralServerSystemParameterService.DEFAULT_CONF_SIGN_DIGEST_ALGO_ID;
import static org.niis.xroad.centralserver.restapi.service.CentralServerSystemParameterService.DEFAULT_SECURITY_SERVER_OWNERS_GROUP;
import static org.niis.xroad.centralserver.restapi.service.CentralServerSystemParameterService.INSTANCE_IDENTIFIER;
import static org.niis.xroad.centralserver.restapi.service.CentralServerSystemParameterService.SECURITY_SERVER_OWNERS_GROUP;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_INVALID_INIT_PARAMS;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_SERVER_ALREADY_FULLY_INITIALIZED;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_SOFTWARE_TOKEN_INIT_FAILED;

@SuppressWarnings("checkstyle:TodoComment")
@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
public class InitializationService {

    private final SignerProxyFacade signerProxyFacade;
    private final SystemParameterRepository systemParameterRepository;
    private final CentralServerSystemParameterService centralServerSystemParameterService;
    private final HAConfigStatus currentHaConfigStatus;


    public InitializationStatusDto getInitializationStatusDto() {
        TokenInitStatusInfo initStatusInfo;
        try {
            if (isSWTokenInitialized()) {
                initStatusInfo = TokenInitStatusInfo.INITIALIZED;
            } else {
                initStatusInfo = TokenInitStatusInfo.NOT_INITIALIZED;
            }
        } catch (SignerNotReachableException notReachableException) {
            initStatusInfo = TokenInitStatusInfo.UNKNOWN;
        }
        InitializationStatusDto statusDto = new InitializationStatusDto();

        statusDto.setInstanceIdentifier(getStoredInstanceIdentifier());
        statusDto.setCentralServerAddress(getStoredCentralServerAddress());
        statusDto.setTokenInitStatus(initStatusInfo);
        return statusDto;
    }


    public void initialize(InitializationConfigDto configDto)
            throws ServerAlreadyFullyInitializedException, SoftwareTokenInitException {

        if (isCentralServerInitialized()) {
            throw new ServerAlreadyFullyInitializedException(
                    "Central server Initialization failed, already initialized"
            );
        }
        centralServerSystemParameterService.updateOrCreateParameter(
                CENTRAL_SERVER_ADDRESS,
                configDto.getCentralServerAddress()
        );
        centralServerSystemParameterService.updateOrCreateParameter(
                INSTANCE_IDENTIFIER,
                configDto.getInstanceIdentifier()
        );

        // TODO:  Initialize other parameters:
        //          -  to GlobalGroup - table, store SystemParameter::DEFAULT_SECURITY_SERVER_OWNERS_GROUP
        //                              with description  SystemParameter::DEFAULT_SECURITY_SERVER_OWNERS_GROUP_DESC


        centralServerSystemParameterService.updateOrCreateParameter(
                CONF_SIGN_DIGEST_ALGO_ID,
                DEFAULT_CONF_SIGN_DIGEST_ALGO_ID
        );

        centralServerSystemParameterService.updateOrCreateParameter(
                CONF_HASH_ALGO_URI,
                DEFAULT_CONF_HASH_ALGO_URI
        );

        centralServerSystemParameterService.updateOrCreateParameter(
                CONF_SIGN_CERT_HASH_ALGO_URI,
                DEFAULT_CONF_HASH_ALGO_URI
        );

        centralServerSystemParameterService.updateOrCreateParameter(
                SECURITY_SERVER_OWNERS_GROUP,
                DEFAULT_SECURITY_SERVER_OWNERS_GROUP
        );

        try {
            signerProxyFacade.initSoftwareToken(configDto.getSoftwareTokenPin().toCharArray());
        } catch (Exception e) {
            log.warn("Software token initialization failed", e);
            throw new SoftwareTokenInitException("Software token initialization failed", e);
        }
    }


    private boolean isCentralServerInitialized() {
        return isSWTokenInitialized()
                && !getStoredInstanceIdentifier().isEmpty()
                && !getStoredCentralServerAddress().isEmpty();
    }

    private boolean isSWTokenInitialized() {
        boolean isSWTokenInitialized = false;
        List<TokenInfo> tokenInfos;
        try {
            tokenInfos = signerProxyFacade.getTokens();
        } catch (Exception e) {
            throw new SignerNotReachableException("could not list all tokens", e);
        }
        Optional<TokenInfo> firstSWToken = tokenInfos.stream()
                .filter(tokenInfo -> tokenInfo.getId().equals(SignerProxy.SSL_TOKEN_ID))
                .findFirst();

        if (firstSWToken.isPresent()) {
            TokenInfo tokenInfo = firstSWToken.get();
            isSWTokenInitialized = tokenInfo.getStatus() != TokenStatusInfo.NOT_INITIALIZED;
        }
        return isSWTokenInitialized;
    }

    private String getStoredInstanceIdentifier() {
        List<SystemParameter> instanceIdentifier =
                systemParameterRepository.findSystemParameterByKeyAndHaNodeName(
                        INSTANCE_IDENTIFIER,
                        currentHaConfigStatus.getCurrentHaNodeName()
                );
        assert instanceIdentifier.size() <= 1;
        return instanceIdentifier.stream().findFirst().map(SystemParameter::getValue)
                .orElse("");
    }

    private String getStoredCentralServerAddress() {
        List<SystemParameter> serverAddress = systemParameterRepository.findSystemParameterByKeyAndHaNodeName(
                CENTRAL_SERVER_ADDRESS,
                currentHaConfigStatus.getCurrentHaNodeName());
        assert serverAddress.size() <= 1;
        return serverAddress.stream().findFirst().map(SystemParameter::getValue)
                .orElse("");
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

