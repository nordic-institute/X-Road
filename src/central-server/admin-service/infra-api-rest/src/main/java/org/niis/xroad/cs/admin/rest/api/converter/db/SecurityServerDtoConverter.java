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
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.cs.admin.api.domain.SecurityServer;
import org.niis.xroad.cs.admin.rest.api.converter.model.SecurityServerIdDtoConverter;
import org.niis.xroad.cs.openapi.model.SecurityServerDto;
import org.niis.xroad.restapi.converter.UniDirectionalDtoConverter;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;

@Slf4j
@Service
@RequiredArgsConstructor
public class SecurityServerDtoConverter extends UniDirectionalDtoConverter<SecurityServer, SecurityServerDto> {
    private final ZoneOffset dtoZoneOffset;

    private final SecurityServerIdDtoConverter securityServerIdDtoConverter;

    @Override
    public SecurityServerDto toDto(SecurityServer source) {
        return new SecurityServerDto()
                .serverId(securityServerIdDtoConverter.toDto(source.getServerId()))
                .ownerName(source.getOwner().getName())
                .serverAddress(source.getAddress())
                .createdAt(source.getCreatedAt().atOffset(dtoZoneOffset))
                .updatedAt(source.getUpdatedAt().atOffset(dtoZoneOffset));
    }


}
