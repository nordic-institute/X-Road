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

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.centralserver.openapi.model.TokenInitStatus;
import org.niis.xroad.centralserver.restapi.dto.InitializationConfigDto;
import org.niis.xroad.centralserver.restapi.dto.InitializationStatusDto;
import org.niis.xroad.centralserver.restapi.facade.SignerProxyFacade;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.service.ServiceException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_INVALID_INIT_PARAMS;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_METADATA_PIN_NOT_PROVIDED;
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

    public InitializationStatusDto getInitializationStatusDto() {
        InitializationStatusDto statusDto = new InitializationStatusDto();
        // TODO: get Identifier and Address from real SystemParameter table
        statusDto.setInstanceIdentifier(getStoredInstanceIdentifier());
        statusDto.setCentralServerAddress("TODO-central-server-address-should-come-from-SystemParameter-table");
        statusDto.setTokenInitStatus(getTokenInitStatus());
        return statusDto;
    }

    public void initialize(InitializationConfigDto configDto) throws InvalidInitParamsException {

        // TODO: Validate instance_identifier if not already stored at db SystemParamater

        // TODO: Validate central_server_address if not already stored at SystemParamater table

        // TODO: Check if software_token is already initialized (initialized?)

        // TODO: If previous 3 are true -->  already initialized --> throw: init.already_initialized

        // TODO: Validate params
        validateConfigOrThrow(configDto);


        // TODO: Store instance identifier to SystemParameter - table   --- CONSIDERING HA node info

        // TODO: store server address to SystemParameter - table  --- CONSIDERING HA node info

        // TODO:  Initialize other parameters:
        //          -  to GlobalGroup - table, store SystemParameter::DEFAULT_SECURITY_SERVER_OWNERS_GROUP
        //                              with description  SystemParameter::DEFAULT_SECURITY_SERVER_OWNERS_GROUP_DESC
        //          -  to SystemParamater -table - NOTE: in HA setups node_local_parameters need to be updated to the
        //                                               HA node -specific entry
        //                  - SystemParameter::CONF_SIGN_DIGEST_ALGO_ID with
        //                            SystemParameter::DEFAULT_CONF_SIGN_DIGEST_ALGO_ID
        //                  - SystemParameter::CONF_HASH_ALGO_URI with
        //                            SystemParameter::DEFAULT_CONF_HASH_ALGO_URI
        //                  - SystemParameter::CONF_SIGN_CERT_HASH_ALGO_URI
        //                              with  SystemParameter::DEFAULT_CONF_SIGN_CERT_HASH_ALGO_URI
        //                  - SystemParameter::SECURITY_SERVER_OWNERS_GROUP
        //                             with SystemParameter::DEFAULT_SECURITY_SERVER_OWNERS_GROUP

        // TODO: init software token with SignerProxy

    }

    private void validateConfigOrThrow(InitializationConfigDto configDto) throws InvalidInitParamsException {

        List<String> validationErrors = new ArrayList<>();

        // Instance identifier shall not contain any %;:/\ or CharMatcher.javaIsoControl().matchesAnyOf(s)
        // Continuation:     || s.indexOf(FORBIDDEN_BOM) >= 0
        // Continuation:                and || s.indexOf(FORBIDDEN_ZWSP) >= 0;



        // serverAddress: valid internet address or domain name,

        // SW token: must exist
        if (configDto.getSoftwareTokenPin().isEmpty()) {
            validationErrors.add(ERROR_METADATA_PIN_NOT_PROVIDED);
        }

        if (validationErrors.isEmpty()) {
            throw new InvalidInitParamsException("Bad parameters given", validationErrors);
        }


    }


    private TokenInitStatus getTokenInitStatus() {
        // TODO: is SW token initialized - SignerProxy has entry with  SSL_TOKEN_ID


        return TokenInitStatus.UNKNOWN;
    }

    private String getStoredInstanceIdentifier() {

        return "fake-instance";
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

