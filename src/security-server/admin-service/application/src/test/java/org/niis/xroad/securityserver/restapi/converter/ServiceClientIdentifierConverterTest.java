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

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.GlobalGroupId;
import ee.ria.xroad.common.identifier.XRoadObjectType;

import org.junit.Before;
import org.junit.Test;
import org.niis.xroad.securityserver.restapi.dto.ServiceClientIdentifierDto;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ServiceClientIdentifierConverterTest {

    private ServiceClientIdentifierConverter converter;

    @Before
    public void setup() {
        converter = new ServiceClientIdentifierConverter(
                new GlobalGroupConverter());
    }

    @Test
    public void convertLocalGroup() throws Exception {
        ServiceClientIdentifierDto dto = converter.convertId("1234");
        assertEquals(true, dto.isLocalGroup());
        assertEquals(Long.valueOf("1234"), dto.getLocalGroupId());
        assertEquals(null, dto.getXRoadId());
    }

    @Test
    public void convertGlobalGroup() throws Exception {
        ServiceClientIdentifierDto dto = converter.convertId("DEV:security-server-owners");
        assertEquals(false, dto.isLocalGroup());
        assertEquals(null, dto.getLocalGroupId());
        assertEquals(XRoadObjectType.GLOBALGROUP, dto.getXRoadId().getObjectType());
        assertTrue(dto.getXRoadId() instanceof GlobalGroupId);
        GlobalGroupId globalGroupId = (GlobalGroupId) dto.getXRoadId();
        assertEquals("security-server-owners", globalGroupId.getGroupCode());
        assertEquals("DEV", globalGroupId.getXRoadInstance());
    }

    @Test
    public void convertSubsystem() throws Exception {
        ServiceClientIdentifierDto dto = converter.convertId("DEV:ORG:1234:Subsystem");
        assertEquals(false, dto.isLocalGroup());
        assertEquals(null, dto.getLocalGroupId());
        assertEquals(XRoadObjectType.SUBSYSTEM, dto.getXRoadId().getObjectType());
        assertTrue(dto.getXRoadId() instanceof ClientId);
        ClientId clientId = (ClientId) dto.getXRoadId();
        assertEquals("DEV", clientId.getXRoadInstance());
        assertEquals("ORG", clientId.getMemberClass());
        assertEquals("1234", clientId.getMemberCode());
        assertEquals("Subsystem", clientId.getSubsystemCode());
    }

    @Test(expected = ServiceClientIdentifierConverter.BadServiceClientIdentifierException.class)
    public void convertMember() throws Exception {
        ServiceClientIdentifierDto dto = converter.convertId("DEV:ORG:1234");
    }

    @Test(expected = ServiceClientIdentifierConverter.BadServiceClientIdentifierException.class)
    public void convertNonNumeric() throws Exception {
        ServiceClientIdentifierDto dto = converter.convertId("foobar");
    }
}
