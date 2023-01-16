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
package org.niis.xroad.cs.admin.application.openapi;

import org.junit.jupiter.api.Disabled;

@SuppressWarnings("java:S2187")
@Disabled("Has to be revorked for new architecture.")
public class SecurityServersApiTest extends AbstractApiRestTemplateTestContext {

  /*  @Autowired
    SecurityServerClientRepository securityServerClientRepository;

    @Autowired
    TestRestTemplate restTemplate;

    @Test
    public void testGetSecurityServersSucceedsWithoutParameters() {
        securityServerClientRepository.findAll();

        addApiKeyAuthorizationHeader(restTemplate);

        ResponseEntity<PagedSecurityServersDto> response = restTemplate.getForEntity("/api/v1/security-servers",
                PagedSecurityServersDto.class);

        assertNotNull(response, "Security server list response  must not be null.");
        assertEquals(200, response.getStatusCodeValue(), "Security server list request status code must be 200 ");
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getItems(), "Should return at least an empty list");
        assertTrue(response.getBody().getItems().stream()
                .allMatch(client -> client.getXroadId().getType().getValue().equals("SERVER")));
        assertNotNull(response.getBody().getPagingMetadata());
        int itemCount = response.getBody().getItems().size();
        assertTrue(0 < itemCount, "Should return more than one client");
        assertTrue(itemCount <= response.getBody().getPagingMetadata().getTotalItems(),
                "Total items must not be less than clients returned in one page");
    }

    @Test
    public void testPagingParameterLimit() {
        addApiKeyAuthorizationHeader(restTemplate);
        ResponseEntity<PagedSecurityServersDto> response = restTemplate.getForEntity("/api/v1/security-servers/?limit=1",
                PagedSecurityServersDto.class);
        assertNotNull(response, "Security server list response  must not be null.");
        assertEquals(200, response.getStatusCodeValue(), "Security server list request status code must be 200 ");
        assertNotNull(response.getBody());
        assertNotNull(response.getBody().getItems(), "Should return clients");
        int itemCount = response.getBody().getItems().size();
        assertEquals(1, itemCount, "Should return one client");
        assertTrue(itemCount < response.getBody().getPagingMetadata().getTotalItems(),
                "Total items be more than returned in one page");

        ResponseEntity<PagedSecurityServersDto> response2 =
                restTemplate.getForEntity("/api/v1/security-servers/?limit=1&offset=1",
                        PagedSecurityServersDto.class);

        assertEquals(200, response2.getStatusCodeValue());
        assertNotNull(response2.getBody());
        assertNotNull(response2.getBody().getItems(), "Should return clients");
        assertEquals(1, response2.getBody().getItems().size());
        assertEquals(1, response2.getBody().getPagingMetadata().getOffset());
        assertNotEquals(response.getBody().getItems().get(0), response2.getBody().getItems().get(0));


    }

    @Test
    public void givenQParameterReturnsMatchingServers() {
        addApiKeyAuthorizationHeader(restTemplate);

        ResponseEntity<PagedSecurityServersDto> response = restTemplate.getForEntity("/api/v1/security-servers/?q=ADMIN",
                PagedSecurityServersDto.class);

        assertNotNull(response, "Security server list response  must not be null.");
        assertEquals(200, response.getStatusCodeValue(),
                "Security server list request return 200 status");
        assertNotNull(response.getBody());
        List<SecurityServerDto> securityServers = response.getBody().getItems();
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

        ResponseEntity<PagedSecurityServersDto> response =
                restTemplate.getForEntity("/api/v1/security-servers/?desc=true&sort=xroad_id.member_code",
                        PagedSecurityServersDto.class);

        assertNotNull(response, "Security server list response  must not be null.");
        assertEquals(200, response.getStatusCodeValue(),
                "Security server list request return 200 status");
        assertNotNull(response.getBody());
        List<SecurityServerDto> securityServers = response.getBody().getItems();
        assertNotNull(securityServers);
        assertNotNull(response.getBody().getPagingMetadata());
        int itemCount = response.getBody().getItems().size();
        assertTrue(itemCount <= response.getBody().getPagingMetadata().getTotalItems(),
                "Total items must not be less than clients returned in one page");
        assertTrue(0 < securityServers.get(0).getXroadId().getMemberCode()
                .compareTo(securityServers.get(itemCount - 1).getXroadId().getMemberCode()));

    }
*/

}
