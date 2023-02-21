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

import lombok.RequiredArgsConstructor;
import org.niis.xroad.cs.admin.api.domain.ManagementServicesConfiguration;
import org.niis.xroad.cs.admin.api.domain.SecurityServer;
import org.niis.xroad.cs.admin.api.domain.SecurityServerClient;
import org.niis.xroad.cs.admin.api.domain.SecurityServerId;
import org.niis.xroad.cs.admin.api.domain.ServerClient;
import org.niis.xroad.cs.admin.api.domain.XRoadMember;
import org.niis.xroad.cs.admin.api.service.ManagementServicesService;
import org.niis.xroad.cs.admin.api.service.MemberService;
import org.niis.xroad.cs.admin.api.service.SubsystemService;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ManagementServicesServiceImpl implements ManagementServicesService {
    private final SystemParameterService systemParameterService;
    private final MemberService memberService;
    private final SubsystemService subsystemService;

    @Override
    public ManagementServicesConfiguration getManagementServicesConfiguration() {
        var centralServerAddress = systemParameterService.getCentralServerAddress();
        var serviceProviderClientId = systemParameterService.getManagementServiceProviderId();
        var xRoadMember = memberService.findMember(serviceProviderClientId).getOrElseThrow(NoSuchElementException::new);

        return new ManagementServicesConfiguration()
                .setServicesAddress(formatServicesAddress(centralServerAddress))
                .setWsdlAddress(formatWsdlAddress(centralServerAddress))
                .setSecurityServerOwnersGlobalGroupCode(systemParameterService.getSecurityServerOwnersGroup())
                .setServiceProviderId(serviceProviderClientId.asEncodedId(true))
                .setServiceProviderName(xRoadMember.getName())
                .setSecurityServerId(getSecurityServerIds(serviceProviderClientId, xRoadMember));
    }


    private String getSecurityServerIds(ClientId serviceProviderClientId, XRoadMember xRoadMember) {
        SecurityServerClient securityServerClient;
        Set<SecurityServerId> securityServers = new HashSet<>();
        if (serviceProviderClientId.getSubsystemCode() != null) {
            securityServerClient = subsystemService.findByIdentifier(serviceProviderClientId).orElseThrow();
        } else {
            securityServerClient = xRoadMember;

            var ownedServers = memberService.getMemberOwnedServers(xRoadMember.getIdentifier());
            if (ownedServers != null && !ownedServers.isEmpty()) {
                securityServers.addAll(ownedServers.stream()
                        .map(SecurityServer::getServerId)
                        .collect(Collectors.toList()));
            }
        }

        securityServers.addAll(securityServerClient.getServerClients().stream()
                .map(ServerClient::getServerId)
                .collect(Collectors.toList()));

        return securityServers.stream()
                .map(securityServerId -> securityServerId.asEncodedId(true))
                .sorted()
                .collect(Collectors.joining("; "));
    }


    private String formatWsdlAddress(String centralServerAddress) {
        return String.format("http://%s/managementservices.wsdl", centralServerAddress);
    }

    private String formatServicesAddress(String centralServerAddress) {
        return String.format("https://%s:4002/managementservice/manage/", centralServerAddress);
    }

}
