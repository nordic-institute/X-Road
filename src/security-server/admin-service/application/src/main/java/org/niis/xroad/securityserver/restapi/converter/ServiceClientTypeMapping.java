/*
 * The MIT License
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

import ee.ria.xroad.common.identifier.XRoadObjectType;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceClientTypeDto;

import java.util.Arrays;
import java.util.Optional;

/**
 * Mapping between ServiceClientTypeDto in api (enum) and model (XRoadObjectType)
 */
@Getter
@RequiredArgsConstructor
public enum ServiceClientTypeMapping {
    SUBSYSTEM(XRoadObjectType.SUBSYSTEM, ServiceClientTypeDto.SUBSYSTEM),
    LOCALGROUP(XRoadObjectType.LOCALGROUP, ServiceClientTypeDto.LOCALGROUP),
    GLOBALGROUP(XRoadObjectType.GLOBALGROUP, ServiceClientTypeDto.GLOBALGROUP);

    private final XRoadObjectType xRoadObjectType;
    private final ServiceClientTypeDto serviceClientTypeDto;

    /**
     * Return matching ServiceClientTypeDto, if any
     * @param xRoadObjectType
     * @return
     */
    public static Optional<ServiceClientTypeDto> map(XRoadObjectType xRoadObjectType) {
        return getFor(xRoadObjectType).map(ServiceClientTypeMapping::getServiceClientTypeDto);
    }

    /**
     * Return matching XRoadObjectTypeDto, if any
     * @param serviceClientTypeDto
     * @return
     */
    public static Optional<XRoadObjectType> map(ServiceClientTypeDto serviceClientTypeDto) {
        return getFor(serviceClientTypeDto).map(ServiceClientTypeMapping::getXRoadObjectType);
    }

    /**
     * return ServiceClientTypeMapping matching the given xRoadObjectType, if any
     * @param xRoadObjectType
     * @return
     */
    public static Optional<ServiceClientTypeMapping> getFor(XRoadObjectType xRoadObjectType) {
        return Arrays.stream(values())
                .filter(mapping -> mapping.xRoadObjectType.equals(xRoadObjectType))
                .findFirst();
    }

    /**
     * return ServiceClientTypeMapping matching the given serviceClientType, if any
     * @param serviceClientTypeDto
     * @return
     */
    public static Optional<ServiceClientTypeMapping> getFor(ServiceClientTypeDto serviceClientTypeDto) {
        return Arrays.stream(values())
                .filter(mapping -> mapping.serviceClientTypeDto.equals(serviceClientTypeDto))
                .findFirst();
    }

}
