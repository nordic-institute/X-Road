/*
 *  The MIT License
 *  Copyright (c) 2018 Estonian Information System Authority (RIA),
 *  Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 *  Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 *  Permission is hereby granted, free of charge, to any person obtaining a copy
 *  of this software and associated documentation files (the "Software"), to deal
 *  in the Software without restriction, including without limitation the rights
 *  to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 *  copies of the Software, and to permit persons to whom the Software is
 *  furnished to do so, subject to the following conditions:
 *
 *  The above copyright notice and this permission notice shall be included in
 *  all copies or substantial portions of the Software.
 *
 *  THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *  IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 *  FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 *  AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 *  LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 *  OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 *  THE SOFTWARE.
 */
package org.niis.xroad.restapi.service;

import ee.ria.xroad.common.conf.globalconf.GlobalGroupInfo;
import ee.ria.xroad.common.conf.globalconf.MemberInfo;

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.niis.xroad.restapi.dto.AccessRightHolderDto;
import org.niis.xroad.restapi.util.TestUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * test Service service
 */
@RunWith(SpringRunner.class)
@SpringBootTest
@AutoConfigureTestDatabase
@Slf4j
@Transactional
public class ServiceServiceIntegrationTest {

    private static final String INSTANCE_FI = "FI";
    private static final String INSTANCE_EE = "EE";
    private static final String MEMBER_CLASS_GOV = "GOV";
    private static final String MEMBER_CODE_M1 = "M1";
    private static final String SUBSYSTEM1 = "SS1";
    private static final String GLOBALGROUP_CODE = "GlobalGroup";
    private static final String GLOBALGROUP_CODE1 = "GlobalGroup 1";
    private List<MemberInfo> memberInfos = new ArrayList<>(Arrays.asList(
            TestUtils.getMemberInfo(INSTANCE_FI, MEMBER_CLASS_GOV, MEMBER_CODE_M1, null),
            TestUtils.getMemberInfo(INSTANCE_EE, MEMBER_CLASS_GOV, MEMBER_CODE_M1, SUBSYSTEM1),
            TestUtils.getMemberInfo(INSTANCE_FI, MEMBER_CLASS_GOV, MEMBER_CODE_M1, SUBSYSTEM1)));
    private List<GlobalGroupInfo> globalGroupInfos = new ArrayList<>(Arrays.asList(
            TestUtils.getGlobalGroupInfo(INSTANCE_FI, GLOBALGROUP_CODE),
            TestUtils.getGlobalGroupInfo(INSTANCE_FI, GLOBALGROUP_CODE1),
            TestUtils.getGlobalGroupInfo(INSTANCE_EE, GLOBALGROUP_CODE)));
    private List<String> instanceIdentifiers = new ArrayList<>(Arrays.asList(
            INSTANCE_FI,
            INSTANCE_EE));

    @Autowired
    ServiceService serviceService;

    @MockBean
    GlobalConfService globalConfService;

    @Before
    public void setup() {
        when(globalConfService.getGlobalMembers()).thenReturn(memberInfos);
        when(globalConfService.getInstanceIdentifier()).thenReturn(INSTANCE_FI);
        when(globalConfService.getInstanceIdentifiers()).thenReturn(instanceIdentifiers);
        when(globalConfService.getGlobalGroups(any())).thenAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            if (args.length == 0) {
                return globalGroupInfos;
            }
            List<String> foundInstanceIdentifiers = new ArrayList<>(Arrays.asList(
                    Arrays.copyOf(args, args.length, String[].class)));
            return globalGroupInfos.stream()
                    .filter(globalGroupInfo -> foundInstanceIdentifiers
                            .contains(globalGroupInfo.getId().getXRoadInstance()))
                    .collect(Collectors.toList());
        });
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_ACL_SUBJECTS", "VIEW_CLIENTS", "VIEW_MEMBER_CLASSES" })
    public void findAllAccessRightHolders() {
        List<AccessRightHolderDto> dtos = serviceService.findAccessRightHolders(TestUtils.getM1Ss1ClientId(), null,
                null, null, null, null, null);
        assertEquals(7, dtos.size());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_ACL_SUBJECTS", "VIEW_CLIENTS", "VIEW_MEMBER_CLASSES" })
    public void findAccessRightHoldersByMemberOrGroupCode() {
        List<AccessRightHolderDto> dtos = serviceService.findAccessRightHolders(TestUtils.getM1Ss1ClientId(), null,
                null, null, null, "1", null);
        assertEquals(4, dtos.size());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_ACL_SUBJECTS", "VIEW_CLIENTS", "VIEW_MEMBER_CLASSES" })
    public void findAccessRightHoldersByMemberOrGroupCodeNoResults() {
        List<AccessRightHolderDto> dtos = serviceService.findAccessRightHolders(TestUtils.getM1Ss1ClientId(), null,
                null, null, null, "öäöäöäöäöäöä", null);
        assertEquals(0, dtos.size());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_ACL_SUBJECTS", "VIEW_CLIENTS", "VIEW_MEMBER_CLASSES" })
    public void findAccessRightHoldersByInstance() {
        List<AccessRightHolderDto> dtos = serviceService.findAccessRightHolders(TestUtils.getM1Ss1ClientId(), null,
                null, INSTANCE_EE, null, null, null);
        assertEquals(4, dtos.size());
    }

    @Test
    @WithMockUser(authorities = { "VIEW_CLIENT_ACL_SUBJECTS", "VIEW_CLIENTS", "VIEW_MEMBER_CLASSES" })
    public void findAccessRightHoldersByInstanceAndSubSystem() {
        List<AccessRightHolderDto> dtos = serviceService.findAccessRightHolders(TestUtils.getM1Ss1ClientId(), null,
                null, INSTANCE_FI, null, null, SUBSYSTEM1);
        assertEquals(1, dtos.size());
    }
}
