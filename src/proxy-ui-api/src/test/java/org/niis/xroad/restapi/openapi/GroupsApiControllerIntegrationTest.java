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

import ee.ria.xroad.common.identifier.ClientId;

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.stubbing.Answer;
import org.niis.xroad.restapi.converter.GlobalConfWrapper;
import org.niis.xroad.restapi.exceptions.ConflictException;
import org.niis.xroad.restapi.exceptions.NotFoundException;
import org.niis.xroad.restapi.openapi.model.Group;
import org.niis.xroad.restapi.openapi.model.Members;
import org.niis.xroad.restapi.util.TestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Test GroupsApiController
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase
@Transactional
@Slf4j
public class GroupsApiControllerIntegrationTest {
    private static final String GROUP_ID = "1";
    public static final String CLIENT_ID_SS1 = "FI:GOV:M1:SS1";
    public static final String CLIENT_ID_SS2 = "FI:GOV:M1:SS2";
    public static final String GROUP_DESC = "GROUP_DESC";
    public static final String NAME_APPENDIX = "-name";
    private static final String INSTANCE_FI = "FI";
    private static final String INSTANCE_EE = "EE";
    private static final String MEMBER_CLASS_GOV = "GOV";
    private static final String MEMBER_CLASS_PRO = "PRO";
    private static final String MEMBER_CODE_M1 = "M1";
    private static final String MEMBER_CODE_M2 = "M2";
    private static final String SUBSYSTEM1 = "SS1";
    private static final String SUBSYSTEM2 = "SS2";
    private static final String SUBSYSTEM3 = "SS3";

    @Autowired
    private GroupsApiController groupsApiController;

    @MockBean
    private GlobalConfWrapper globalConfWrapper;

    @Before
    public void setup() {
        when(globalConfWrapper.getMemberName(any())).thenAnswer((Answer<String>) invocation -> {
            Object[] args = invocation.getArguments();
            ClientId identifier = (ClientId) args[0];
            return identifier.getSubsystemCode() != null ? identifier.getSubsystemCode() + NAME_APPENDIX
                    : "test-member" + NAME_APPENDIX;
        });
        when(globalConfWrapper.getGlobalMembers(any())).thenReturn(new ArrayList<>(Arrays.asList(
                TestUtils.getMemberInfo(INSTANCE_FI, MEMBER_CLASS_GOV, MEMBER_CODE_M1, null),
                TestUtils.getMemberInfo(INSTANCE_FI, MEMBER_CLASS_GOV, MEMBER_CODE_M1, SUBSYSTEM1),
                TestUtils.getMemberInfo(INSTANCE_FI, MEMBER_CLASS_GOV, MEMBER_CODE_M1, SUBSYSTEM2),
                TestUtils.getMemberInfo(INSTANCE_EE, MEMBER_CLASS_GOV, MEMBER_CODE_M2, SUBSYSTEM3),
                TestUtils.getMemberInfo(INSTANCE_EE, MEMBER_CLASS_GOV, MEMBER_CODE_M1, null),
                TestUtils.getMemberInfo(INSTANCE_EE, MEMBER_CLASS_PRO, MEMBER_CODE_M1, SUBSYSTEM1),
                TestUtils.getMemberInfo(INSTANCE_EE, MEMBER_CLASS_PRO, MEMBER_CODE_M2, null))
        ));
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_DETAILS", "VIEW_CLIENT_LOCAL_GROUPS", "ADD_LOCAL_GROUP" })
    public void getLocalGroup() throws Exception {
        ResponseEntity<Group> response =
                groupsApiController.getGroup(GROUP_ID);
        assertEquals(HttpStatus.OK, response.getStatusCode());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_DETAILS", "VIEW_CLIENT_LOCAL_GROUPS", "EDIT_LOCAL_GROUP_DESC" })
    public void updateGroup() throws Exception {
        groupsApiController.updateGroup(GROUP_ID, GROUP_DESC);
        ResponseEntity<Group> response =
                groupsApiController.getGroup(GROUP_ID);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(GROUP_DESC, response.getBody().getDescription());
    }

    @Test
    @WithMockUser(authorities = { "DELETE_LOCAL_GROUP", "VIEW_CLIENT_DETAILS", "VIEW_CLIENT_LOCAL_GROUPS" })
    public void deleteLocalGroup() throws Exception {
        ResponseEntity<Void> response =
                groupsApiController.deleteGroup(GROUP_ID);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        try {
            groupsApiController.getGroup(GROUP_ID);
        } catch (NotFoundException expected) {
            // success
        }
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENTS", "VIEW_CLIENT_LOCAL_GROUPS", "EDIT_LOCAL_GROUP_MEMBERS" })
    public void addGroupMember() throws Exception {
        ResponseEntity<Void> response =
                groupsApiController.addGroupMember(GROUP_ID,
                        new Members().items(Collections.singletonList(CLIENT_ID_SS2)));
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        ResponseEntity<Group> localGroupResponse = groupsApiController.getGroup(GROUP_ID);
        assertEquals(1, localGroupResponse.getBody().getMembers().size());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENTS", "VIEW_CLIENT_LOCAL_GROUPS", "EDIT_LOCAL_GROUP_MEMBERS" })
    public void addMultipleGroupMembers() throws Exception {
        List<String> membersToBeAdded = Arrays.asList(CLIENT_ID_SS1, CLIENT_ID_SS2, CLIENT_ID_SS1, CLIENT_ID_SS2);
        ResponseEntity<Void> response = groupsApiController.addGroupMember(GROUP_ID,
                new Members().items(membersToBeAdded));
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        ResponseEntity<Group> localGroupResponse = groupsApiController.getGroup(GROUP_ID);
        assertEquals(2, localGroupResponse.getBody().getMembers().size());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENTS", "VIEW_CLIENT_LOCAL_GROUPS", "EDIT_LOCAL_GROUP_MEMBERS" })
    public void addDuplicateGroupMember() throws Exception {
        List<String> membersToBeAdded = Arrays.asList(CLIENT_ID_SS1, CLIENT_ID_SS2);
        ResponseEntity<Void> response = groupsApiController.addGroupMember(GROUP_ID,
                new Members().items(membersToBeAdded));
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        ResponseEntity<Group> localGroupResponse = groupsApiController.getGroup(GROUP_ID);
        assertEquals(2, localGroupResponse.getBody().getMembers().size());
        try {
            groupsApiController.addGroupMember(GROUP_ID,
                    new Members().items(Collections.singletonList(CLIENT_ID_SS2)));
        } catch (ConflictException expected) {
            // expected exception
        }
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENTS", "VIEW_CLIENT_LOCAL_GROUPS", "EDIT_LOCAL_GROUP_MEMBERS" })
    public void deleteGroupMember() throws Exception {
        ResponseEntity<Void> response =
                groupsApiController.addGroupMember(GROUP_ID, new Members()
                        .items(Collections.singletonList(CLIENT_ID_SS2)));
        assertEquals(HttpStatus.CREATED, response.getStatusCode());
        ResponseEntity<Void> deleteResponse = groupsApiController.deleteGroupMember(GROUP_ID,
                        new Members().items(Collections.singletonList(CLIENT_ID_SS2)));
        assertEquals(HttpStatus.CREATED, deleteResponse.getStatusCode());
        ResponseEntity<Group> localGroupResponse = groupsApiController.getGroup(GROUP_ID);
        assertEquals(0, localGroupResponse.getBody().getMembers().size());
    }
}
