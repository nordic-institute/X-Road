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
package org.niis.xroad.centralserver.restapi.converter;

import org.niis.xroad.centralserver.openapi.model.PagedSecurityServers;
import org.niis.xroad.centralserver.openapi.model.PagingMetadata;
import org.niis.xroad.centralserver.openapi.model.SecurityServerId;
import org.niis.xroad.centralserver.openapi.model.XRoadId;
import org.niis.xroad.centralserver.restapi.dto.SecurityServerDto;
import org.springframework.data.domain.Page;

import java.time.ZoneOffset;
import java.util.stream.Collectors;

public class SecurityServerConverter {

    public PagedSecurityServers convert(Page<SecurityServerDto> servers) {
        return new PagedSecurityServers()
                .pagingMetadata(new PagingMetadata()
                        .totalItems((int) servers.getTotalElements())
                        .items(servers.getNumberOfElements())
                        .limit(servers.getSize())
                        .offset(servers.getNumber())
                )
                .items(servers.getContent().stream()
                        .map(dto -> new org.niis.xroad.centralserver.openapi.model.SecurityServer()
                                .id(dto.getServerId().toShortString(':'))
                                .xroadId(getSecurityServerId(dto))
                                .ownerName(dto.getOwnerName())
                                .serverAddress(dto.getServerAddress())
                                .updatedAt(dto.getUpdatedAt().atOffset(ZoneOffset.UTC))
                                .createdAt(dto.getCreatedAt().atOffset(ZoneOffset.UTC)))
                        .collect(Collectors.toUnmodifiableList()));
    }

    //todo should use xroad identifier converter (tbd)
    private SecurityServerId getSecurityServerId(SecurityServerDto serverDto) {
        var id = serverDto.getServerId();
        var result = new SecurityServerId()
                .memberClass(id.getMemberClass())
                .memberCode(id.getMemberCode())
                .serverCode(id.getServerCode());
        result.instanceId(id.getXRoadInstance()).type(XRoadId.TypeEnum.SERVER);
        return result;
    }

}
