/**
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
import org.mockito.stubbing.Answer;
import org.niis.xroad.restapi.openapi.ResourceNotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static junit.framework.TestCase.fail;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * test member classes api controller
 */
public class MemberClassesApiControllerIntegrationTest extends AbstractApiControllerTestContext {

    @Autowired
    MemberClassesApiController memberClassesApiController;

    private static final String INSTANCE_A = "instance_a";
    private static final String INSTANCE_B = "instance_b";
    private static final String INSTANCE_C = "instance_c";
    private static final Set<String> INSTANCE_IDS = new HashSet<>(
            Arrays.asList(INSTANCE_A, INSTANCE_B, INSTANCE_C));

    private static final List<String> A_MEMBER_CLASSES = Arrays.asList("CODE1", "CODE2");
    private static final List<String> B_MEMBER_CLASSES = Arrays.asList("CODE3", "CODE2");
    private static final Set<String> UNION_MEMBER_CLASSES = new HashSet<>(A_MEMBER_CLASSES);

    static {
        UNION_MEMBER_CLASSES.addAll(B_MEMBER_CLASSES);
    }

    @Before
    public void setup() throws Exception {
        Map<String, List<String>> instanceMemberClasses = new HashMap<>();
        instanceMemberClasses.put(INSTANCE_A, A_MEMBER_CLASSES);
        instanceMemberClasses.put(INSTANCE_B, B_MEMBER_CLASSES);
        instanceMemberClasses.put(INSTANCE_C, new ArrayList<>());
        when(globalConfFacade.getMemberClasses(anyString()))
                .thenAnswer((Answer<Set<String>>) invocation -> {
                    List<String> classes = instanceMemberClasses.get(invocation.getArgument(0));
                    if (classes == null) {
                        return new HashSet<>();
                    }
                    return new HashSet(classes);
                });

        when(globalConfFacade.getMemberClasses())
                .thenReturn(UNION_MEMBER_CLASSES);

        when(globalConfFacade.getInstanceIdentifiers())
                .thenReturn(INSTANCE_IDS);

        when(globalConfService.getMemberClassesForThisInstance())
                .thenReturn(new HashSet<>(A_MEMBER_CLASSES));
    }

    @Test
    @WithMockUser(authorities = { "VIEW_MEMBER_CLASSES" })
    public void getMemberClassesForInstance() {
        ResponseEntity<Set<String>> response =
                memberClassesApiController.getMemberClassesForInstance(INSTANCE_A);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertThat("List equality without order",
                response.getBody(), containsInAnyOrder(A_MEMBER_CLASSES.toArray()));

        response = memberClassesApiController.getMemberClassesForInstance(INSTANCE_B);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertThat("List equality without order",
                response.getBody(), containsInAnyOrder(B_MEMBER_CLASSES.toArray()));

        response = memberClassesApiController.getMemberClassesForInstance(INSTANCE_C);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(new HashSet<>(), response.getBody());

        try {
            memberClassesApiController.getMemberClassesForInstance("instance which does not exist");
            fail("should throw ResourceNotFoundException");
        } catch (ResourceNotFoundException expected) {
            // nothing should be found
        }
    }

    @Test
    @WithMockUser(authorities = { "VIEW_MEMBER_CLASSES" })
    public void getMemberClasses() {
        ResponseEntity<Set<String>> response =
                memberClassesApiController.getMemberClasses(true);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertThat("List equality without order",
                response.getBody(), containsInAnyOrder(A_MEMBER_CLASSES.toArray()));

        response = memberClassesApiController.getMemberClasses(false);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertThat("List equality without order",
                response.getBody(), containsInAnyOrder(UNION_MEMBER_CLASSES.toArray()));
    }
}
