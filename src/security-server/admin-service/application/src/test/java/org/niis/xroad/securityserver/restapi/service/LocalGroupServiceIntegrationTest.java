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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.conf.serverconf.model.ClientType;
import ee.ria.xroad.common.conf.serverconf.model.LocalGroupType;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.LocalGroupId;
import ee.ria.xroad.common.identifier.XRoadId;

import org.junit.Test;
import org.niis.xroad.securityserver.restapi.util.TestUtils;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.Collections;
import java.util.Date;

import static junit.framework.TestCase.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;

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
        LocalGroupType localGroupType = new LocalGroupType();
        localGroupType.setGroupCode(TestUtils.NEW_GROUPCODE);
        localGroupType.setDescription(TestUtils.GROUP_DESC);
        localGroupType.setUpdated(new Date());
        localGroupType = localGroupService.addLocalGroup(id, localGroupType);

        LocalGroupType localGroupTypeFromDb = localGroupService.getLocalGroup(localGroupType.getId());

        assertEquals(TestUtils.NEW_GROUPCODE, localGroupTypeFromDb.getGroupCode());
        assertEquals(TestUtils.GROUP_DESC, localGroupTypeFromDb.getDescription());
        assertEquals(0, localGroupTypeFromDb.getGroupMember().size());
        assertNotNull(localGroupTypeFromDb.getId());
    }

    @Test
    public void addDuplicateLocalGroup() throws Exception {
        ClientId id = TestUtils.getM1Ss1ClientId();
        LocalGroupType localGroupType = localGroupService.getLocalGroup(GROUP_ID);
        try {
            localGroupService.addLocalGroup(id, localGroupType);
            fail("should have thrown DuplicateLocalGroupCodeException");
        } catch (LocalGroupService.DuplicateLocalGroupCodeException expected) {
        }
    }

    @Test
    public void updateDescription() throws Exception {
        LocalGroupType localGroupType = localGroupService.getLocalGroup(GROUP_ID);
        assertEquals(localGroupType.getDescription(), FOO);
        localGroupService.updateDescription(GROUP_ID, TestUtils.NEW_GROUP_DESC);
        localGroupType = localGroupService.getLocalGroup(GROUP_ID);
        assertEquals(localGroupType.getDescription(), TestUtils.NEW_GROUP_DESC);
    }

    @Test
    public void localGroupsExist() {
        ClientType ss1 = clientService.getLocalClient(TestUtils.getM1Ss1ClientId());
        ClientType ss2 = clientService.getLocalClient(
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
        ClientType ss1 = clientService.getLocalClient(TestUtils.getM1Ss1ClientId());
        Long groupId = new Long(TestUtils.DB_LOCAL_GROUP_ID_1);
        XRoadId localGroupXroadId = localGroupService.getLocalGroupIdAsXroadId(groupId);

        assertTrue(ss1.getAcl().stream()
                .filter(acl -> acl.getSubjectId().equals(localGroupXroadId))
                .count() == TestUtils.GROUP1_ACCESS_RIGHTS_COUNT);

        localGroupService.deleteLocalGroup(groupId);

        // local group should be removed
        LocalGroupType localGroup = localGroupService.getLocalGroup(groupId);
        assertNull(localGroup);

        // access rights of the local group should be removed
        assertTrue(ss1.getAcl().stream().noneMatch(acl -> acl.getSubjectId().equals(localGroupXroadId)));
    }

}
