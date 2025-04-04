/*
 * The MIT License
 *
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
package org.niis.xroad.securityserver.restapi.converter;

import ee.ria.xroad.common.identifier.ClientId;

import org.junit.Before;
import org.junit.Test;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.impl.GlobalConfImpl;
import org.niis.xroad.restapi.converter.ClientIdConverter;
import org.niis.xroad.securityserver.restapi.openapi.model.LocalGroupDto;
import org.niis.xroad.serverconf.model.GroupMember;
import org.niis.xroad.serverconf.model.LocalGroup;

import java.util.Date;

import static org.junit.Assert.assertEquals;

/**
 * Test LocalGroupConverter
 */
public class LocalGroupConverterTest extends AbstractConverterTestContext {

    public static final String MEMBER_NAME_PREFIX = "member-name-for-";
    public static final String SUBSYSTEM_NAME_PREFIX = "subsystem-name-for-";

    private LocalGroupConverter localGroupConverter;
    private final ClientIdConverter clientIdConverter = new ClientIdConverter();

    @Before
    public void setup() {
        GlobalConfProvider globalConfFacade = new GlobalConfImpl(null, null) {
            @Override
            public String getMemberName(ClientId identifier) {
                return MEMBER_NAME_PREFIX + identifier.getMemberCode();
            }

            @Override
            public String getSubsystemName(ClientId identifier) {
                return SUBSYSTEM_NAME_PREFIX + identifier.getSubsystemCode();
            }
        };
        localGroupConverter = new LocalGroupConverter(globalConfFacade);
    }

    @Test
    public void convertWithMembers() {
        ClientId clientId = clientIdConverter.convertId("XRD2:GOV:M4:SS1");
        LocalGroup localGroup = new LocalGroup();
        GroupMember groupMember = new GroupMember();

        groupMember.setId(1L);
        groupMember.setAdded(new Date());
        groupMember.setGroupMemberId(clientId);

        localGroup.setId(1L);
        localGroup.setDescription("Local Group 1");
        localGroup.setGroupCode("Local Group Code 1");
        localGroup.setUpdated(new Date());
        localGroup.getGroupMembers().add(groupMember);

        LocalGroupDto group = localGroupConverter.convert(localGroup);

        assertEquals(1, group.getMembers().size());
    }

    @Test
    public void convertWithoutMembers() {
        LocalGroup localGroup = new LocalGroup();

        localGroup.setId(1L);
        localGroup.setDescription("Local Group 1");
        localGroup.setGroupCode("Local Group Code 1");
        localGroup.setUpdated(new Date());

        localGroupConverter.convert(localGroup);

        LocalGroupDto group = localGroupConverter.convert(localGroup);

        assertEquals(0, group.getMembers().size());
    }
}
