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

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import org.niis.xroad.serverconf.converter.GenericUniDirectionalMapper;
import org.niis.xroad.serverconf.entity.ServiceTypeEntity;
import org.niis.xroad.serverconf.model.ServiceType;

import java.util.List;
import java.util.stream.Collectors;

@Mapper
public interface ServiceTypeMapper extends GenericUniDirectionalMapper<ServiceTypeEntity, ServiceType> {
    ServiceTypeMapper INSTANCE = Mappers.getMapper(ServiceTypeMapper.class);
    static ServiceTypeMapper get() {
        return INSTANCE;
    }

    @Override
    default ServiceType toTarget(ServiceTypeEntity entity) {
        if (entity == null) {
            return null;
        }

        ServiceType serviceType = new ServiceType();

        serviceType.setId(entity.getId());
        serviceType.setServiceDescription(ServiceDescriptionBaseTypeMapper.get().toTarget(entity.getServiceDescription()));
        serviceType.setServiceCode(entity.getServiceCode());
        serviceType.setServiceVersion(entity.getServiceVersion());
        serviceType.setTitle(entity.getTitle());
        serviceType.setUrl(entity.getUrl());
        serviceType.setSslAuthentication(entity.getSslAuthentication());
        if (entity.getTimeout() != null) {
            serviceType.setTimeout(entity.getTimeout());
        }
        serviceType.getEndpoints().addAll(EndpointTypeMapper.get().toTargets(
                entity.getServiceDescription().getClient().getEndpoint().stream()
                .filter(endpointTypeEntity -> endpointTypeEntity.getServiceCode().equals(entity.getServiceCode()))
                .collect(Collectors.toList())
                )
        );

        return serviceType;
    }

    List<ServiceType> toTargets(List<ServiceTypeEntity> entities);
}
