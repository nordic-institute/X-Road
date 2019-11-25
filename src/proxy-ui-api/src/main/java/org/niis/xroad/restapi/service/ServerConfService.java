/**
 * The MIT License
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
package org.niis.xroad.restapi.service;

import ee.ria.xroad.common.conf.serverconf.model.ServerConfType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.repository.ServerConfRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for accessing ServerConf related data
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
public class ServerConfService {

    private final ServerConfRepository serverConfRepository;

    /**
     * Constructor
     * @param serverConfRepository
     */
    @Autowired
    public ServerConfService(ServerConfRepository serverConfRepository) {
        this.serverConfRepository = serverConfRepository;
    }

    /**
     * Returns SecurityServerId of this security server
     */
    public SecurityServerId getSecurityServerId() {
        ServerConfType serverConfType = serverConfRepository.getServerConf();
        ClientId ownerId = getSecurityServerOwnerId();
        SecurityServerId securityServerId = SecurityServerId.create(ownerId, serverConfType.getServerCode());
        return securityServerId;
    }

    /**
     * Returns owner's ClientId of this security server
     */
    public ClientId getSecurityServerOwnerId() {
        ServerConfType serverConfType = serverConfRepository.getServerConf();
        return serverConfType.getOwner().getIdentifier();
    }
}
