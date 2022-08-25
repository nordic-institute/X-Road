/*
 *  The MIT License
 *  Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.GlobalGroupId;
import ee.ria.xroad.common.identifier.XRoadId;
import ee.ria.xroad.common.identifier.XRoadObjectType;

import org.junit.Test;
import org.niis.xroad.securityserver.restapi.dto.ServiceClientAccessRightDto;
import org.niis.xroad.securityserver.restapi.dto.ServiceClientDto;
import org.niis.xroad.securityserver.restapi.util.TestUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * test Service client service
 */
public class ServiceClientServiceIntegrationTest extends AbstractServiceIntegrationTestContext {

    @Autowired
    ServiceClientService serviceClientService;

    @Test(expected = ClientNotFoundException.class)
    public void getClientServiceClientsFromUnexistingClient() throws Exception {
        serviceClientService.getServiceClientsByClient(ClientId.Conf.create("NO", "SUCH", "CLIENT"));
    }

    // ACL subject identifier.IDs: 3 4 5 9 10 11 (only base endpoint ACLs count)
    // 6 and 8 are missing?
    public static final int SS1_SERVICE_CLIENTS = 6;

    @Test
    public void getObsoleteClientServiceClientsByClient() throws Exception {
        ClientId serviceOwner = TestUtils.getM1Ss1ClientId();

        List<ServiceClientDto> scs = serviceClientService.getServiceClientsByClient(serviceOwner);
        assertEquals(SS1_SERVICE_CLIENTS, scs.size());
        Set<XRoadId> scIds = scs.stream()
                .map(dto -> dto.getSubjectId())
                .collect(Collectors.toSet());
        assertTrue(scIds.contains(TestUtils.OBSOLETE_GGROUP_ID));
        assertTrue(scIds.contains(TestUtils.OBSOLETE_SUBSYSTEM_ID));
    }

    @Test
    public void getObsoleteClientServiceClientsByEndpoint() throws Exception {
        List<ServiceClientDto> scs = serviceClientService.getServiceClientsByEndpoint(
                TestUtils.OBSOLETE_SCS_BASE_ENDPOINT_ID);
        assertEquals(2, scs.size());
        Set<XRoadId> scIds = scs.stream()
                .map(dto -> dto.getSubjectId())
                .collect(Collectors.toSet());
        assertTrue(scIds.contains(TestUtils.OBSOLETE_GGROUP_ID));
        assertTrue(scIds.contains(TestUtils.OBSOLETE_SUBSYSTEM_ID));
    }

    @Test
    public void getObsoleteClientServiceClientsByService() throws Exception {
        ClientId serviceOwner = TestUtils.getM1Ss1ClientId();

        List<ServiceClientDto> scs = serviceClientService.getServiceClientsByService(serviceOwner,
                TestUtils.OBSOLETE_SCS_FULL_SERVICE_CODE);
        assertEquals(2, scs.size());
        Set<XRoadId> scIds = scs.stream()
                .map(dto -> dto.getSubjectId())
                .collect(Collectors.toSet());
        assertTrue(scIds.contains(TestUtils.OBSOLETE_GGROUP_ID));
        assertTrue(scIds.contains(TestUtils.OBSOLETE_SUBSYSTEM_ID));
    }

    @Test
    public void getObsoleteServiceClientAccessRights() throws Exception {
        ClientId serviceOwner = TestUtils.getM1Ss1ClientId();

        List<ServiceClientAccessRightDto> accessRightDtos = serviceClientService.getServiceClientAccessRights(
                serviceOwner,
                TestUtils.OBSOLETE_GGROUP_ID);
        assertTrue(findAccessRightDto(accessRightDtos, TestUtils.OBSOLETE_SCS_SERVICE_CODE).isPresent());
        assertEquals(1, accessRightDtos.size());

        accessRightDtos = serviceClientService.getServiceClientAccessRights(
                serviceOwner,
                TestUtils.OBSOLETE_SUBSYSTEM_ID);
        assertTrue(findAccessRightDto(accessRightDtos, TestUtils.OBSOLETE_SCS_SERVICE_CODE).isPresent());
        assertEquals(1, accessRightDtos.size());
    }

    @Test
    public void getServiceClientAccessRights() throws Exception {
        // get access rights for normal service client that has some
        ClientId serviceOwner = TestUtils.getM1Ss1ClientId();

        List<ServiceClientAccessRightDto> accessRightDtos = serviceClientService.getServiceClientAccessRights(
                serviceOwner,
                GlobalGroupId.Conf.create("FI", "test-globalgroup"));
        assertTrue(findAccessRightDto(accessRightDtos, "getRandom").isPresent());
        assertTrue(findAccessRightDto(accessRightDtos, "calculatePrime").isPresent());
        assertTrue(findAccessRightDto(accessRightDtos, "openapi-servicecode").isPresent());
        assertEquals(3, accessRightDtos.size());
    }

    private Optional<ServiceClientAccessRightDto> findAccessRightDto(List<ServiceClientAccessRightDto> dtos,
            String serviceCode) {
        return dtos.stream()
                .filter(dto -> dto.getServiceCode().equals(serviceCode))
                .findFirst();
    }

    @Test(expected = ServiceClientNotFoundException.class)
    public void getServiceClientMissingAccessRights() throws Exception {
        // get access rights for normal service client that has none
        // this is effectively the same as getClientServiceClientAccessRightsFromUnexistingClient where service client
        // object does not exist
        ClientId serviceOwner = TestUtils.getM1Ss1ClientId();

        List<ServiceClientAccessRightDto> accessRightDtos = serviceClientService.getServiceClientAccessRights(
                serviceOwner,
                serviceOwner);
        assertEquals(0, accessRightDtos.size());
    }

    @Test(expected = ServiceClientNotFoundException.class)
    public void getClientServiceClientAccessRightsFromUnexistingClient() throws Exception {
        ClientId.Conf serviceOwner = TestUtils.getM1Ss1ClientId();
        // this is not existing, not even obsolete (has no IDENTIFIER)
        ClientId.Conf unexistingClient = ClientId.Conf.create("NO", "SUCH", "CLIENT");

        serviceClientService.getServiceClientAccessRights(
                serviceOwner,
                unexistingClient);
    }

    @Test
    public void getClientServiceClients() throws Exception {
        ClientId.Conf clientId1 = ClientId.Conf.create("FI", "GOV", "M2", "SS6");
        List<ServiceClientDto> serviceClients1 = serviceClientService.getServiceClientsByClient(clientId1);
        assertTrue(serviceClients1.size() == 1);

        ServiceClientDto arh1 = serviceClients1.get(0);
        assertTrue(arh1.getSubjectId().getObjectType().equals(XRoadObjectType.SUBSYSTEM));
        assertNull(arh1.getLocalGroupCode());
        assertNull(arh1.getLocalGroupDescription());
        assertNull(arh1.getLocalGroupId());
        assertTrue(arh1.getSubjectId().getXRoadInstance().equals("FI"));

        ClientId.Conf clientId2 = ClientId.Conf.create("FI", "GOV", "M1");
        assertTrue(serviceClientService.getServiceClientsByClient(clientId2).isEmpty());

        ClientId.Conf clientId3 = ClientId.Conf.create("FI", "GOV", "M1", "SS1");
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
        ClientId clientId = ClientId.Conf.create("FI", "GOV", "M1", "SS1");
        List<ServiceClientDto> serviceClients = serviceClientService.getServiceClientsByClient(clientId);
        XRoadId globalGroupId = GlobalGroupId.Conf.create("FI", "test-globalgroup");
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
