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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.LocalGroupId;

import org.assertj.core.api.Assertions;
import org.junit.Test;
import org.niis.xroad.globalconf.model.MemberInfo;
import org.niis.xroad.securityserver.restapi.util.TestUtils;
import org.niis.xroad.serverconf.impl.entity.ClientEntity;
import org.niis.xroad.serverconf.impl.entity.XRoadIdEntity;
import org.niis.xroad.serverconf.impl.mapper.XRoadIdMapper;
import org.niis.xroad.serverconf.model.GroupMember;
import org.niis.xroad.serverconf.model.LocalGroup;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.when;
import static org.niis.xroad.securityserver.restapi.util.TestUtils.CLIENT_ID_SS1;
import static org.niis.xroad.securityserver.restapi.util.TestUtils.DB_LOCAL_GROUP_ID_1;
import static org.niis.xroad.securityserver.restapi.util.TestUtils.getClientId;

/**
 * test LocalGroupService
 */
public class LocalGroupServiceIntegrationTest extends AbstractServiceIntegrationTestContext {

    @Autowired
    LocalGroupService localGroupService;

    @Autowired
    ClientService clientService;

    private static final Long GROUP_ID = 1L;
    private static final String FOO = "foo";

    @Test
    public void addLocalGroup() throws Exception {
        ClientId id = TestUtils.getM1Ss1ClientId();
        LocalGroup localGroup = new LocalGroup();
        localGroup.setGroupCode(TestUtils.NEW_GROUPCODE);
        localGroup.setDescription(TestUtils.GROUP_DESC);
        localGroup.setUpdated(new Date());
        localGroup = localGroupService.addLocalGroup(id, localGroup);

        LocalGroup localGroupFromDb = localGroupService.getLocalGroup(localGroup.getId());

        assertEquals(TestUtils.NEW_GROUPCODE, localGroupFromDb.getGroupCode());
        assertEquals(TestUtils.GROUP_DESC, localGroupFromDb.getDescription());
        assertEquals(0, localGroupFromDb.getGroupMembers().size());
        assertNotNull(localGroupFromDb.getId());
    }

    @Test
    public void addLocalClientToLocalGroup() {
        Long groupId = Long.valueOf(DB_LOCAL_GROUP_ID_1);

        localGroupService.addLocalGroupMembers(
                groupId, List.of(getClientId(CLIENT_ID_SS1)));

        LocalGroup localGroup = localGroupService.getLocalGroup(groupId);
        Assertions.assertThat(localGroup.getGroupMembers())
                .singleElement()
                .extracting(GroupMember::getGroupMemberId)
                .isEqualTo(getClientId(CLIENT_ID_SS1));
    }

    @Test
    public void addDuplicateClientToLocalGroup() {
        Long groupId = Long.valueOf(DB_LOCAL_GROUP_ID_1);
        localGroupService.addLocalGroupMembers(
                groupId, List.of(getClientId(CLIENT_ID_SS1)));

        Assertions.assertThatThrownBy(() ->
            localGroupService.addLocalGroupMembers(groupId, List.of(getClientId(CLIENT_ID_SS1))))
                .isInstanceOf(LocalGroupService.MemberAlreadyExistsException.class);

    }

    @Test
    public void addGlobalClientToLocalGroup() {
        Long groupId = Long.valueOf(DB_LOCAL_GROUP_ID_1);
        List<MemberInfo> members = List.of(
                // member that's not present in local identifiers table
                TestUtils.getMemberInfo(TestUtils.INSTANCE_FI, TestUtils.MEMBER_CLASS_PRO, TestUtils.MEMBER_CODE_M1, TestUtils.SUBSYSTEM1));
        when(globalConfProvider.getMembers()).thenReturn(new ArrayList<>(members));

        String globalClientId = "FI:PRO:M1:SS1";
        localGroupService.addLocalGroupMembers(
                groupId, List.of(getClientId(globalClientId)));

        LocalGroup localGroup = localGroupService.getLocalGroup(groupId);
        Assertions.assertThat(localGroup.getGroupMembers())
                .singleElement()
                .extracting(GroupMember::getGroupMemberId)
                .isEqualTo(getClientId(globalClientId));
    }

    @Test
    public void addNonExistentClientToLocalGroup() {
        Long groupId = Long.valueOf(DB_LOCAL_GROUP_ID_1);

        String nonExistentClientId = "FI:PRO:DOES-NOT-EXIST:SS1";
        Assertions.assertThatThrownBy(() -> localGroupService.addLocalGroupMembers(
                groupId, List.of(getClientId(nonExistentClientId))))
                .isInstanceOf(LocalGroupService.LocalGroupMemberNotFoundException.class);
    }

    @Test
    public void addDuplicateLocalGroup() throws Exception {
        ClientId id = TestUtils.getM1Ss1ClientId();
        LocalGroup localGroup = localGroupService.getLocalGroup(GROUP_ID);
        assertThrows(LocalGroupService.DuplicateLocalGroupCodeException.class, () -> localGroupService.addLocalGroup(id, localGroup));
    }

    @Test
    public void updateDescription() throws Exception {
        LocalGroup localGroup = localGroupService.getLocalGroup(GROUP_ID);
        assertEquals(FOO, localGroup.getDescription());
        localGroupService.updateDescription(GROUP_ID, TestUtils.NEW_GROUP_DESC);
        localGroup = localGroupService.getLocalGroup(GROUP_ID);
        assertEquals(TestUtils.NEW_GROUP_DESC, localGroup.getDescription());
    }

    @Test
    public void localGroupsExist() {
        ClientEntity ss1 = clientService.getLocalClientEntity(TestUtils.getM1Ss1ClientId());
        ClientEntity ss2 = clientService.getLocalClientEntity(
                ClientId.Conf.create("FI", "GOV", "M1", "SS2"));
        assertTrue(localGroupService.localGroupsExist(ss1,
                Collections.singletonList(LocalGroupId.Conf.create("group2"))));
        assertTrue(localGroupService.localGroupsExist(ss1,
                Arrays.asList(LocalGroupId.Conf.create("group1"), LocalGroupId.Conf.create("group2"))));
        assertTrue(localGroupService.localGroupsExist(ss1,
                Collections.singletonList(LocalGroupId.Conf.create("identifier-less-group"))));
        assertFalse(localGroupService.localGroupsExist(ss1,
                Collections.singletonList(LocalGroupId.Conf.create("nonexistent"))));
        assertFalse(localGroupService.localGroupsExist(ss2,
                Collections.singletonList(LocalGroupId.Conf.create("group2"))));
        assertFalse(localGroupService.localGroupsExist(ss1,
                Arrays.asList(LocalGroupId.Conf.create("group2"), LocalGroupId.Conf.create("nonexistent"))));
    }

    @Test
    public void deleteLocalGroup() throws Exception {
        ClientEntity ss1 = clientService.getLocalClientEntity(TestUtils.getM1Ss1ClientId());
        Long groupId = Long.valueOf(DB_LOCAL_GROUP_ID_1);
        XRoadIdEntity localGroupXroadId = XRoadIdMapper.get().toEntity(
                localGroupService.getLocalGroupIdAsXroadId(groupId)
        );

        assertEquals(TestUtils.GROUP1_ACCESS_RIGHTS_COUNT,
                ss1.getAccessRights().stream()
                        .filter(acl -> acl.getSubjectId().equals(localGroupXroadId))
                        .count()
        );

        localGroupService.deleteLocalGroup(groupId);

        // local group should be removed
        LocalGroup localGroup = localGroupService.getLocalGroup(groupId);
        assertNull(localGroup);

        // access rights of the local group should be removed
        assertTrue(ss1.getAccessRights().stream().noneMatch(acl -> acl.getSubjectId().equals(localGroupXroadId)));
    }

}
