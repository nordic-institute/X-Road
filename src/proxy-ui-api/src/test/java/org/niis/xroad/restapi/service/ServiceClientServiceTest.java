/*
 *  The MIT License
 *  Copyright (c) 2018 Estonian Information System Authority (RIA),
 *  Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 *  Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package org.niis.xroad.restapi.service;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.GlobalGroupId;
import ee.ria.xroad.common.identifier.XRoadId;
import ee.ria.xroad.common.identifier.XRoadObjectType;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.niis.xroad.restapi.dto.ServiceClientDto;
import org.niis.xroad.restapi.facade.GlobalConfFacade;
import org.niis.xroad.restapi.repository.ClientRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.niis.xroad.restapi.service.AccessRightServiceTest.notImplemented;

/**
 * test Service client service
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureTestDatabase
@Slf4j
@Transactional
@WithMockUser
public class ServiceClientServiceTest {

    @MockBean
    GlobalConfFacade globalConfFacade;

    @MockBean
    GlobalConfService globalConfService;

    @Autowired
    ServiceClientService serviceClientService;

    @Autowired
    ClientRepository clientRepository;

    @Test(expected = ClientNotFoundException.class)
    public void getClientServiceClientsFromUnexistingClient() throws Exception {
        serviceClientService.getServiceClientsByClient(ClientId.create("NO", "SUCH", "CLIENT"));
    }

    @Test
    public void getObsoleteClientServiceClientsByClient() throws Exception {
        notImplemented();
    }
    @Test
    public void getObsoleteClientServiceClientsByEndpoint() throws Exception {
        notImplemented();
    }
    @Test
    public void getObsoleteClientServiceClientsByService() throws Exception {
        notImplemented();
    }
    @Test
    public void getObsoleteServiceClientAccessRights() throws Exception {
        notImplemented();
    }

    @Test
    public void getClientServiceClients() throws Exception {
        ClientId clientId1 = ClientId.create("FI", "GOV", "M2", "SS6");
        List<ServiceClientDto> serviceClients1 = serviceClientService.getServiceClientsByClient(clientId1);
        assertTrue(serviceClients1.size() == 1);

        ServiceClientDto arh1 = serviceClients1.get(0);
        assertTrue(arh1.getSubjectId().getObjectType().equals(XRoadObjectType.SUBSYSTEM));
        assertNull(arh1.getLocalGroupCode());
        assertNull(arh1.getLocalGroupDescription());
        assertNull(arh1.getLocalGroupId());
        assertTrue(arh1.getSubjectId().getXRoadInstance().equals("FI"));

        ClientId clientId2 = ClientId.create("FI", "GOV", "M1");
        assertTrue(serviceClientService.getServiceClientsByClient(clientId2).isEmpty());

        ClientId clientId3 = ClientId.create("FI", "GOV", "M1", "SS1");
        List<ServiceClientDto> serviceClients3 = serviceClientService.getServiceClientsByClient(clientId3);
        assertEquals(6, serviceClients3.size());
        assertTrue(serviceClients3.stream().anyMatch(arh -> arh.getSubjectId()
                .getObjectType().equals(XRoadObjectType.GLOBALGROUP)));
        assertTrue(serviceClients3.stream().anyMatch(arh -> arh.getSubjectId()
                .getObjectType().equals(XRoadObjectType.LOCALGROUP)));
        assertTrue(serviceClients3.stream().anyMatch(arh -> arh.getSubjectId()
                .getObjectType().equals(XRoadObjectType.SUBSYSTEM)
                && arh.getSubjectId().getXRoadInstance().equals("FI")));

    }

    @Test
    public void getClientServiceClientsHasCorrectRightsGiven() throws Exception {
        ClientId clientId = ClientId.create("FI", "GOV", "M1", "SS1");
        List<ServiceClientDto> serviceClients = serviceClientService.getServiceClientsByClient(clientId);
        XRoadId globalGroupId = GlobalGroupId.create("FI", "test-globalgroup");
        Optional<ServiceClientDto> groupServiceClient = serviceClients.stream()
                .filter(dto -> dto.getSubjectId().equals(globalGroupId))
                .findFirst();
        assertTrue(groupServiceClient.isPresent());
        // data.sql populates times in local time zone
        ZonedDateTime correctRightsGiven = LocalDateTime.parse("2020-01-01T09:07:22").atZone(ZoneId.systemDefault());
        // persistence layer gives times in utc time zone, so compare instants
        assertEquals(correctRightsGiven.toInstant(), groupServiceClient.get().getRightsGiven().toInstant());
    }

}
