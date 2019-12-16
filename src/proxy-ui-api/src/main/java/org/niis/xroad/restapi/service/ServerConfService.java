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
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.repository.ServerConfRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
     * @throws MalformedServerConfException serverconf cannot be retrieved (e.g. security server is not initialized)
     */
    public ServerConfType getServerConf() throws MalformedServerConfException {
        ServerConfType serverConf = null;
        try {
            serverConf = serverConfRepository.getServerConf();
        } catch (CodedException e) {
            if (isCausedByMalformedServerConf(e)) {
                throw new MalformedServerConfException(e);
            } else {
                throw e;
            }
        }
        return serverConf;
    }

    /**
     * Get the Security Server's {@link SecurityServerId}
     * @return SecurityServerId
     * @throws MalformedServerConfException
     */
    public SecurityServerId getSecurityServerId() throws MalformedServerConfException {
        ServerConfType serverConf = getServerConf();
        ClientId ownerId = serverConf.getOwner().getIdentifier();
        String serverCode = serverConf.getServerCode();
        return SecurityServerId.create(ownerId.getXRoadInstance(),
                ownerId.getMemberClass(), ownerId.getMemberCode(), serverCode);
    }

    static boolean isCausedByMalformedServerConf(CodedException e) {
        return ERROR_MALFORMED_SERVERCONF.equals(e.getFaultCode());
    }

    static final String ERROR_MALFORMED_SERVERCONF = X_MALFORMED_SERVERCONF;

    /**
     * Thrown if serverconf cannot be retrieved (e.g. security server is not initialized)
     */
    public static class MalformedServerConfException extends ServiceException {
        public static final String MALFORMED_SERVERCONF = "malformed_serverconf";

        public MalformedServerConfException(Throwable t) {
            super(t, new ErrorDeviation(MALFORMED_SERVERCONF));
        }
    }
}
