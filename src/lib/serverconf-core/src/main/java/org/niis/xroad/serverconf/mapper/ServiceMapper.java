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
package org.niis.xroad.serverconf.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;
import org.niis.xroad.serverconf.converter.GenericUniDirectionalMapper;
import org.niis.xroad.serverconf.entity.ServiceEntity;
import org.niis.xroad.serverconf.model.Service;

import java.util.List;
import java.util.stream.Collectors;

@Mapper
public interface ServiceMapper extends GenericUniDirectionalMapper<ServiceEntity, Service> {
    ServiceMapper INSTANCE = Mappers.getMapper(ServiceMapper.class);
    static ServiceMapper get() {
        return INSTANCE;
    }

    @Override
    default Service toTarget(ServiceEntity entity) {
        if (entity == null) {
            return null;
        }

        Service service = new Service();

        service.setId(entity.getId());
        service.setServiceDescription(ServiceDescriptionBaseMapper.get().toTarget(entity.getServiceDescription()));
        service.setServiceCode(entity.getServiceCode());
        service.setServiceVersion(entity.getServiceVersion());
        service.setTitle(entity.getTitle());
        service.setUrl(entity.getUrl());
        service.setSslAuthentication(entity.getSslAuthentication());
        if (entity.getTimeout() != null) {
            service.setTimeout(entity.getTimeout());
        }
        service.getEndpoints().addAll(EndpointMapper.get().toTargets(
                entity.getServiceDescription().getClient().getEndpoints().stream()
                .filter(endpointEntity -> endpointEntity.getServiceCode().equals(entity.getServiceCode()))
                .collect(Collectors.toList())
                )
        );

        return service;
    }

    List<Service> toTargets(List<ServiceEntity> entities);
}
