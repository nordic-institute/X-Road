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

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import org.junit.Before;
import org.junit.Test;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.impl.GlobalConfImpl;
import org.niis.xroad.securityserver.restapi.cache.CurrentSecurityServerId;
import org.niis.xroad.securityserver.restapi.cache.CurrentSecurityServerSignCertificates;
import org.niis.xroad.securityserver.restapi.cache.SubsystemNameStatus;
import org.niis.xroad.securityserver.restapi.converter.comparator.ClientSortingComparator;
import org.niis.xroad.securityserver.restapi.openapi.model.ClientDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ClientStatusDto;
import org.niis.xroad.serverconf.model.Client;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;

/**
 * test ClientConverter
 */
public class ClientConverterTest {

    public static final String MEMBER_NAME_PREFIX = "member-name-for-";
    public static final String SUBSYSTEM_NAME_PREFIX = "subsystem-name-for-";
    private ClientConverter clientConverter;
    private ClientSortingComparator clientSortingComparator = new ClientSortingComparator();

    @Before
    public void setup() {
        GlobalConfProvider globalConfFacade = new GlobalConfImpl(null) {
            @Override
            public String getMemberName(ClientId identifier) {
                return MEMBER_NAME_PREFIX + identifier.getMemberCode();
            }

            @Override
            public String getSubsystemName(ClientId identifier) {
                return SUBSYSTEM_NAME_PREFIX + identifier.getSubsystemCode();
            }
        };
        ClientId.Conf ownerId = ClientId.Conf.create("XRD2", "GOV", "M4");
        SecurityServerId.Conf ownerSsId = SecurityServerId.Conf.create(ownerId, "CS");

        SubsystemNameStatus subsystemNameStatus = new SubsystemNameStatus();

        clientConverter = new ClientConverter(globalConfFacade, new CurrentSecurityServerId(ownerSsId),
                new CurrentSecurityServerSignCertificates(new ArrayList<>()), clientSortingComparator, subsystemNameStatus);
    }

    @Test
    public void convert() throws Exception {
        Client client = new Client();
        client.setClientStatus("registered");
        client.setIsAuthentication("SSLNOAUTH");
        client.setIdentifier(ClientId.Conf.create("XRD2", "GOV", "M4", "SS1"));
        ClientDto converted = clientConverter.convert(client);
        assertEquals("XRD2:GOV:M4:SS1", converted.getId());
        assertEquals(ClientStatusDto.REGISTERED, converted.getStatus());
        assertEquals("XRD2", converted.getInstanceId());
        assertEquals("GOV", converted.getMemberClass());
        assertEquals("M4", converted.getMemberCode());
        assertEquals("SS1", converted.getSubsystemCode());
        assertEquals(org.niis.xroad.securityserver.restapi.openapi.model.ConnectionTypeDto.HTTPS_NO_AUTH,
                converted.getConnectionType());
        assertEquals(MEMBER_NAME_PREFIX + "M4", converted.getMemberName());
    }

}
