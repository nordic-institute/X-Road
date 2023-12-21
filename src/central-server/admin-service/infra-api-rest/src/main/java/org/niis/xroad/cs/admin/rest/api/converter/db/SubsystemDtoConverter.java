/*
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
package org.niis.xroad.cs.admin.rest.api.converter.db;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.cs.admin.api.domain.ClientId;
import org.niis.xroad.cs.admin.api.domain.ServerClient;
import org.niis.xroad.cs.admin.api.domain.Subsystem;
import org.niis.xroad.cs.admin.api.service.SecurityServerService;
import org.niis.xroad.cs.admin.api.service.SubsystemService;
import org.niis.xroad.cs.openapi.model.SubsystemDto;
import org.niis.xroad.cs.openapi.model.UsedSecurityServersDto;
import org.niis.xroad.restapi.converter.DtoConverter;
import org.springframework.stereotype.Service;

import static ee.ria.xroad.common.util.Fn.self;

@Service
@RequiredArgsConstructor
public class SubsystemDtoConverter extends DtoConverter<Subsystem, SubsystemDto> {

    private final SubsystemService subsystemService;

    private final SecurityServerService securityServerService;

    private final ClientIdDtoConverter clientIdDtoConverter;

    @Override
    public SubsystemDto toDto(Subsystem source) {
        return self(new SubsystemDto(), self -> {
            self.setSubsystemId(clientIdDtoConverter.toDto(source.getIdentifier()));
            self.setUsedSecurityServers(source.getServerClients().stream().map(serverClient -> {
                UsedSecurityServersDto usedSecurityServersDto = new UsedSecurityServersDto();
                usedSecurityServersDto.setServerCode(serverClient.getServerCode());
                usedSecurityServersDto.setServerOwner(serverClient.getServerOwner());

                var securityServerRegStatus = resolveSecurityServerStatus(serverClient, source.getIdentifier());
                usedSecurityServersDto.setStatus(securityServerRegStatus);
                return usedSecurityServersDto;
            }).toList());
        });
    }

    private String resolveSecurityServerStatus(ServerClient serverClient, ClientId clientId) {
        if (!serverClient.isEnabled()) {
            return "DISABLED";
        }
        var status = securityServerService.findSecurityServerClientRegistrationStatus(serverClient.getServerId(), clientId);
        return status != null ? status.name() : null;
    }

    @Override
    public Subsystem fromDto(SubsystemDto source) {
        return subsystemService
                .findByIdentifier(clientIdDtoConverter
                        .fromDto(source.getSubsystemId())).orElse(null);
    }
}
