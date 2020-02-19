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

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.niis.xroad.restapi.service.ClientService;
import org.niis.xroad.restapi.service.EndpointService;
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

    private final String NO_SUCH_ENDPOINT_ID = "1294379018";

    @Test(expected = BadRequestException.class)
    @WithMockUser(authorities = {"DELETE_ENDPOINT"})
    public void deleteEndpointNotExist() {
        endpointsApiController.deleteEndpoint(NO_SUCH_ENDPOINT_ID);
    }

    @Test
    @WithMockUser(authorities = {"DELETE_ENDPOINT"})
    public void deleteEndpoint() {
        endpointsApiController.deleteEndpoint("12");
        ClientType client = clientService.getClient(getClientId("FI", "GOV", "M2", "SS6"));
        assertTrue(!client.getEndpoint().stream().anyMatch(ep -> ep.getId().equals("12")));
    }

}
