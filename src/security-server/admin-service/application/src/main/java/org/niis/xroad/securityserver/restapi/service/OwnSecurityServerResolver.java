/*
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

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.securityserver.restapi.repository.ServerConfRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

import static org.niis.xroad.common.core.exception.ErrorCode.MALFORMED_SERVERCONF;

/**
 * This Security Server as GlobalConf knows it: owner member, server id and registered address.
 *
 * <p>Reads serverconf through the repository, not {@link ServerConfService}, because callers include
 * the unauthenticated scheduled provisioning worker, which the service's authentication guard would
 * reject. Each lookup runs in its own short read-only transaction, so callers do not hold a database
 * connection across remote calls. An uninitialized owner, a GlobalConf that is not yet downloaded, or
 * a registration that has not landed all read as empty — normal states before and during
 * registration, not errors.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OwnSecurityServerResolver {

    private final ServerConfRepository serverConfRepository;
    private final GlobalConfProvider globalConfProvider;

    @Transactional(readOnly = true)
    public Optional<ClientId> owner() {
        try {
            return Optional.ofNullable(serverConfRepository.getServerConf().getOwner())
                    .map(owner -> (ClientId) owner.getIdentifier());
        } catch (XrdRuntimeException e) {
            if (MALFORMED_SERVERCONF.code().equals(e.getErrorCode())) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Transactional(readOnly = true)
    public Optional<SecurityServerId.Conf> serverId() {
        return owner().map(owner -> SecurityServerId.Conf.create(owner, serverConfRepository.getServerConf().getServerCode()));
    }

    @Transactional(readOnly = true)
    public Optional<String> registeredAddress() {
        return serverId().flatMap(serverId -> {
            try {
                return Optional.ofNullable(globalConfProvider.getSecurityServerAddress(serverId))
                        .filter(address -> !address.isBlank());
            } catch (XrdRuntimeException e) {
                log.debug("GlobalConf not readable yet, registered address of {} unknown", serverId, e);
                return Optional.empty();
            }
        });
    }
}
