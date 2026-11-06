/*
 * The MIT License
 *
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
package org.niis.xroad.cs.admin.core.service;

import ee.ria.xroad.common.identifier.SecurityServerId;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.exception.SecurityServerNotFoundException;
import org.niis.xroad.cs.admin.api.domain.SystemDidCheck;
import org.niis.xroad.cs.admin.api.service.SystemDidCheckService;
import org.niis.xroad.cs.admin.core.dataspace.DidBatchResolution;
import org.niis.xroad.cs.admin.core.dataspace.ParticipantValueValidator;
import org.niis.xroad.cs.admin.core.dataspace.RegisteredServer;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.DID_HELD_BY_ANOTHER_SERVER;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.NO_STORED_VALUE;

@Service
@RequiredArgsConstructor
public class SystemDidCheckServiceImpl implements SystemDidCheckService {

    private final SecurityServerRepository securityServerRepository;
    private final DidBatchResolution didBatchResolution;

    @Override
    public SystemDidCheck check(SecurityServerId serverId, String serverAddress, String systemDid) {
        var server = securityServerRepository.findBy(serverId)
                .orElseThrow(() -> new SecurityServerNotFoundException(serverId));
        if (server.getSystemDid() == null) {
            return SystemDidCheck.refused(NO_STORED_VALUE);
        }

        var validator = new ParticipantValueValidator(new RegisteredServer(serverAddress, serverId.getOwner(), Set.of()));
        var refusal = validator.validateSystemDid(systemDid);
        if (refusal.isPresent()) {
            return SystemDidCheck.refused(refusal.get());
        }
        if (securityServerRepository.existsBySystemDidAndIdNot(systemDid, server.getId())) {
            return SystemDidCheck.refused(DID_HELD_BY_ANOTHER_SERVER);
        }
        return resolve(systemDid);
    }

    private SystemDidCheck resolve(String systemDid) {
        var result = didBatchResolution.resolve(List.of(systemDid)).getFirst();
        return switch (result.status()) {
            case RESOLVED -> SystemDidCheck.RESOLVED;
            case NOT_RESOLVED -> SystemDidCheck.notResolved(result.resolution().reason());
            case RESOLVER_UNAVAILABLE -> SystemDidCheck.RESOLVER_UNAVAILABLE;
        };
    }
}
