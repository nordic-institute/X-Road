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
import org.niis.xroad.restapi.util.TestUtils;
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
 * test LocalGroupService
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureTestDatabase
@Slf4j
@Transactional
public class LocalGroupServiceIntegrationTest {

    private static final Long GROUP_ID = 1L;

    @Autowired
    private LocalGroupService localGroupService;

    private ClientId getM1Ss1ClientId() {
        return ClientId.create("FI", "GOV", "M1", "SS1");
    }

    @Test
    @WithMockUser(authorities = { "ADD_LOCAL_GROUP", "VIEW_CLIENT_LOCAL_GROUPS" })
    public void addLocalGroup() throws Exception {
        ClientId id = getM1Ss1ClientId();
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
    @WithMockUser(authorities = { "ADD_LOCAL_GROUP", "VIEW_CLIENT_LOCAL_GROUPS" })
    public void addDuplicateLocalGroup() throws Exception {
        ClientId id = getM1Ss1ClientId();
        LocalGroupType localGroupType = localGroupService.getLocalGroup(GROUP_ID);
        try {
            localGroupService.addLocalGroup(id, localGroupType);
            localGroupService.addLocalGroup(id, localGroupType);
            fail("should have thrown DuplicateLocalGroupCodeException");
        } catch (LocalGroupService.DuplicateLocalGroupCodeException expected) {
        }
    }

    @Test
    @WithMockUser(authorities = { "ADD_LOCAL_GROUP", "VIEW_CLIENT_LOCAL_GROUPS", "EDIT_LOCAL_GROUP_DESC" })
    public void updateDescription() throws Exception {
        LocalGroupType localGroupType = localGroupService.getLocalGroup(GROUP_ID);
        assertEquals(localGroupType.getDescription(), TestUtils.FOO);
        localGroupService.updateDescription(GROUP_ID, TestUtils.NEW_GROUP_DESC);
        localGroupType = localGroupService.getLocalGroup(GROUP_ID);
        assertEquals(localGroupType.getDescription(), TestUtils.NEW_GROUP_DESC);
    }
}
