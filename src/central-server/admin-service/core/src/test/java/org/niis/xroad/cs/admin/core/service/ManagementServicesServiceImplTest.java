/**
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.cs.admin.core.service;

import ee.ria.xroad.common.identifier.ClientId;

import io.vavr.control.Option;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.cs.admin.api.domain.MemberClass;
import org.niis.xroad.cs.admin.api.domain.SecurityServer;
import org.niis.xroad.cs.admin.api.domain.SecurityServerId;
import org.niis.xroad.cs.admin.api.domain.ServerClient;
import org.niis.xroad.cs.admin.api.domain.Subsystem;
import org.niis.xroad.cs.admin.api.domain.XRoadMember;
import org.niis.xroad.cs.admin.api.service.MemberService;
import org.niis.xroad.cs.admin.api.service.SubsystemService;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.AssertionsForInterfaceTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ManagementServicesServiceImplTest {
    private static final String INSTANCE = "instance";
    private static final String MEMBER_CLASS = "memberclass";
    private static final String MEMBER_CODE = "membercode";
    private static final String SUBSYSTEM_CODE = "subsystemcode";

    private static final String SECURITY_SERVER_GRP = "security-group";
    private static final String MEMBER_NAME = "name";


    @Mock
    private SystemParameterService systemParameterService;
    @Mock
    private MemberService memberService;
    @Mock
    private SubsystemService subsystemService;

    @Mock
    private Subsystem subsystem;

    @InjectMocks
    private ManagementServicesServiceImpl managementServicesService;

    private final ClientId clientId = ClientId.Conf.create(INSTANCE, MEMBER_CLASS, MEMBER_CODE);
    private XRoadMember xRoadMember;

    @BeforeEach
    void setUp() {
        when(systemParameterService.getCentralServerAddress()).thenReturn("cs");
        when(systemParameterService.getSecurityServerOwnersGroup()).thenReturn(SECURITY_SERVER_GRP);

        xRoadMember = new XRoadMember(MEMBER_NAME, clientId, new MemberClass(MEMBER_CLASS, ""));
    }

    @Test
    void shouldReturnConfigurationFromSubsystem() {
        var serviceProviderClientId = ClientId.Conf.create(INSTANCE, MEMBER_CLASS, MEMBER_CODE, SUBSYSTEM_CODE);
        when(systemParameterService.getManagementServiceProviderId()).thenReturn(serviceProviderClientId);
        when(memberService.findMember(serviceProviderClientId)).thenReturn(Option.of(xRoadMember));

        when(subsystemService.findByIdentifier(serviceProviderClientId)).thenReturn(Optional.of(subsystem));
        var serverClient = new ServerClient();
        serverClient.setServerId(SecurityServerId.create(serviceProviderClientId, "SS0"));
        when(subsystem.getServerClients()).thenReturn(Set.of(serverClient));


        var result = managementServicesService.getManagementServicesConfiguration();

        assertThat(result.getServicesAddress()).isEqualTo("https://cs:4002/managementservice/manage/");
        assertThat(result.getWsdlAddress()).isEqualTo("http://cs/managementservices.wsdl");

        assertThat(result.getSecurityServerOwnersGlobalGroupCode()).isEqualTo(SECURITY_SERVER_GRP);
        assertThat(result.getServiceProviderId()).isEqualTo("SUBSYSTEM:instance:memberclass:membercode:subsystemcode");
        assertThat(result.getServiceProviderName()).isEqualTo(MEMBER_NAME);
        assertThat(result.getSecurityServerId()).isEqualTo("SERVER:instance:memberclass:membercode:SS0");
    }

    @Test
    void shouldReturnConfigurationFromMember() {

        when(systemParameterService.getManagementServiceProviderId()).thenReturn(clientId);
        when(memberService.findMember(clientId)).thenReturn(Option.of(xRoadMember));

        var serverClient0 = new ServerClient();
        serverClient0.setServerId(SecurityServerId.create(clientId, "SS0"));
        xRoadMember.setServerClients(Set.of(serverClient0));

        when(memberService.getMemberOwnedServers(any())).thenReturn(Set.of(new SecurityServer(xRoadMember, "SS1")));

        var result = managementServicesService.getManagementServicesConfiguration();

        verify(subsystemService, never()).findByIdentifier(clientId);

        assertThat(result.getServicesAddress()).isEqualTo("https://cs:4002/managementservice/manage/");
        assertThat(result.getWsdlAddress()).isEqualTo("http://cs/managementservices.wsdl");

        assertThat(result.getSecurityServerOwnersGlobalGroupCode()).isEqualTo(SECURITY_SERVER_GRP);
        assertThat(result.getServiceProviderId()).isEqualTo("MEMBER:instance:memberclass:membercode");
        assertThat(result.getServiceProviderName()).isEqualTo(MEMBER_NAME);
        assertThat(result.getSecurityServerId())
                .isEqualTo("SERVER:instance:memberclass:membercode:SS0; SERVER:instance:memberclass:membercode:SS1");
    }
}
