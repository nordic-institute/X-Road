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
package org.niis.xroad.centralserver.restapi.converter.model;

import ee.ria.xroad.common.util.NoCoverage;

import lombok.extern.slf4j.Slf4j;
import org.mapstruct.Mapper;
import org.mapstruct.ValueMapping;
import org.mapstruct.ValueMappings;
import org.niis.xroad.centralserver.openapi.model.ManagementRequestTypeDto;
import org.niis.xroad.centralserver.restapi.domain.ManagementRequestType;
import org.niis.xroad.centralserver.restapi.dto.converter.DtoConverter;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

@Mapper
public interface ManagementRequestDtoTypeConverter {

    @Slf4j
    @Converter
    @NoCoverage
    class Impl extends ManagementRequestDtoTypeConverterImpl implements AttributeConverter<
            ManagementRequestTypeDto,
            ManagementRequestType
            > {

        private static final Impl IMPLEMENTATION = new Impl();

        @Override
        public ManagementRequestType convertToDatabaseColumn(ManagementRequestTypeDto attribute) {
            return IMPLEMENTATION.convert(attribute);
        }

        @Override
        public ManagementRequestTypeDto convertToEntityAttribute(ManagementRequestType dbData) {
            return IMPLEMENTATION.convert(dbData);
        }
    }

    @Slf4j
    @org.springframework.stereotype.Service
    @NoCoverage
    class Service extends DtoConverter<ManagementRequestType, ManagementRequestTypeDto> {
        private static final Impl IMPLEMENTATION = new Impl();

        @Override
        public ManagementRequestTypeDto toDto(ManagementRequestType source) {
            return IMPLEMENTATION.convert(source);
        }

        @Override
        public ManagementRequestType fromDto(ManagementRequestTypeDto source) {
            return IMPLEMENTATION.convert(source);
        }
    }

    @ValueMappings({
            @ValueMapping(source = "AUTH_CERT_REGISTRATION_REQUEST", target = "AUTH_CERT_REGISTRATION_REQUEST"),
            @ValueMapping(source = "CLIENT_REGISTRATION_REQUEST", target = "CLIENT_REGISTRATION_REQUEST"),
            @ValueMapping(source = "OWNER_CHANGE_REQUEST", target = "OWNER_CHANGE_REQUEST"),
            @ValueMapping(source = "CLIENT_DELETION_REQUEST", target = "CLIENT_DELETION_REQUEST"),
            @ValueMapping(source = "AUTH_CERT_DELETION_REQUEST", target = "AUTH_CERT_DELETION_REQUEST"),
    })
    ManagementRequestTypeDto convert(ManagementRequestType source);

    @ValueMappings({
            @ValueMapping(source = "AUTH_CERT_REGISTRATION_REQUEST", target = "AUTH_CERT_REGISTRATION_REQUEST"),
            @ValueMapping(source = "CLIENT_REGISTRATION_REQUEST", target = "CLIENT_REGISTRATION_REQUEST"),
            @ValueMapping(source = "OWNER_CHANGE_REQUEST", target = "OWNER_CHANGE_REQUEST"),
            @ValueMapping(source = "CLIENT_DELETION_REQUEST", target = "CLIENT_DELETION_REQUEST"),
            @ValueMapping(source = "AUTH_CERT_DELETION_REQUEST", target = "AUTH_CERT_DELETION_REQUEST"),
    })
    ManagementRequestType convert(ManagementRequestTypeDto source);
}
