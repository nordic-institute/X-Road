/*
 * The MIT License
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
package org.niis.xroad.cs.admin.core.entity.converter;

import ee.ria.xroad.common.identifier.XRoadObjectType;
import ee.ria.xroad.common.util.NoCoverage;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.Mapper;
import org.mapstruct.MappingConstants;
import org.mapstruct.ValueMapping;
import org.mapstruct.ValueMappings;
import org.niis.xroad.restapi.converter.DtoConverter;

@Mapper
public interface XRoadObjectTypeConverter {

    @Slf4j
    @Converter
    @NoCoverage
    class Impl extends XRoadObjectTypeConverterImpl implements AttributeConverter<XRoadObjectType, String> {

        private static final Impl IMPLEMENTATION = new Impl();

        @Override
        public String convertToDatabaseColumn(XRoadObjectType attribute) {
            return IMPLEMENTATION.convert(attribute);
        }

        @Override
        public XRoadObjectType convertToEntityAttribute(String dbData) {
            return IMPLEMENTATION.convert(dbData);
        }
    }

    @Slf4j
    @org.springframework.stereotype.Service
    @NoCoverage
    class Service extends DtoConverter<String, XRoadObjectType> {
        private static final Impl IMPLEMENTATION = new Impl();

        @Override
        public XRoadObjectType toDto(String source) {
            return IMPLEMENTATION.convert(source);
        }

        @Override
        public String fromDto(XRoadObjectType source) {
            return IMPLEMENTATION.convert(source);
        }
    }

    @ValueMappings({
            @ValueMapping(source = "SERVER", target = "SERVER"),
            @ValueMapping(source = "SERVICE", target = "SERVICE"),
            @ValueMapping(source = "MEMBER", target = "MEMBER"),
            @ValueMapping(source = "SUBSYSTEM", target = "SUBSYSTEM"),
            @ValueMapping(source = "GLOBALGROUP", target = "GLOBALGROUP"),
            @ValueMapping(source = "LOCALGROUP", target = "LOCALGROUP"),
            @ValueMapping(source = MappingConstants.ANY_UNMAPPED, target = MappingConstants.NULL),
    })
    XRoadObjectType convert(String source);

    @ValueMappings({
            @ValueMapping(source = "SERVER", target = "SERVER"),
            @ValueMapping(source = "SERVICE", target = "SERVICE"),
            @ValueMapping(source = "MEMBER", target = "MEMBER"),
            @ValueMapping(source = "SUBSYSTEM", target = "SUBSYSTEM"),
            @ValueMapping(source = "GLOBALGROUP", target = "GLOBALGROUP"),
            @ValueMapping(source = "LOCALGROUP", target = "LOCALGROUP"),
    })
    String convert(XRoadObjectType source);
}
