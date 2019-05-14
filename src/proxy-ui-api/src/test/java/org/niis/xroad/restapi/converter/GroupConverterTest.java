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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.util.Date;

/**
 * Test GroupConverter
 */
@RunWith(SpringRunner.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class GroupConverterTest {

    @Autowired
    private GroupConverter groupConverter;

    @Autowired
    private ClientConverter clientConverter;

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

        groupConverter.convert(localGroupType);
    }

    @Test
    public void convertWithoutMembers() {
        LocalGroupType localGroupType = new LocalGroupType();

        localGroupType.setId(1L);
        localGroupType.setDescription("Local Group 1");
        localGroupType.setGroupCode("Local Group Code 1");
        localGroupType.setUpdated(new Date());

        groupConverter.convert(localGroupType);
    }
}
