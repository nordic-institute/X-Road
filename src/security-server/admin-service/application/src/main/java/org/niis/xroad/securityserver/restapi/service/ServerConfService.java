/**
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.conf.serverconf.model.ServerConfType;
import ee.ria.xroad.common.conf.serverconf.model.TspType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import lombok.extern.slf4j.Slf4j;
import org.hibernate.Hibernate;
import org.niis.xroad.securityserver.restapi.repository.ServerConfRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

import static ee.ria.xroad.common.ErrorCodes.X_MALFORMED_SERVERCONF;

/**
 * service class for handling serverconf
 */
@Slf4j
@Service
@Transactional
@PreAuthorize("isAuthenticated()")
public class ServerConfService {
    private final ServerConfRepository serverConfRepository;

    @Autowired
    public ServerConfService(ServerConfRepository serverConfRepository) {
        this.serverConfRepository = serverConfRepository;
    }

    /**
     * Get the Security Server's ServerConf
     * @return ServerConfType
     */
    public ServerConfType getServerConf() {
        return serverConfRepository.getServerConf();
    }

    /**
     * Get a server conf; an existing server conf will be returned if one exists. Otherwise
     * a new transient instance is returned.
     * @return
     */
    public ServerConfType getOrCreateServerConf() {
        ServerConfType serverConfType = getServerConfGracefully();
        if (serverConfType == null) {
            return new ServerConfType();
        }
        return serverConfType;
    }

    /**
     * Get the Security Server's {@link SecurityServerId}
     * @return SecurityServerId
     */
    public SecurityServerId.Conf getSecurityServerId() {
        ServerConfType serverConf = getServerConf();
        ClientId ownerId = serverConf.getOwner().getIdentifier();
        String serverCode = serverConf.getServerCode();
        return SecurityServerId.Conf.create(ownerId, serverCode);
    }

    /**
     * Returns owner's ClientId of this security server
     */
    public ClientId.Conf getSecurityServerOwnerId() {
        ServerConfType serverConfType = serverConfRepository.getServerConf();
        return serverConfType.getOwner().getIdentifier();
    }

    /**
     * Return a list of configured timestamping services
     * @return
     */
    public List<TspType> getConfiguredTimestampingServices() {
        ServerConfType serverConfType = serverConfRepository.getServerConf();
        List<TspType> tsp = serverConfType.getTsp();
        Hibernate.initialize(tsp);
        return tsp;
    }

    /**
     * Is server code initialized
     * @return
     */
    public boolean isServerCodeInitialized() {
        ServerConfType serverConfType = getServerConfGracefully();
        if (serverConfType != null) {
            return !StringUtils.isEmpty(serverConfType.getServerCode());
        }
        return false;
    }

    /**
     * Is server owner initialized
     * @return
     */
    public boolean isServerOwnerInitialized() {
        ServerConfType serverConfType = getServerConfGracefully();
        if (serverConfType != null) {
            return serverConfType.getOwner() != null;
        }
        return false;
    }

    /**
     * Save or update ServerConf
     * @return ServerConfType
     */
    public ServerConfType saveOrUpdate(ServerConfType serverConfType) {
        return serverConfRepository.saveOrUpdate(serverConfType);
    }

    /**
     * Helper to get the server conf object without failing if the server conf is not yet initialized
     * @return {@link ServerConfType} or <code>null</code> if not initialized
     */
    private ServerConfType getServerConfGracefully() {
        try {
            return getServerConf();
        } catch (CodedException ce) {
            log.info("ServerConfService#isServerConfInitialized: CodedException thrown when getting Server Conf", ce);
            if (ce.getFaultCode().equals(X_MALFORMED_SERVERCONF)) {
                return null;
            } else {
                throw ce;
            }
        }
    }
}
