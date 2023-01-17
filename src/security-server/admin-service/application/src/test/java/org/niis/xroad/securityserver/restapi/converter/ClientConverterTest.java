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
package org.niis.xroad.securityserver.restapi.converter;

import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import org.junit.Before;
import org.junit.Test;
import org.niis.xroad.securityserver.restapi.cache.CurrentSecurityServerId;
import org.niis.xroad.securityserver.restapi.cache.CurrentSecurityServerSignCertificates;
import org.niis.xroad.securityserver.restapi.converter.comparator.ClientSortingComparator;
import org.niis.xroad.securityserver.restapi.facade.GlobalConfFacade;
import org.niis.xroad.securityserver.restapi.openapi.model.Client;
import org.niis.xroad.securityserver.restapi.openapi.model.ClientStatus;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;

/**
 * test ClientConverter
 */
public class ClientConverterTest {

    public static final String MEMBER_NAME_PREFIX = "member-name-for-";
    private ClientConverter clientConverter;
    private ClientSortingComparator clientSortingComparator = new ClientSortingComparator();

    @Before
    public void setup() {
        GlobalConfFacade globalConfFacade = new GlobalConfFacade() {
            @Override
            public String getMemberName(ClientId identifier) {
                return MEMBER_NAME_PREFIX + identifier.getMemberCode();
            }
        };
        ClientId.Conf ownerId = ClientId.Conf.create("XRD2", "GOV", "M4");
        SecurityServerId.Conf ownerSsId = SecurityServerId.Conf.create(ownerId, "CS");

        clientConverter = new ClientConverter(globalConfFacade, new CurrentSecurityServerId(ownerSsId),
                new CurrentSecurityServerSignCertificates(new ArrayList<>()), clientSortingComparator);
    }

    @Test
    public void convert() throws Exception {
        ClientType clientType = new ClientType();
        clientType.setClientStatus("registered");
        clientType.setIsAuthentication("SSLNOAUTH");
        clientType.setIdentifier(ClientId.Conf.create("XRD2", "GOV", "M4", "SS1"));
        Client converted = clientConverter.convert(clientType);
        assertEquals("XRD2:GOV:M4:SS1", converted.getId());
        assertEquals(ClientStatus.REGISTERED, converted.getStatus());
        assertEquals("XRD2", converted.getInstanceId());
        assertEquals("GOV", converted.getMemberClass());
        assertEquals("M4", converted.getMemberCode());
        assertEquals("SS1", converted.getSubsystemCode());
        assertEquals(org.niis.xroad.securityserver.restapi.openapi.model.ConnectionType.HTTPS_NO_AUTH,
                converted.getConnectionType());
        assertEquals(MEMBER_NAME_PREFIX + "M4", converted.getMemberName());
    }

}
