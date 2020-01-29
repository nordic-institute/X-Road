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
import org.mockito.stubbing.Answer;
import org.niis.xroad.restapi.converter.ClientConverter;
import org.niis.xroad.restapi.facade.GlobalConfFacade;
import org.niis.xroad.restapi.openapi.model.Client;
import org.niis.xroad.restapi.util.TestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Test live clients api controller with rest template.
 * Test exists to check open-session-in-view related details
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase
@Transactional
@Slf4j
public class ClientsApiControllerRestTemplateTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @MockBean
    private GlobalConfFacade globalConfFacade;

    @SpyBean
    private ClientConverter clientConverter;

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void normalClientConverterWorks() {
        restTemplate.getRestTemplate().setInterceptors(
                Collections.singletonList((request, body, execution) -> {
                    request.getHeaders()
                            .add("Authorization", "X-Road-apikey token=d56e1ca7-4134-4ed4-8030-5f330bdb602a");
                    return execution.execute(request, body);
                }));

        ResponseEntity<Client> clientResponse = restTemplate.getForEntity("/api/clients/" + TestUtils.CLIENT_ID_SS1,
                Client.class);
        assertEquals(HttpStatus.OK, clientResponse.getStatusCode());
        assertEquals("M1", clientResponse.getBody().getMemberCode());
    }

    @Test
    @WithMockUser(authorities = "VIEW_CLIENTS")
    public void clientConverterCannotLazyLoadPropertiesSinceOsivIsNotUsed() {
        doAnswer((Answer<String>) invocation -> {
            ClientType clientType = (ClientType) invocation.getArguments()[0];
            // cause a lazy loading exception
            log.info("lazy loaded server code=" + clientType.getConf().getServerCode());
            return null;
        }).when(clientConverter).convert(any(ClientType.class));

        restTemplate.getRestTemplate().setInterceptors(
                Collections.singletonList((request, body, execution) -> {
                    request.getHeaders()
                            .add("Authorization",
                                    "X-Road-apikey token=d56e1ca7-4134-4ed4-8030-5f330bdb602a");
                    return execution.execute(request, body);
                }));

        ResponseEntity<Object> clientResponse = restTemplate.getForEntity("/api/clients/" + TestUtils.CLIENT_ID_SS1,
                Object.class);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, clientResponse.getStatusCode());
    }
}
