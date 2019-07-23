/**
 * The MIT License
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
package org.niis.xroad.restapi.converter;

import ee.ria.xroad.common.identifier.ServiceId;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * test ServiceConverter
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class ServiceConverterTest {

    @Autowired
    private ServiceConverter serviceConverter;

    @Test
    public void convertStringIdWithoutVersion() throws Exception {
        String serviceName = "awesomeService";
        ServiceId serviceId = serviceConverter.convertId("XRD2:GOV:M4:SS1:" + serviceName);
        assertEquals("XRD2", serviceId.getXRoadInstance());
        assertEquals("GOV", serviceId.getMemberClass());
        assertEquals("M4", serviceId.getMemberCode());
        assertEquals(serviceName, serviceId.getServiceCode());
        assertNull(serviceId.getServiceVersion());
    }

    @Test
    public void convertDifficultStringId() throws Exception {
        String difficultServiceCode = "FOO SS-;/?@=&-X<!-- o -->BAR";
        String serviceVersion = "v2";
        ServiceId serviceId = serviceConverter.convertId("XRD2:GOV:M4:SS1:" + difficultServiceCode
                + "." + serviceVersion);
        assertEquals("XRD2", serviceId.getXRoadInstance());
        assertEquals("GOV", serviceId.getMemberClass());
        assertEquals("M4", serviceId.getMemberCode());
        assertEquals(difficultServiceCode, serviceId.getServiceCode());
        assertEquals(serviceVersion, serviceId.getServiceVersion());
    }

    @Test(expected = RuntimeException.class)
    public void convertBadStringId() throws Exception {
        serviceConverter.convertId("XRD2:GOV:M4:SS1:aa:asd");
    }

    @Test(expected = RuntimeException.class)
    public void convertBadStringId2() throws Exception {
        serviceConverter.convertId("XRD2:GOV:M4:SS1");
    }
}
