/*
 * The MIT License
 *
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

package org.niis.xroad.cs.admin.core.entity.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.factory.Mappers;
import org.niis.xroad.cs.admin.api.converter.GenericUniDirectionalMapper;
import org.niis.xroad.cs.admin.api.domain.ServerClient;
import org.niis.xroad.cs.admin.core.entity.ServerClientEntity;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ServerClientMapper extends GenericUniDirectionalMapper<ServerClientEntity, ServerClient> {

    @Override
    default ServerClient toTarget(ServerClientEntity serverClientEntity) {
        final ServerClient dto = new ServerClient();
        dto.setServerOwner(serverClientEntity.getSecurityServer().getOwner().getName());
        dto.setServerCode(serverClientEntity.getSecurityServer().getServerCode());
        dto.setId(serverClientEntity.getId());
        final SecurityServerIdMapper securityServerIdMapper = Mappers.getMapper(SecurityServerIdMapper.class);
        dto.setServerId(securityServerIdMapper.toTarget(serverClientEntity.getSecurityServer().getServerId()));
        return dto;
    }

}
