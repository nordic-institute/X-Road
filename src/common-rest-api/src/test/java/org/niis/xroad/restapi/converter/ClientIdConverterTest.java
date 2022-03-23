/**
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
package org.niis.xroad.restapi.converter;

import ee.ria.xroad.common.identifier.ClientId;

import org.junit.Test;
import org.niis.xroad.restapi.openapi.BadRequestException;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * test ClientConverter
 */
public class ClientIdConverterTest {

    public static final String MEMBER_NAME_PREFIX = "member-name-for-";
    private ClientIdConverter clientIdConverter = new ClientIdConverter();

    @Test
    public void convertStringId() throws Exception {
        ClientId clientId = clientIdConverter.convertId("XRD2:GOV:M4:SS1");
        assertEquals("XRD2", clientId.getXRoadInstance());
        assertEquals("GOV", clientId.getMemberClass());
        assertEquals("M4", clientId.getMemberCode());
        assertEquals("SS1", clientId.getSubsystemCode());

        clientId = clientIdConverter.convertId("XRD2:GOV:M4");
        assertNull(clientId.getSubsystemCode());
    }
    @Test

    public void convertDifficultStringId() throws Exception {
        String difficultSubsystemId = "FOO SS-;/?@=&-X<!-- o -->BAR";
        ClientId clientId = clientIdConverter.convertId("XRD2:GOV:M4:" + difficultSubsystemId);
        assertEquals("XRD2", clientId.getXRoadInstance());
        assertEquals("GOV", clientId.getMemberClass());
        assertEquals("M4", clientId.getMemberCode());
        assertEquals(difficultSubsystemId, clientId.getSubsystemCode());
    }

    @Test(expected = BadRequestException.class)
    public void convertBadStringId() throws Exception {
        clientIdConverter.convertId("XRD2:GOV:M4:SS1:aa");
    }

    @Test(expected = BadRequestException.class)
    public void convertBadStringId2() throws Exception {
        clientIdConverter.convertId("XRD2");
    }

    @Test(expected = BadRequestException.class)
    public void convertBadStringId3() throws Exception {
        clientIdConverter.convertId("XRD2:GOV:M4:SS1::::::");
    }

    @Test
    public void isEncodedMemberId() throws Exception {
        assertTrue(clientIdConverter.isEncodedMemberId("XRD2:GOV:M4"));
        assertFalse(clientIdConverter.isEncodedMemberId("XRD2:GOV:M4:SS1"));
    }

    @Test
    public void isEncodedSubsystemId() throws Exception {
        assertFalse(clientIdConverter.isEncodedSubsystemId("XRD2:GOV:M4"));
        assertTrue(clientIdConverter.isEncodedSubsystemId("XRD2:GOV:M4:SS1"));
    }

    @Test
    public void isEncodedClientId() throws Exception {
        assertTrue(clientIdConverter.isEncodedClientId("XRD2:GOV:M4"));
        assertTrue(clientIdConverter.isEncodedClientId("XRD2:GOV:M4:SS1"));
    }

    @Test
    public void convertClientId() throws Exception {
        ClientId clientId = ClientId.create("XRD2", "GOV", "M4", "SS1");
        String encoded = clientIdConverter.convertId(clientId);
        assertEquals("XRD2:GOV:M4:SS1", encoded);

        clientId = ClientId.create("XRD2", "GOV", "M4");
        encoded = clientIdConverter.convertId(clientId);
        assertEquals("XRD2:GOV:M4", encoded);
    }

}
