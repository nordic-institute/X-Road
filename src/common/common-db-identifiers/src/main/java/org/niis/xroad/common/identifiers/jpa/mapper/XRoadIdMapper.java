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
package org.niis.xroad.common.identifiers.jpa.mapper;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.GlobalGroupId;
import ee.ria.xroad.common.identifier.LocalGroupId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.identifier.XRoadId;

import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.factory.Mappers;
import org.niis.xroad.common.core.mapper.GenericUniDirectionalMapper;
import org.niis.xroad.common.identifiers.jpa.ClientIdEntityFactory;
import org.niis.xroad.common.identifiers.jpa.entity.ClientIdEntity;
import org.niis.xroad.common.identifiers.jpa.entity.GlobalGroupIdEntity;
import org.niis.xroad.common.identifiers.jpa.entity.LocalGroupIdEntity;
import org.niis.xroad.common.identifiers.jpa.entity.SecurityServerIdEntity;
import org.niis.xroad.common.identifiers.jpa.entity.ServiceIdEntity;
import org.niis.xroad.common.identifiers.jpa.entity.XRoadIdEntity;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Mapper(componentModel = MappingConstants.ComponentModel.JAKARTA_CDI)
public interface XRoadIdMapper extends GenericUniDirectionalMapper<XRoadIdEntity, XRoadId.Conf> {
    XRoadIdMapper INSTANCE = Mappers.getMapper(XRoadIdMapper.class);

    static XRoadIdMapper get() {
        return INSTANCE;
    }

    default XRoadIdEntity toEntity(XRoadId.Conf source) {
        return switch (source) {
            case null -> null;
            case ClientId.Conf domain -> toEntity(domain);
            case GlobalGroupId.Conf domain -> toEntity(domain);
            case LocalGroupId.Conf domain -> toEntity(domain);
            case SecurityServerId.Conf domain -> toEntity(domain);
            case ServiceId.Conf domain -> toEntity(domain);
            default -> throw new IllegalArgumentException("Cannot map " + source.getClass());
        };
    }

    default XRoadIdEntity toEntity(XRoadId source) {
        return switch (source) {
            case null -> null;
            case ClientId domain -> toEntity(domain);
            case GlobalGroupId domain -> toEntity(domain);
            case LocalGroupId domain -> toEntity(domain);
            case SecurityServerId domain -> toEntity(domain);
            case ServiceId domain -> toEntity(domain);
            default -> throw new IllegalArgumentException("Cannot map " + source.getClass());
        };
    }

    @Override
    default XRoadId.Conf toTarget(XRoadIdEntity source) {
        return switch (source) {
            case null -> null;
            case ClientIdEntity entity -> toTarget(entity);
            case GlobalGroupIdEntity entity -> toTarget(entity);
            case LocalGroupIdEntity entity -> toTarget(entity);
            case SecurityServerIdEntity entity -> toTarget(entity);
            case ServiceIdEntity entity -> toTarget(entity);
            default -> throw new IllegalArgumentException("Cannot map " + source.getClass());
        };
    }

    default ClientId.Conf toTarget(ClientIdEntity source) {
        if (source == null) {
            return null;
        }
        return switch (source.getObjectType()) {
            case null -> null;
            case MEMBER -> ClientId.Conf.create(
                    source.getXRoadInstance(),
                    source.getMemberClass(),
                    source.getMemberCode());
            case SUBSYSTEM -> ClientId.Conf.create(
                    source.getXRoadInstance(),
                    source.getMemberClass(),
                    source.getMemberCode(),
                    source.getSubsystemCode());
            default -> throw new IllegalArgumentException("Cannot map " + source.getClass());
        };
    }

    default GlobalGroupId.Conf toTarget(GlobalGroupIdEntity source) {
        if (source == null) {
            return null;
        }
        return GlobalGroupId.Conf.create(source.getXRoadInstance(),
                source.getGroupCode());
    }

    default LocalGroupId.Conf toTarget(LocalGroupIdEntity source) {
        if (source == null) {
            return null;
        }
        return LocalGroupId.Conf.create(source.getGroupCode());
    }

    default SecurityServerId.Conf toTarget(SecurityServerIdEntity source) {
        if (source == null) {
            return null;
        }
        return SecurityServerId.Conf.create(source.getXRoadInstance(),
                source.getMemberClass(),
                source.getMemberCode(),
                source.getServerCode());
    }

    default ServiceId.Conf toTarget(ServiceIdEntity source) {
        if (source == null) {
            return null;
        }
        return ServiceId.Conf.create(source.getXRoadInstance(),
                source.getMemberClass(),
                source.getMemberCode(),
                source.getSubsystemCode(),
                source.getServiceCode(),
                source.getServiceVersion());
    }

    default ClientIdEntity toEntity(ClientId.Conf domain) {
        if (domain == null) {
            return null;
        }
        return ClientIdEntityFactory.create(domain);
    }

    default ClientIdEntity toEntity(ClientId domain) {
        if (domain == null) {
            return null;
        }
        return ClientIdEntityFactory.create(domain);
    }

    default GlobalGroupIdEntity toEntity(GlobalGroupId domain) {
        if (domain == null) {
            return null;
        }
        return GlobalGroupIdEntity.create(domain);
    }

    default GlobalGroupIdEntity toEntity(GlobalGroupId.Conf domain) {
        if (domain == null) {
            return null;
        }
        return GlobalGroupIdEntity.create(domain);
    }

    default LocalGroupIdEntity toEntity(LocalGroupId domain) {
        if (domain == null) {
            return null;
        }
        return LocalGroupIdEntity.create(domain);
    }

    default LocalGroupIdEntity toEntity(LocalGroupId.Conf domain) {
        if (domain == null) {
            return null;
        }
        return LocalGroupIdEntity.create(domain);
    }

    default SecurityServerIdEntity toEntity(SecurityServerId domain) {
        if (domain == null) {
            return null;
        }
        return SecurityServerIdEntity.create(domain);
    }

    default SecurityServerIdEntity toEntity(SecurityServerId.Conf domain) {
        if (domain == null) {
            return null;
        }
        return SecurityServerIdEntity.create(domain);
    }

    default ServiceIdEntity toEntity(ServiceId.Conf domain) {
        if (domain == null) {
            return null;
        }
        return ServiceIdEntity.create(domain);
    }

    default ServiceIdEntity toEntity(ServiceId domain) {
        if (domain == null) {
            return null;
        }
        return ServiceIdEntity.create(domain);
    }

    Collection<XRoadId.Conf> toTargets(Collection<XRoadIdEntity> entities);

    List<ServiceId.Conf> toServices(List<ServiceIdEntity> entities);

    Set<XRoadIdEntity> toEntities(Set<? extends XRoadId> domains);

    Set<XRoadIdEntity> toSubjects(Set<? extends XRoadId> domains);
}
