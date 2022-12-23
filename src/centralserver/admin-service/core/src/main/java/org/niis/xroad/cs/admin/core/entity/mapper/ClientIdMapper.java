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
package org.niis.xroad.cs.admin.core.entity.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.niis.xroad.cs.admin.api.converter.GenericBiDirectionalMapper;
import org.niis.xroad.cs.admin.api.domain.ClientId;
import org.niis.xroad.cs.admin.api.domain.MemberId;
import org.niis.xroad.cs.admin.api.domain.SubsystemId;
import org.niis.xroad.cs.admin.core.entity.ClientIdEntity;
import org.niis.xroad.cs.admin.core.entity.MemberIdEntity;
import org.niis.xroad.cs.admin.core.entity.SubsystemIdEntity;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ClientIdMapper extends GenericBiDirectionalMapper<ClientIdEntity, ClientId> {

    @Override
    default ClientId toTarget(ClientIdEntity source) {
        if (source == null) {
            return null;
        }
        if (source instanceof MemberIdEntity) {
            return toMemberId((MemberIdEntity) source);
        }
        if (source instanceof SubsystemIdEntity) {
            return toSubsystemId((SubsystemIdEntity) source);
        }

        throw new IllegalArgumentException("Cannot map " + source.getClass());
    }


    @Override
    default ClientIdEntity fromTarget(ClientId source) {
        if (source == null) {
            return null;
        }
        if (source instanceof MemberId) {
            return fromMemberId((MemberId) source);
        }
        if (source instanceof SubsystemId) {
            return fromSubsystemId((SubsystemId) source);
        }

        throw new IllegalArgumentException("Cannot map " + source.getClass());
    }

    default MemberIdEntity fromMemberId(MemberId source) {
        return MemberIdEntity.create(source.getXRoadInstance(), source.getMemberClass(), source.getMemberCode());
    }

    MemberId toMemberId(MemberIdEntity source);

    default SubsystemIdEntity fromSubsystemId(SubsystemId source) {
        return SubsystemIdEntity.create(
                source.getXRoadInstance(),
                source.getMemberClass(),
                source.getMemberCode(),
                source.getSubsystemCode());
    }

    SubsystemId toSubsystemId(SubsystemIdEntity source);
}
