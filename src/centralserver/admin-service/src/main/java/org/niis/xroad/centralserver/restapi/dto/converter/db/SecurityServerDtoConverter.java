/**
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
package org.niis.xroad.centralserver.restapi.dto.converter.db;

import ee.ria.xroad.common.identifier.SecurityServerId;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.centralserver.openapi.model.SecurityServerDto;
import org.niis.xroad.centralserver.openapi.model.SecurityServerIdDto;
import org.niis.xroad.centralserver.restapi.dto.converter.DtoConverter;
import org.niis.xroad.centralserver.restapi.dto.converter.model.SecurityServerIdDtoConverter;
import org.niis.xroad.centralserver.restapi.entity.SecurityServer;
import org.niis.xroad.centralserver.restapi.entity.XRoadMember;
import org.niis.xroad.centralserver.restapi.repository.SecurityServerRepository;
import org.niis.xroad.centralserver.restapi.repository.XRoadMemberRepository;
import org.niis.xroad.centralserver.restapi.service.exception.ErrorMessage;
import org.niis.xroad.centralserver.restapi.service.exception.NotFoundException;
import org.niis.xroad.restapi.converter.SecurityServerIdConverter;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.util.function.Supplier;

import static ee.ria.xroad.common.util.Fn.self;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityServerDtoConverter extends DtoConverter<SecurityServer, SecurityServerDto> {

    private final ZoneOffset dtoZoneOffset;

    private final SecurityServerRepository securityServerRepository;
    private final XRoadMemberRepository xRoadMemberRepository;

    private final SecurityServerIdConverter securityServerIdConverter;
    private final SecurityServerIdDtoConverter securityServerIdDtoConverter;

    @Override
    public SecurityServerDto toDto(SecurityServer source) {
        return self(new SecurityServerDto(), self -> {
            self(source.getServerId(), securityServerId -> {
                self.id(securityServerIdConverter.convert(securityServerId));
                self.xroadId(securityServerIdDtoConverter.toDto(securityServerId));
            });
            self.ownerName(source.getOwner().getName());
            self.serverAddress(source.getAddress());
            self.createdAt(source.getCreatedAt().atOffset(dtoZoneOffset));
            self.updatedAt(source.getUpdatedAt().atOffset(dtoZoneOffset));
        });
    }

    @Override
    public SecurityServer fromDto(SecurityServerDto source) {
        SecurityServerIdDto securityServerIdDto = source.getXroadId();
        SecurityServerId serverId = securityServerIdConverter.convert(securityServerIdDto);
        XRoadMember owner = xRoadMemberRepository.findMember(serverId.getOwner())
                .getOrElseThrow(() -> new NotFoundException(ErrorMessage.MEMBER_NOT_FOUND));
        String serverCode = securityServerIdDto.getServerCode();

        Supplier<SecurityServer> newMemberClass = () -> self(new SecurityServer(owner, serverCode), self -> {
            self.setAddress(source.getServerAddress());
        });

        return securityServerRepository
                .findByOwnerAndServerCode(owner, serverCode)
                .getOrElse(newMemberClass);
    }
}
