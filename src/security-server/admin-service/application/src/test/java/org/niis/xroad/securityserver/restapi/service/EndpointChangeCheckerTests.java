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
package org.niis.xroad.securityserver.restapi.service;

import org.junit.Before;
import org.junit.Test;
import org.niis.xroad.serverconf.impl.entity.AccessRightEntity;
import org.niis.xroad.serverconf.impl.entity.EndpointEntity;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * endpoints change checker tests
 */
public class EndpointChangeCheckerTests {

    private EndpointEntityChangeChecker serviceChangeChecker;
    private EndpointEntity all;
    private EndpointEntity toBeRemoved;
    private EndpointEntity unchanged;
    private EndpointEntity wsdlEndpoint;
    private EndpointEntity newEndpoint;
    private AccessRightEntity toBeRemovedAcl;

    @Before
    public void setup() {
        serviceChangeChecker = new EndpointEntityChangeChecker();
        all = EndpointEntity.create("unitTestService", "*", "**", true);
        toBeRemoved = EndpointEntity.create("unitTestService", "GET", "/random", true);
        unchanged = EndpointEntity.create("unitTestService", "POST", "/random1", true);
        wsdlEndpoint = EndpointEntity.create("wsdlOperation1", "*", "**", true);
        newEndpoint = EndpointEntity.create("unitTestService", "GET", "/foo", true);
        toBeRemovedAcl = createAccessRight(toBeRemoved);
    }

    private AccessRightEntity createAccessRight(EndpointEntity endpoint) {
        AccessRightEntity acl = new AccessRightEntity();
        acl.setEndpoint(endpoint);
        return acl;
    }

    @Test
    public void testNoChanges() {
        List<EndpointEntity> serviceClientEndpoints = List.of(all, toBeRemoved, unchanged, wsdlEndpoint);
        List<EndpointEntity> openApiEndpoints = List.of(all, toBeRemoved, unchanged);
        List<AccessRightEntity> acls = List.of();
        assertTrue(serviceChangeChecker.check(serviceClientEndpoints, openApiEndpoints, openApiEndpoints, acls)
                .isEmpty());
    }

    @Test
    public void testChanges() {
        List<EndpointEntity> serviceClientEndpoints = List.of(all, toBeRemoved, unchanged, wsdlEndpoint);
        List<EndpointEntity> oldEndpoints = List.of(all, unchanged, toBeRemoved);
        List<EndpointEntity> newEndpoints = List.of(all, unchanged, newEndpoint);
        List<AccessRightEntity> acls = List.of(toBeRemovedAcl);
        EndpointEntityChangeChecker.ServiceChanges changes = serviceChangeChecker.check(serviceClientEndpoints,
                oldEndpoints, newEndpoints, acls);

        assertFalse(changes.isEmpty());
        assertEquals(List.of("GET /foo"), changes.getAddedEndpointsCodes());
        assertEquals(List.of("GET /random"), changes.getRemovedEndpointsCodes());
        assertEquals(1, changes.getRemovedAcls().size());
        assertEquals(toBeRemovedAcl, changes.getRemovedAcls().get(0));
    }

    @Test
    public void testChangesAddAll() {
        List<EndpointEntity> serviceClientEndpoints = List.of();
        List<EndpointEntity> allServices = List.of(all, unchanged, toBeRemoved, newEndpoint);
        List<String> allCodes = List.of("* **", "GET /random", "POST /random1", "GET /foo");
        List<EndpointEntity> noServices = new ArrayList<>();
        List<AccessRightEntity> acls = List.of(toBeRemovedAcl);
        EndpointEntityChangeChecker.ServiceChanges changes = serviceChangeChecker.check(serviceClientEndpoints,
                noServices, allServices, acls);

        assertFalse(changes.isEmpty());
        assertEquals(new HashSet<>(allCodes), new HashSet<>(changes.getAddedEndpointsCodes()));
        assertEquals(new HashSet<>(), new HashSet<>(changes.getRemovedEndpointsCodes()));
        assertTrue(changes.getRemovedAcls().isEmpty());
    }

    @Test
    public void testChangesRemoveAll() {
        List<EndpointEntity> serviceClientEndpoints = List.of(all, toBeRemoved, unchanged, newEndpoint, wsdlEndpoint);
        List<EndpointEntity> allServices = List.of(all, unchanged, toBeRemoved, newEndpoint);
        List<String> allCodes = List.of("* **", "GET /random", "POST /random1", "GET /foo");
        List<EndpointEntity> noServices = new ArrayList<>();
        List<AccessRightEntity> acls = List.of(toBeRemovedAcl);
        EndpointEntityChangeChecker.ServiceChanges changes = serviceChangeChecker.check(serviceClientEndpoints,
                allServices, noServices, acls);

        assertFalse(changes.isEmpty());
        assertEquals(new HashSet<>(), new HashSet<>(changes.getAddedEndpointsCodes()));
        assertEquals(new HashSet<>(allCodes), new HashSet<>(changes.getRemovedEndpointsCodes()));
        assertEquals(1, changes.getRemovedAcls().size());
        assertEquals(toBeRemovedAcl, changes.getRemovedAcls().get(0));
    }

}
