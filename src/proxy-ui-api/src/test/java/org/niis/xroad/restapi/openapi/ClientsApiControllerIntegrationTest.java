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

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.niis.xroad.restapi.converter.GlobalConfWrapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Test ClientsApiController
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase
@Transactional
@Slf4j
public class ClientsApiControllerIntegrationTest {

    @MockBean
    private GlobalConfWrapper globalConfWrapper;

    @Before
    public void setup() {
        when(globalConfWrapper.getMemberName(any())).thenReturn("test-member-name");
    }

    @Autowired
    private ClientsApiController clientsApiController;

    // tests with TestRestTemplate would be good, but require some extra work
    // for authentication setup.
    // WithMockUser will not work with restTemplate, would need
    // to implement auth manually. Maybe improve this later.
//    @Autowired
//    private TestRestTemplate restTemplate;

//    @Test
//    public void test() {
//        ResponseEntity<List> response = this.restTemplate.getForEntity
//                ("/api/clients", List.class);
//        assertEquals(2, response.getBody().size());
//    }
    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void getClients() {
        ResponseEntity<List<org.niis.xroad.restapi.openapi.model.Client>> response =
                clientsApiController.getClients();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, response.getBody().size());
        org.niis.xroad.restapi.openapi.model.Client client = response.getBody().get(0);
        assertEquals("test-member-name", client.getMemberName());
        assertEquals("M1", client.getMemberCode());
    }

    @Test
    @WithMockUser(roles = "WRONG_ROLE")
    public void forbidden() {
        try {
            ResponseEntity<List<org.niis.xroad.restapi.openapi.model.Client>> response =
                    clientsApiController.getClients();
            fail("should throw AccessDeniedException");
        } catch (AccessDeniedException expected) { }
    }

}
