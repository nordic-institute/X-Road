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
package org.niis.xroad.restapi.converter;

import ee.ria.xroad.common.identifier.ServiceId;

import org.junit.jupiter.api.Test;
import org.niis.xroad.common.exception.BadRequestException;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;


/**
 * test ClientConverter
 */
class ServiceIdConverterTest {
    private final ServiceIdConverter serviceIdConverter = new ServiceIdConverter();

    @Test
    void convertStringId() {
        ServiceId serviceId = serviceIdConverter.convertId("XRD2:GOV:M4:TestService:getData");
        assertEquals("XRD2", serviceId.getXRoadInstance());
        assertEquals("GOV", serviceId.getMemberClass());
        assertEquals("M4", serviceId.getMemberCode());
        assertEquals("TestService", serviceId.getSubsystemCode());
        assertEquals("getData", serviceId.getServiceCode());
        assertNull(serviceId.getServiceVersion());

        serviceId = serviceIdConverter.convertId("XRD2:GOV:M4:TestService:getData.v1");
        assertEquals("XRD2", serviceId.getXRoadInstance());
        assertEquals("GOV", serviceId.getMemberClass());
        assertEquals("M4", serviceId.getMemberCode());
        assertEquals("TestService", serviceId.getSubsystemCode());
        assertEquals("getData", serviceId.getServiceCode());
        assertEquals("v1", serviceId.getServiceVersion());
    }

    @Test
    void convertBadStringId() {
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> serviceIdConverter.convertId("XRD2:GOV:M4:TestService:getData:getInfo"));
    }

    @Test
    void convertBadStringId2() {
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> serviceIdConverter.convertId("XRD2:GOV:M4:TestService"));
    }

    @Test
    void convertBadStringId3() {
        assertThatExceptionOfType(BadRequestException.class)
                .isThrownBy(() -> serviceIdConverter.convertId("XRD2:GOV:M4:TestService:getData.v1.v2"));
    }

    @Test
    void convertServiceId() {
        ServiceId serviceId = ServiceId.Conf.create("XRD2", "GOV", "M4", "TestService", "getData");
        String encoded = serviceIdConverter.convertId(serviceId);
        assertEquals("XRD2:GOV:M4:TestService:getData", encoded);

        serviceId = ServiceId.Conf.create("XRD2", "GOV", "M4","TestService", "getData", "v1");
        encoded = serviceIdConverter.convertId(serviceId);
        assertEquals("XRD2:GOV:M4:TestService:getData.v1", encoded);
    }

}
