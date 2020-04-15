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

import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import org.junit.Before;
import org.junit.Test;
import org.niis.xroad.restapi.cache.CurrentSecurityServerId;
import org.niis.xroad.restapi.facade.GlobalConfFacade;
import org.niis.xroad.restapi.openapi.BadRequestException;
import org.niis.xroad.restapi.openapi.model.Client;
import org.niis.xroad.restapi.openapi.model.ClientStatus;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * test ClientConverter
 */
public class ClientConverterTest {

    public static final String MEMBER_NAME_PREFIX = "member-name-for-";
    private ClientConverter clientConverter;

    @Before
    public void setup() {
        GlobalConfFacade globalConfFacade = new GlobalConfFacade() {
            @Override
            public String getMemberName(ClientId identifier) {
                return MEMBER_NAME_PREFIX + identifier.getMemberCode();
            }
        };
        ClientId ownerId = ClientId.create("XRD2", "GOV", "M4");
        SecurityServerId ownerSsId = SecurityServerId.create(ownerId, "CS");

        clientConverter = new ClientConverter(globalConfFacade, new CurrentSecurityServerId(ownerSsId));
    }

    @Test
    public void convert() throws Exception {
        ClientType clientType = new ClientType();
        clientType.setClientStatus("registered");
        clientType.setIsAuthentication("SSLNOAUTH");
        clientType.setIdentifier(ClientId.create("XRD2", "GOV", "M4", "SS1"));
        Client converted = clientConverter.convert(clientType);
        assertEquals("XRD2:GOV:M4:SS1", converted.getId());
        assertEquals(ClientStatus.REGISTERED, converted.getStatus());
        assertEquals("XRD2", converted.getInstanceId());
        assertEquals("GOV", converted.getMemberClass());
        assertEquals("M4", converted.getMemberCode());
        assertEquals("SS1", converted.getSubsystemCode());
        assertEquals(org.niis.xroad.restapi.openapi.model.ConnectionType.HTTPS_NO_AUTH, converted.getConnectionType());
        assertEquals(MEMBER_NAME_PREFIX + "M4", converted.getMemberName());
    }

    @Test
    public void convertStringId() throws Exception {
        ClientId clientId = clientConverter.convertId("XRD2:GOV:M4:SS1");
        assertEquals("XRD2", clientId.getXRoadInstance());
        assertEquals("GOV", clientId.getMemberClass());
        assertEquals("M4", clientId.getMemberCode());
        assertEquals("SS1", clientId.getSubsystemCode());

        clientId = clientConverter.convertId("XRD2:GOV:M4");
        assertNull(clientId.getSubsystemCode());
    }
    @Test

    public void convertDifficultStringId() throws Exception {
        String difficultSubsystemId = "FOO SS-;/?@=&-X<!-- o -->BAR";
        ClientId clientId = clientConverter.convertId("XRD2:GOV:M4:" + difficultSubsystemId);
        assertEquals("XRD2", clientId.getXRoadInstance());
        assertEquals("GOV", clientId.getMemberClass());
        assertEquals("M4", clientId.getMemberCode());
        assertEquals(difficultSubsystemId, clientId.getSubsystemCode());
    }

    @Test(expected = BadRequestException.class)
    public void convertBadStringId() throws Exception {
        clientConverter.convertId("XRD2:GOV:M4:SS1:aa");
    }

    @Test(expected = BadRequestException.class)
    public void convertBadStringId2() throws Exception {
        clientConverter.convertId("XRD2");
    }

    @Test(expected = BadRequestException.class)
    public void convertBadStringId3() throws Exception {
        clientConverter.convertId("XRD2:GOV:M4:SS1::::::");
    }

    @Test
    public void isEncodedMemberId() throws Exception {
        assertTrue(clientConverter.isEncodedMemberId("XRD2:GOV:M4"));
        assertFalse(clientConverter.isEncodedMemberId("XRD2:GOV:M4:SS1"));
    }

    @Test
    public void isEncodedSubsystemId() throws Exception {
        assertFalse(clientConverter.isEncodedSubsystemId("XRD2:GOV:M4"));
        assertTrue(clientConverter.isEncodedSubsystemId("XRD2:GOV:M4:SS1"));
    }

    @Test
    public void isEncodedClientId() throws Exception {
        assertTrue(clientConverter.isEncodedClientId("XRD2:GOV:M4"));
        assertTrue(clientConverter.isEncodedClientId("XRD2:GOV:M4:SS1"));
    }

    @Test
    public void convertClientId() throws Exception {
        ClientId clientId = ClientId.create("XRD2", "GOV", "M4", "SS1");
        String encoded = clientConverter.convertId(clientId);
        assertEquals("XRD2:GOV:M4:SS1", encoded);

        clientId = ClientId.create("XRD2", "GOV", "M4");
        encoded = clientConverter.convertId(clientId);
        assertEquals("XRD2:GOV:M4", encoded);
    }

}
