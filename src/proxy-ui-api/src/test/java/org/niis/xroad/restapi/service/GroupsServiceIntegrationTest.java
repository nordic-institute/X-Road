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
package org.niis.xroad.restapi.service;

import ee.ria.xroad.common.conf.serverconf.model.LocalGroupType;
import ee.ria.xroad.common.identifier.ClientId;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.niis.xroad.restapi.exceptions.ConflictException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

/**
 * test groups service
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase
@Slf4j
@Transactional
public class GroupsServiceIntegrationTest {

    private static final String GROUPCODE = "group1";
    private static final String NEW_GROUPCODE = "groupX";
    private static final String GROUP_DESC = "foo";
    private static final String NEW_GROUP_DESC = "bar";

    @Autowired
    private GroupService groupsService;

    private ClientId getM1Ss1ClientId() {
        return ClientId.create("FI", "GOV", "M1", "SS1");
    }

    @Test
    @WithMockUser(authorities = { "ADD_LOCAL_GROUP", "VIEW_CLIENT_LOCAL_GROUPS" })
    public void addLocalGroup() {
        ClientId id = getM1Ss1ClientId();
        LocalGroupType localGroupType = new LocalGroupType();
        localGroupType.setGroupCode(NEW_GROUPCODE);
        localGroupType.setDescription(GROUP_DESC);
        localGroupType.setUpdated(new Date());
        groupsService.addLocalGroup(id, localGroupType);

        localGroupType = groupsService.getLocalGroup(NEW_GROUPCODE, id);

        assertEquals(NEW_GROUPCODE, localGroupType.getGroupCode());
        assertEquals(GROUP_DESC, localGroupType.getDescription());
        assertEquals(0, localGroupType.getGroupMember().size());
        assertNotNull(localGroupType.getId());
    }

    @Test
    @WithMockUser(authorities = { "ADD_LOCAL_GROUP", "VIEW_CLIENT_LOCAL_GROUPS" })
    public void addDuplicateLocalGroup() {
        ClientId id = getM1Ss1ClientId();
        LocalGroupType localGroupType = groupsService.getLocalGroup(GROUPCODE, id);
        try {
            groupsService.addLocalGroup(id, localGroupType);
            groupsService.addLocalGroup(id, localGroupType);
            fail("should have thrown ConflictException");
        } catch (ConflictException expected) {
        }
    }

    @Test
    @WithMockUser(authorities = { "ADD_LOCAL_GROUP", "VIEW_CLIENT_LOCAL_GROUPS", "EDIT_LOCAL_GROUP_DESC" })
    public void updateDescription() {
        LocalGroupType localGroupType = groupsService.getLocalGroup(GROUPCODE, getM1Ss1ClientId());
        assertEquals(localGroupType.getDescription(), GROUP_DESC);
        groupsService.updateDescription(getM1Ss1ClientId(), GROUPCODE, NEW_GROUP_DESC);
        localGroupType = groupsService.getLocalGroup(GROUPCODE, getM1Ss1ClientId());
        assertEquals(localGroupType.getDescription(), NEW_GROUP_DESC);
    }
}
