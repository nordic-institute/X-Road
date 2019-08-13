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
package org.niis.xroad.restapi.converter;

import ee.ria.xroad.common.conf.serverconf.model.GroupMemberType;
import ee.ria.xroad.common.conf.serverconf.model.LocalGroupType;
import ee.ria.xroad.common.identifier.ClientId;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.niis.xroad.restapi.openapi.model.Group;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Date;

import static org.junit.Assert.assertEquals;

/**
 * Test GroupConverter
 */
@RunWith(SpringRunner.class)
@SpringBootTest
public class GroupConverterTest {

    public static final String MEMBER_NAME_PREFIX = "member-name-for-";

    private GroupConverter groupConverter;
    private ClientConverter clientConverter;

    @Before
    public void setup() {
        GlobalConfWrapper globalConfWrapper = new GlobalConfWrapper() {
            @Override
            public String getMemberName(ClientId identifier) {
                return MEMBER_NAME_PREFIX + identifier.getMemberCode();
            }
        };
        clientConverter = new ClientConverter(globalConfWrapper);
        groupConverter = new GroupConverter(clientConverter, globalConfWrapper);
    }

    @Test
    public void convertWithMembers() {
        ClientId clientId = clientConverter.convertId("XRD2:GOV:M4:SS1");
        LocalGroupType localGroupType = new LocalGroupType();
        GroupMemberType groupMemberType = new GroupMemberType();

        groupMemberType.setId(1L);
        groupMemberType.setAdded(new Date());
        groupMemberType.setGroupMemberId(clientId);

        localGroupType.setId(1L);
        localGroupType.setDescription("Local Group 1");
        localGroupType.setGroupCode("Local Group Code 1");
        localGroupType.setUpdated(new Date());
        localGroupType.getGroupMember().add(groupMemberType);

        Group group = groupConverter.convert(localGroupType);

        assertEquals(1, group.getMembers().size());
    }

    @Test
    public void convertWithoutMembers() {
        LocalGroupType localGroupType = new LocalGroupType();

        localGroupType.setId(1L);
        localGroupType.setDescription("Local Group 1");
        localGroupType.setGroupCode("Local Group Code 1");
        localGroupType.setUpdated(new Date());

        groupConverter.convert(localGroupType);

        Group group = groupConverter.convert(localGroupType);

        assertEquals(0, group.getMembers().size());
    }
}
