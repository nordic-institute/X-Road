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
            case ClientId.Conf domain -> toEntity(domain);
            case GlobalGroupId.Conf domain -> toEntity(domain);
            case LocalGroupId.Conf domain -> toEntity(domain);
            case SecurityServerId.Conf domain -> toEntity(domain);
            case ServiceId.Conf domain -> toEntity(domain);
            default -> throw new IllegalArgumentException("Cannot map " + source.getClass());
        };
    }

    default XRoadIdConfEntity toEntity(XRoadId source) {
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

    default ClientIdConfEntity toEntity(ClientId.Conf domain) {
        if (domain == null) {
            return null;
        }
        return ClientIdConfEntity.create(domain);
    }

    default ClientIdConfEntity toEntity(ClientId domain) {
        if (domain == null) {
            return null;
        }
        return ClientIdConfEntity.create(domain);
    }

    default GlobalGroupConfEntity toEntity(GlobalGroupId domain) {
        if (domain == null) {
            return null;
        }
        return GlobalGroupConfEntity.create(domain);
    }

    default GlobalGroupConfEntity toEntity(GlobalGroupId.Conf domain) {
        if (domain == null) {
            return null;
        }
        return GlobalGroupConfEntity.create(domain);
    }

    default LocalGroupConfEntity toEntity(LocalGroupId domain) {
        if (domain == null) {
            return null;
        }
        return LocalGroupConfEntity.create(domain);
    }

    default LocalGroupConfEntity toEntity(LocalGroupId.Conf domain) {
        if (domain == null) {
            return null;
        }
        return LocalGroupConfEntity.create(domain);
    }

    default SecurityServerIdConfEntity toEntity(SecurityServerId domain) {
        if (domain == null) {
            return null;
        }
        return SecurityServerIdConfEntity.create(domain);
    }

    default SecurityServerIdConfEntity toEntity(SecurityServerId.Conf domain) {
        if (domain == null) {
            return null;
        }
        return SecurityServerIdConfEntity.create(domain);
    }

    default ServiceIdConfEntity toEntity(ServiceId.Conf domain) {
        if (domain == null) {
            return null;
        }
        return ServiceIdConfEntity.create(domain);
    }

    default ServiceIdConfEntity toEntity(ServiceId domain) {
        if (domain == null) {
            return null;
        }
        return ServiceIdConfEntity.create(domain);
    }

    Collection<XRoadId.Conf> toTargets(Collection<XRoadIdConfEntity> entities);

    List<ServiceId.Conf> toServices(List<ServiceIdConfEntity> entities);

    Set<XRoadIdConfEntity> toEntities(Set<XRoadId.Conf> domains);

    Set<XRoadIdConfEntity> toSubjects(Set<? extends XRoadId> domains);
}
