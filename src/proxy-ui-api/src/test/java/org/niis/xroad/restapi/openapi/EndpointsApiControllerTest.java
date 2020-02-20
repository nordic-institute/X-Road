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
package org.niis.xroad.restapi.openapi;

import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.conf.serverconf.model.EndpointType;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.niis.xroad.restapi.openapi.model.Endpoint;
import org.niis.xroad.restapi.service.ClientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.Assert.assertTrue;
import static org.niis.xroad.restapi.util.TestUtils.getClientId;

@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureTestDatabase
@Transactional
public class EndpointsApiControllerTest {

    @Autowired
    private EndpointsApiController endpointsApiController;

    @Autowired
    private ClientService clientService;

    private static final String NO_SUCH_ENDPOINT_ID = "1294379018";

    @Test(expected = ResourceNotFoundException.class)
    @WithMockUser(authorities = {"DELETE_ENDPOINT"})
    public void deleteEndpointNotExist() {
        endpointsApiController.deleteEndpoint(NO_SUCH_ENDPOINT_ID);
    }

    @Test
    @WithMockUser(authorities = {"DELETE_ENDPOINT"})
    public void deleteEndpoint() {
        ClientType client = clientService.getClient(getClientId("FI", "GOV", "M2", "SS6"));
        int aclCount = client.getAcl().size();
        endpointsApiController.deleteEndpoint("11");
        assertTrue(!client.getEndpoint().stream().anyMatch(ep -> ep.getId().equals("11")));
        assertTrue(client.getAcl().size() < aclCount);
    }

    @Test(expected = ConflictException.class)
    @WithMockUser(authorities = {"EDIT_OPENAPI3_ENDPOINT"})
    public void updateGeneratedEndpoint() {
        Endpoint endpointUpdate = new Endpoint();
        endpointUpdate.setId("10");
        endpointUpdate.setServiceCode("TestServiceCode");
        endpointUpdate.setMethod("*");
        endpointUpdate.setPath("/test");
        endpointUpdate.setGenerated(false);
        endpointsApiController.updateEndpoint("10", endpointUpdate);
    }

    @Test
    @WithMockUser(authorities = {"EDIT_OPENAPI3_ENDPOINT"})
    public void updateEndpoint() {
        Endpoint endpointUpdate = new Endpoint();
        endpointUpdate.setId("12");
        endpointUpdate.setServiceCode("TestServiceCode");
        endpointUpdate.setMethod("*");
        endpointUpdate.setPath("/test");
        endpointUpdate.setGenerated(false);
        endpointsApiController.updateEndpoint("12", endpointUpdate);

        ClientType client = clientService.getClient(getClientId("FI", "GOV", "M2", "SS6"));
        EndpointType endpointType = client.getEndpoint().stream().filter(ep -> ep.getId().equals(12L))
                .findFirst().get();

        assertTrue(endpointType.getServiceCode().equals("TestServiceCode"));
        assertTrue(endpointType.getMethod().equals("*"));
        assertTrue(endpointType.getPath().equals("/test"));

    }

}
