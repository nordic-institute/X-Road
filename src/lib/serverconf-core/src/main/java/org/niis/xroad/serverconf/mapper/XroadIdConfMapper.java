/*
 * The MIT License
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
package org.niis.xroad.serverconf.mapper;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.GlobalGroupId;
import ee.ria.xroad.common.identifier.LocalGroupId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.identifier.XRoadId;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import org.niis.xroad.serverconf.converter.GenericUniDirectionalMapper;
import org.niis.xroad.serverconf.entity.ClientIdConfEntity;
import org.niis.xroad.serverconf.entity.GlobalGroupConfEntity;
import org.niis.xroad.serverconf.entity.LocalGroupConfEntity;
import org.niis.xroad.serverconf.entity.SecurityServerIdConfEntity;
import org.niis.xroad.serverconf.entity.ServiceIdConfEntity;
import org.niis.xroad.serverconf.entity.XRoadIdConfEntity;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Mapper
public interface XroadIdConfMapper extends GenericUniDirectionalMapper<XRoadIdConfEntity, XRoadId.Conf> {
    XroadIdConfMapper INSTANCE = Mappers.getMapper(XroadIdConfMapper.class);

    static XroadIdConfMapper get() {
        return INSTANCE;
    }

    default XRoadIdConfEntity toEntity(XRoadId.Conf source) {
        return switch (source) {
            case null -> null;
            case ClientId.Conf record -> toEntity(record);
            case GlobalGroupId.Conf record -> toEntity(record);
            case LocalGroupId.Conf record -> toEntity(record);
            case SecurityServerId.Conf record -> toEntity(record);
            case ServiceId.Conf record -> toEntity(record);
            default -> throw new IllegalArgumentException("Cannot map " + source.getClass());
        };
    }

    default XRoadIdConfEntity toEntity(XRoadId source) {
        return switch (source) {
            case null -> null;
            case ClientId record -> toEntity(record);
            case GlobalGroupId record -> toEntity(record);
            case LocalGroupId record -> toEntity(record);
            case SecurityServerId record -> toEntity(record);
            case ServiceId record -> toEntity(record);
            default -> throw new IllegalArgumentException("Cannot map " + source.getClass());
        };
    }

    @Override
    default XRoadId.Conf toTarget(XRoadIdConfEntity source) {
        return switch (source) {
            case null -> null;
            case ClientIdConfEntity entity -> toTarget(entity);
            case GlobalGroupConfEntity entity -> toTarget(entity);
            case LocalGroupConfEntity entity -> toTarget(entity);
            case SecurityServerIdConfEntity entity -> toTarget(entity);
            case ServiceIdConfEntity entity -> toTarget(entity);
            default -> throw new IllegalArgumentException("Cannot map " + source.getClass());
        };
    }

    default ClientId.Conf toTarget(ClientIdConfEntity source) {
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

    default GlobalGroupId.Conf toTarget(GlobalGroupConfEntity source) {
        if (source == null) {
            return null;
        }
        return GlobalGroupId.Conf.create(source.getXRoadInstance(),
                source.getGroupCode());
    }

    default LocalGroupId.Conf toTarget(LocalGroupConfEntity source) {
        if (source == null) {
            return null;
        }
        return LocalGroupId.Conf.create(source.getGroupCode());
    }

    default SecurityServerId.Conf toTarget(SecurityServerIdConfEntity source) {
        if (source == null) {
            return null;
        }
        return SecurityServerId.Conf.create(source.getXRoadInstance(),
                source.getMemberClass(),
                source.getMemberCode(),
                source.getServerCode());
    }

    default ServiceId.Conf toTarget(ServiceIdConfEntity source) {
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

    default ClientIdConfEntity toEntity(ClientId.Conf record) {
        if (record == null) {
            return null;
        }
        return ClientIdConfEntity.create(record);
    }

    default ClientIdConfEntity toEntity(ClientId record) {
        if (record == null) {
            return null;
        }
        return ClientIdConfEntity.create(record);
    }

    default GlobalGroupConfEntity toEntity(GlobalGroupId record) {
        if (record == null) {
            return null;
        }
        return GlobalGroupConfEntity.create(record);
    }

    default GlobalGroupConfEntity toEntity(GlobalGroupId.Conf record) {
        if (record == null) {
            return null;
        }
        return GlobalGroupConfEntity.create(record);
    }

    default LocalGroupConfEntity toEntity(LocalGroupId record) {
        if (record == null) {
            return null;
        }
        return LocalGroupConfEntity.create(record);
    }

    default LocalGroupConfEntity toEntity(LocalGroupId.Conf record) {
        if (record == null) {
            return null;
        }
        return LocalGroupConfEntity.create(record);
    }

    default SecurityServerIdConfEntity toEntity(SecurityServerId record) {
        if (record == null) {
            return null;
        }
        return SecurityServerIdConfEntity.create(record);
    }

    default SecurityServerIdConfEntity toEntity(SecurityServerId.Conf record) {
        if (record == null) {
            return null;
        }
        return SecurityServerIdConfEntity.create(record);
    }

    default ServiceIdConfEntity toEntity(ServiceId.Conf record) {
        if (record == null) {
            return null;
        }
        return ServiceIdConfEntity.create(record);
    }

    default ServiceIdConfEntity toEntity(ServiceId record) {
        if (record == null) {
            return null;
        }
        return ServiceIdConfEntity.create(record);
    }

    Collection<XRoadId.Conf> toTargets(Collection<XRoadIdConfEntity> entities);

    List<ServiceId.Conf> toServices(List<ServiceIdConfEntity> entities);

    Set<XRoadIdConfEntity> toEntities(Set<XRoadId.Conf> records);

    Set<XRoadIdConfEntity> toSubjects(Set<? extends XRoadId> records);
}
