/*
 * The MIT License
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
package org.niis.xroad.securityserver.restapi.openapi;

import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

/**
 * test xroad instances api controller
 */
public class XroadInstancesApiControllerIntegrationTest extends AbstractApiControllerTestContext {

    @Autowired
    XroadInstancesApiController xroadInstancesApiController;

    private static final String INSTANCE_A = "instance_a";
    private static final String INSTANCE_B = "instance_b";
    private static final String INSTANCE_C = "instance_c";
    private static final Set<String> INSTANCE_IDS = new HashSet<>(Arrays.asList(INSTANCE_A, INSTANCE_B, INSTANCE_C));

    @Before
    public void setup() {
        when(globalConfFacade.getInstanceIdentifiers()).thenReturn(INSTANCE_IDS);
    }

    @Test
    @WithMockUser(authorities = { "VIEW_XROAD_INSTANCES" })
    public void getMemberClassesForInstance() {
        ResponseEntity<Set<String>> response = xroadInstancesApiController.getXroadInstances();
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(INSTANCE_IDS, response.getBody());
    }
}
