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

import ee.ria.xroad.common.identifier.ClientId;

import org.junit.Before;
import org.junit.Test;
import org.mockito.stubbing.Answer;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.restapi.openapi.ConflictException;
import org.niis.xroad.restapi.openapi.ResourceNotFoundException;
import org.niis.xroad.securityserver.restapi.openapi.model.LocalGroup;
import org.niis.xroad.securityserver.restapi.openapi.model.LocalGroupDescription;
import org.niis.xroad.securityserver.restapi.openapi.model.Members;
import org.niis.xroad.securityserver.restapi.util.TestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static junit.framework.TestCase.fail;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.niis.xroad.securityserver.restapi.util.TestUtils.assertMissingLocationHeader;

/**
 * Test LocalGroupsApiController
 */
public class LocalGroupsApiControllerIntegrationTest extends AbstractApiControllerTestContext {

    @Autowired
    LocalGroupsApiController localGroupsApiController;

    @Before
    public void setup() {
        when(globalConfFacade.getMemberName(any())).thenAnswer((Answer<String>) invocation -> {
            Object[] args = invocation.getArguments();
            ClientId identifier = (ClientId) args[0];
            return identifier.getSubsystemCode() != null ? TestUtils.NAME_FOR + identifier.getSubsystemCode()
                    : TestUtils.NAME_FOR + "test-member";
        });
        when(globalConfFacade.getMembers(any())).thenReturn(new ArrayList<>(Arrays.asList(
                TestUtils.getMemberInfo(TestUtils.INSTANCE_FI, TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M1,
                        null),
                TestUtils.getMemberInfo(TestUtils.INSTANCE_FI, TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M1,
                        TestUtils.SUBSYSTEM1),
                TestUtils.getMemberInfo(TestUtils.INSTANCE_FI, TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M1,
                        TestUtils.SUBSYSTEM2),
                TestUtils.getMemberInfo(TestUtils.INSTANCE_EE, TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M2,
                        TestUtils.SUBSYSTEM3),
                TestUtils.getMemberInfo(TestUtils.INSTANCE_EE, TestUtils.MEMBER_CLASS_GOV, TestUtils.MEMBER_CODE_M1,
                        null),
                TestUtils.getMemberInfo(TestUtils.INSTANCE_EE, TestUtils.MEMBER_CLASS_PRO, TestUtils.MEMBER_CODE_M1,
                        TestUtils.SUBSYSTEM1),
                TestUtils.getMemberInfo(TestUtils.INSTANCE_EE, TestUtils.MEMBER_CLASS_PRO, TestUtils.MEMBER_CODE_M2,
                        null))
        ));
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_LOCAL_GROUPS" })
    public void getLocalGroup() throws Exception {
        ResponseEntity<LocalGroup> response =
                localGroupsApiController.getLocalGroup(TestUtils.DB_LOCAL_GROUP_ID_1);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        try {
            localGroupsApiController.getLocalGroup(TestUtils.INVALID_GROUP_ID);
            fail("should throw ResourceNotFoundException");
        } catch (NotFoundException expected) {
            // nothing should be found
        }
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_LOCAL_GROUPS", "EDIT_LOCAL_GROUP_DESC" })
    public void updateGroup() throws Exception {
        localGroupsApiController.updateLocalGroup(TestUtils.DB_LOCAL_GROUP_ID_1,
                new LocalGroupDescription().description(TestUtils.GROUP_DESC));
        ResponseEntity<LocalGroup> response =
                localGroupsApiController.getLocalGroup(TestUtils.DB_LOCAL_GROUP_ID_1);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(TestUtils.GROUP_DESC, response.getBody().getDescription());
    }

    @Test
    @WithMockUser(authorities = { "DELETE_LOCAL_GROUP", "VIEW_CLIENT_LOCAL_GROUPS" })
    public void deleteLocalGroup() throws Exception {
        ResponseEntity<Void> response =
                localGroupsApiController.deleteLocalGroup(TestUtils.DB_LOCAL_GROUP_ID_1);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());

        try {
            localGroupsApiController.getLocalGroup(TestUtils.DB_LOCAL_GROUP_ID_1);
            fail("should throw ResourceNotFoundException");
        } catch (ResourceNotFoundException expected) {
            // success
        }
        // Local group access right removal is tested in service tests
    }

    @Test
    @WithMockUser(authorities = { "EDIT_LOCAL_GROUP_MEMBERS", "VIEW_CLIENT_LOCAL_GROUPS" })
    public void addGroupMember() throws Exception {
        ResponseEntity<Members> response =
                localGroupsApiController.addLocalGroupMember(TestUtils.DB_LOCAL_GROUP_ID_1,
                        new Members().items(Collections.singletonList(TestUtils.CLIENT_ID_SS2)));

        List<String> addedMembers = response.getBody().getItems();
        assertEquals(Collections.singletonList(TestUtils.CLIENT_ID_SS2), addedMembers);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertMissingLocationHeader(response);

        ResponseEntity<LocalGroup> localGroupResponse = localGroupsApiController.getLocalGroup(
                TestUtils.DB_LOCAL_GROUP_ID_1);
        assertEquals(1, localGroupResponse.getBody().getMembers().size());
    }

    @Test
    @WithMockUser(authorities = { "EDIT_LOCAL_GROUP_MEMBERS", "VIEW_CLIENT_LOCAL_GROUPS" })
    public void addMultipleGroupMembers() throws Exception {
        List<String> membersToBeAdded = Arrays.asList(TestUtils.CLIENT_ID_SS1, TestUtils.CLIENT_ID_SS2,
                TestUtils.CLIENT_ID_SS1, TestUtils.CLIENT_ID_SS2);
        ResponseEntity<Members> response = localGroupsApiController.addLocalGroupMember(TestUtils.DB_LOCAL_GROUP_ID_1,
                new Members().items(membersToBeAdded));
        List<String> addedMembers = response.getBody().getItems();
        assertEquals(membersToBeAdded, addedMembers);
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        assertMissingLocationHeader(response);

        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        ResponseEntity<LocalGroup> localGroupResponse = localGroupsApiController.getLocalGroup(
                TestUtils.DB_LOCAL_GROUP_ID_1);
        assertEquals(2, localGroupResponse.getBody().getMembers().size());
    }

    @Test
    @WithMockUser(authorities = { "EDIT_LOCAL_GROUP_MEMBERS", "VIEW_CLIENT_LOCAL_GROUPS" })
    public void addDuplicateGroupMember() throws Exception {
        List<String> membersToBeAdded = Arrays.asList(TestUtils.CLIENT_ID_SS1, TestUtils.CLIENT_ID_SS2);
        ResponseEntity<Members> response = localGroupsApiController.addLocalGroupMember(TestUtils.DB_LOCAL_GROUP_ID_1,
                new Members().items(membersToBeAdded));
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        ResponseEntity<LocalGroup> localGroupResponse = localGroupsApiController.getLocalGroup(
                TestUtils.DB_LOCAL_GROUP_ID_1);
        assertEquals(2, localGroupResponse.getBody().getMembers().size());
        try {
            localGroupsApiController.addLocalGroupMember(TestUtils.DB_LOCAL_GROUP_ID_1,
                    new Members().items(Collections.singletonList(TestUtils.CLIENT_ID_SS2)));
            fail("should throw ConflictException");
        } catch (ConflictException expected) {
            // expected exception
        }
    }

    @Test
    @WithMockUser(authorities = { "EDIT_LOCAL_GROUP_MEMBERS", "VIEW_CLIENT_LOCAL_GROUPS" })
    public void deleteGroupMember() throws Exception {
        ResponseEntity<Members> response =
                localGroupsApiController.addLocalGroupMember(TestUtils.DB_LOCAL_GROUP_ID_1, new Members()
                        .items(Collections.singletonList(TestUtils.CLIENT_ID_SS2)));
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        ResponseEntity<Void> deleteResponse = localGroupsApiController.deleteLocalGroupMember(
                TestUtils.DB_LOCAL_GROUP_ID_1,
                new Members().items(Collections.singletonList(TestUtils.CLIENT_ID_SS2)));
        assertEquals(HttpStatus.NO_CONTENT, deleteResponse.getStatusCode());
        ResponseEntity<LocalGroup> localGroupResponse = localGroupsApiController.getLocalGroup(
                TestUtils.DB_LOCAL_GROUP_ID_1);
        assertEquals(0, localGroupResponse.getBody().getMembers().size());
    }
}
