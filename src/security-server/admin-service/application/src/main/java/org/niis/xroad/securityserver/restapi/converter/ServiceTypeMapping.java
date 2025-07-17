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
package org.niis.xroad.securityserver.restapi.converter;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceTypeDto;
import org.niis.xroad.serverconf.model.DescriptionType;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Mapping between ServiceType in api (enum) and model (DescriptionType)
 */
@Getter
@RequiredArgsConstructor
public enum ServiceTypeMapping {
    WSDL(DescriptionType.WSDL, ServiceTypeDto.WSDL),
    OPENAPI3(DescriptionType.OPENAPI3, ServiceTypeDto.OPENAPI3),
    REST(DescriptionType.REST, ServiceTypeDto.REST);

    private final DescriptionType descriptionType;
    private final ServiceTypeDto serviceTypeDto;

    private static final Map<DescriptionType, ServiceTypeDto> DESCRIPTION_TO_SERVICE_MAP =
            Stream.of(values()).collect(Collectors.toMap(ServiceTypeMapping::getDescriptionType, ServiceTypeMapping::getServiceTypeDto));

    private static final Map<ServiceTypeDto, DescriptionType> SERVICE_TO_DESCRIPTION_MAP =
            Stream.of(values()).collect(Collectors.toMap(ServiceTypeMapping::getServiceTypeDto, ServiceTypeMapping::getDescriptionType));

    public static ServiceTypeDto map(DescriptionType descriptionType) {
        return DESCRIPTION_TO_SERVICE_MAP.get(descriptionType);
    }

    public static DescriptionType map(ServiceTypeDto serviceType) {
        return SERVICE_TO_DESCRIPTION_MAP.get(serviceType);
    }
}
