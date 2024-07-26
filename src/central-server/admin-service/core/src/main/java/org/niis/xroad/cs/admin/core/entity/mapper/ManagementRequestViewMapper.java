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
package org.niis.xroad.cs.admin.core.entity.mapper;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.Named;
import org.niis.xroad.cs.admin.api.converter.GenericUniDirectionalMapper;
import org.niis.xroad.cs.admin.api.domain.ManagementRequestView;
import org.niis.xroad.cs.admin.api.domain.MemberId;
import org.niis.xroad.cs.admin.api.domain.SubsystemId;
import org.niis.xroad.cs.admin.core.converter.CertificateConverter;
import org.niis.xroad.cs.admin.core.entity.ManagementRequestViewEntity;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING, uses = CertificateConverter.class)
public interface ManagementRequestViewMapper extends GenericUniDirectionalMapper<ManagementRequestViewEntity, ManagementRequestView> {

    @Override
    @Mapping(target = "certificateDetails", source = "authCert")
    @Mapping(target = "status", source = "requestProcessingStatus")
    @Mapping(target = "type", source = "managementRequestType")
    @Mapping(target = "securityServerId", source = ".", qualifiedByName = "toSecurityServerId")
    @Mapping(target = "clientId", source = ".", qualifiedByName = "toClientId")
    ManagementRequestView toTarget(ManagementRequestViewEntity managementRequestViewEntity);

    @Named("toSecurityServerId")
    static SecurityServerId toSecurityServerId(ManagementRequestViewEntity entity) {
        return SecurityServerId.Conf.create(entity.getXroadInstance(), entity.getMemberClass(),
                entity.getMemberCode(), entity.getServerCode());
    }

    @Named("toClientId")
    static ClientId toClientId(ManagementRequestViewEntity entity) {
        return switch (entity.getClientType()) {
            case MEMBER -> MemberId.create(entity.getClientXroadInstance(),
                    entity.getClientMemberClass(),
                    entity.getClientMemberCode());
            case SUBSYSTEM -> SubsystemId.create(entity.getClientXroadInstance(),
                    entity.getClientMemberClass(),
                    entity.getClientMemberCode(),
                    entity.getClientSubsystemCode());
            case null, default -> null;
        };
    }
}
