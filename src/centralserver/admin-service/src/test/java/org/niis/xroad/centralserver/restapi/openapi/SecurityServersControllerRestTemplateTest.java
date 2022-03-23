/**
 * The MIT License
 * <p>
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.centralserver.restapi.openapi;

import org.junit.Test;
import org.niis.xroad.centralserver.openapi.model.PagedSecurityServers;
import org.niis.xroad.centralserver.openapi.model.SecurityServer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.niis.xroad.centralserver.restapi.util.TestUtils.addApiKeyAuthorizationHeader;

@Transactional
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
public class SecurityServersControllerRestTemplateTest extends AbstractApiRestTemplateTestContext {

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    public void testGetSecurityServersSucceedsWithoutParameters() {
        addApiKeyAuthorizationHeader(restTemplate);
        ResponseEntity<PagedSecurityServers> response = restTemplate.getForEntity("/api/v1/security-servers",
                PagedSecurityServers.class);
        assertNotNull(response, "Security server list response  must not be null.");
        assertEquals(200, response.getStatusCodeValue(), "Security server list request status code must be 200 ");
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getClients(), "Should return at least an empty list");
        assertTrue(response.getBody().getClients().stream()
                .allMatch(client -> client.getXroadId().getType().getValue().equals("SERVER")));
        assertNotNull(response.getBody().getPagingMetadata());
        int itemCount = response.getBody().getClients().size();
        assertTrue(0 < itemCount, "Should return more than one client");
        assertTrue(itemCount <= response.getBody().getPagingMetadata().getTotalItems(),
                "Total items must not be less than clients returned in one page");

    }

    @Test
    public void testPagingParameterLimit() {
        addApiKeyAuthorizationHeader(restTemplate);
        ResponseEntity<PagedSecurityServers> response = restTemplate.getForEntity("/api/v1/security-servers/?limit=1",
                PagedSecurityServers.class);
        assertNotNull(response, "Security server list response  must not be null.");
        assertEquals(200, response.getStatusCodeValue(), "Security server list request status code must be 200 ");
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getClients(), "Should return clients");
        int itemCount = response.getBody().getClients().size();
        assertTrue(0 < itemCount, "Should return more than one client");
        assertTrue(itemCount < response.getBody().getPagingMetadata().getTotalItems(),
                "Total items be more than returned in one page");

        ResponseEntity<PagedSecurityServers> response2 =
                restTemplate.getForEntity("/api/v1/security-servers/?limit=1&offset=1",
                        PagedSecurityServers.class);

        assertEquals(200, response2.getStatusCodeValue());
        assertNotNull(response2.getBody());
        assertNotNull(response2.getBody().getClients(), "Should return clients");
        assertEquals(1, response2.getBody().getClients().size());
        assertEquals(1, response2.getBody().getPagingMetadata().getOffset());
        assertNotEquals(response.getBody().getClients().get(0), response2.getBody().getClients().get(0));


    }

    @Test
    public void givenQParameterReturnsMatchingServers() {
        addApiKeyAuthorizationHeader(restTemplate);
        ResponseEntity<PagedSecurityServers> response = restTemplate.getForEntity("/api/v1/security-servers/?q=ADMIN",
                PagedSecurityServers.class);
        assertNotNull(response, "Security server list response  must not be null.");
        assertEquals(200, response.getStatusCodeValue(),
                "Security server list request return 200 status");
        assertNotNull(response.getBody());
        List<SecurityServer> securityServers = response.getBody().getClients();
        assertNotNull(securityServers);
        final int securityServersMatchingSearchTermAdmin = 1;
        assertEquals(securityServersMatchingSearchTermAdmin, securityServers.size());
        assertNotNull(response.getBody().getPagingMetadata());
        Integer itemCount = response.getBody().getPagingMetadata().getTotalItems();
        assertNotNull(itemCount);
        assertTrue(itemCount <= response.getBody().getPagingMetadata().getTotalItems(),
                "Total items must not be less than clients returned in one page");

    }

    @Test
    public void givenDescSortReturnsCorrectlySorted() {
        addApiKeyAuthorizationHeader(restTemplate);
        ResponseEntity<PagedSecurityServers> response =
                restTemplate.getForEntity("/api/v1/security-servers/?desc=true&sort=owner_code",
                        PagedSecurityServers.class);
        assertNotNull(response, "Security server list response  must not be null.");
        assertEquals(200, response.getStatusCodeValue(),
                "Security server list request return 200 status");
        assertNotNull(response.getBody());
        List<SecurityServer> securityServers = response.getBody().getClients();
        assertNotNull(securityServers);
        assertNotNull(response.getBody().getPagingMetadata());
        int itemCount = response.getBody().getClients().size();
        assertTrue(itemCount <= response.getBody().getPagingMetadata().getTotalItems(),
                "Total items must not be less than clients returned in one page");
        assertTrue(0 < securityServers.get(0).getXroadId().getMemberCode()
                .compareTo(securityServers.get(itemCount - 1).getXroadId().getMemberCode()));

    }


}
