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

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.conf.serverconf.model.ServerConfType;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;
import ee.ria.xroad.signer.protocol.dto.TokenStatusInfo;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.dto.AnchorFile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static org.niis.xroad.restapi.service.PossibleActionsRuleEngine.SOFTWARE_TOKEN_ID;

/**
 * service for initializing the security server
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
public class InitializationService {
    private final SystemService systemService;
    private final ServerConfService serverConfService;
    private final TokenService tokenService;

    @Autowired
    public InitializationService(SystemService systemService,
            ServerConfService serverConfService, TokenService tokenService) {
        this.systemService = systemService;
        this.serverConfService = serverConfService;
        this.tokenService = tokenService;
    }

    /**
     * Check the whole init status of the Security Server. The init status consists of the following:
     * global conf - whether or not a configuration anchor has been imported
     * server conf - whether or not a server conf exists
     * software token - whether or not a software token exists AND it's status != TokenStatusInfo.NOT_INITIALIZED
     * @return
     */
    public boolean isSecurityServerInitialized() {
        boolean isGlobalConfInitialized = isGlobalConfInitialized();
        boolean isServerConfInitialized = isServerConfInitialized();
        boolean isSoftwareTokenInitialized = isSoftwareTokenInitialized();
        return isGlobalConfInitialized && isServerConfInitialized && isSoftwareTokenInitialized;
    }

    /**
     * Is global conf initialized -> it is if whe can find a Configuration anchor
     * @return
     */
    public boolean isGlobalConfInitialized() {
        boolean isGlobalConfInitialized = false;
        try {
            AnchorFile anchorFile = systemService.getAnchorFile();
            if (anchorFile != null) {
                isGlobalConfInitialized = true;
            }
        } catch (AnchorNotFoundException e) {
            log.info("Checking initialization status: could not find Global Configuration Anchor", e);
            // global conf does not exist - good!
        }
        return isGlobalConfInitialized;
    }

    /**
     * Is server conf initialized -> it is if whe can find one
     * @return
     */
    public boolean isServerConfInitialized() {
        boolean isServerConfInitialized = false;
        try {
            ServerConfType serverConfType = serverConfService.getServerConf();
            if (serverConfType != null) {
                isServerConfInitialized = true;
            }
        } catch (CodedException ce) { // -> this is X_MALFORMED_SERVERCONF, "Server conf is not initialized!"
            log.info("Checking initialization status: CodedException thrown when getting Server Conf", ce);
            // server conf does not exist - nice!
        }
        return isServerConfInitialized;
    }

    /**
     * Whether or not a software token exists AND it's status != TokenStatusInfo.NOT_INITIALIZED
     * @return
     */
    public boolean isSoftwareTokenInitialized() {
        boolean isSoftwareTokenInitialized = false;
        List<TokenInfo> tokens = tokenService.getAllTokens();
        Optional<TokenInfo> firstSoftwareToken = tokens.stream()
                .filter(tokenInfo -> tokenInfo.getId().equals(SOFTWARE_TOKEN_ID))
                .findFirst();

        if (firstSoftwareToken.isPresent()) {
            TokenInfo token = firstSoftwareToken.get();
            isSoftwareTokenInitialized = token.getStatus() != TokenStatusInfo.NOT_INITIALIZED;
        }
        return isSoftwareTokenInitialized;
    }
}
