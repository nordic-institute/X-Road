/*
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
import org.apache.commons.lang3.StringUtils;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.common.exception.ValidationFailureException;
import org.niis.xroad.cs.admin.api.domain.ClientRegistrationRequest;
import org.niis.xroad.cs.admin.api.domain.ManagementServicesConfiguration;
import org.niis.xroad.cs.admin.api.domain.SecurityServer;
import org.niis.xroad.cs.admin.api.domain.SecurityServerClient;
import org.niis.xroad.cs.admin.api.domain.SecurityServerId;
import org.niis.xroad.cs.admin.api.domain.ServerClient;
import org.niis.xroad.cs.admin.api.domain.XRoadMember;
import org.niis.xroad.cs.admin.api.exception.ErrorMessage;
import org.niis.xroad.cs.admin.api.service.ManagementRequestService;
import org.niis.xroad.cs.admin.api.service.ManagementServicesService;
import org.niis.xroad.cs.admin.api.service.MemberService;
import org.niis.xroad.cs.admin.api.service.SubsystemService;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;
import org.niis.xroad.cs.admin.core.entity.SecurityServerClientEntity;
import org.niis.xroad.cs.admin.core.repository.SecurityServerClientRepository;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

import static java.util.stream.Collectors.joining;
import static org.niis.xroad.cs.admin.api.domain.ManagementRequestStatus.APPROVED;
import static org.niis.xroad.cs.admin.api.domain.Origin.CENTER;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MANAGEMENT_SERVICE_PROVIDER_NOT_SET;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.SUBSYSTEM_ALREADY_REGISTERED_TO_SECURITY_SERVER;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.SUBSYSTEM_NOT_FOUND;
import static org.niis.xroad.cs.admin.core.service.SystemParameterServiceImpl.MANAGEMENT_SERVICE_PROVIDER_CLASS;
import static org.niis.xroad.cs.admin.core.service.SystemParameterServiceImpl.MANAGEMENT_SERVICE_PROVIDER_CODE;
import static org.niis.xroad.cs.admin.core.service.SystemParameterServiceImpl.MANAGEMENT_SERVICE_PROVIDER_SUBSYSTEM;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.CLIENT_IDENTIFIER;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OWNER_CLASS;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OWNER_CODE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.SERVER_CODE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.SERVICE_PROVIDER_IDENTIFIER;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.SERVICE_PROVIDER_NAME;

@Service
@RequiredArgsConstructor
public class ManagementServicesServiceImpl implements ManagementServicesService {
    private final SystemParameterService systemParameterService;
    private final MemberService memberService;
    private final SubsystemService subsystemService;
    private final AuditDataHelper auditData;
    private final SecurityServerClientRepository<SecurityServerClientEntity> clients;
    private final ManagementRequestService managementRequestService;

    @Override
    public ManagementServicesConfiguration updateManagementServicesProvider(ClientId serviceProviderClientId) {
        var subsystem = subsystemService.findByIdentifier(serviceProviderClientId)
                .orElseThrow(() -> new NotFoundException(SUBSYSTEM_NOT_FOUND));
        var xRoadMember = subsystem.getXroadMember();

        systemParameterService.updateOrCreateParameter(MANAGEMENT_SERVICE_PROVIDER_CLASS, xRoadMember.getMemberClass().getCode());
        systemParameterService.updateOrCreateParameter(MANAGEMENT_SERVICE_PROVIDER_CODE, xRoadMember.getMemberCode());
        systemParameterService.updateOrCreateParameter(MANAGEMENT_SERVICE_PROVIDER_SUBSYSTEM, subsystem.getSubsystemCode());

        auditData.put(SERVICE_PROVIDER_IDENTIFIER, serviceProviderClientId.asEncodedId());
        auditData.put(SERVICE_PROVIDER_NAME, subsystem.getXroadMember().getName());

        return getFullConfiguration(xRoadMember, serviceProviderClientId);
    }

    @Override
    public ManagementServicesConfiguration registerManagementServicesSecurityServer(
            ee.ria.xroad.common.identifier.SecurityServerId securityServerId) {
        final ClientId serviceProviderClientId = systemParameterService.getManagementServiceProviderId();
        if (serviceProviderClientId == null) {
            throw new ValidationFailureException(MANAGEMENT_SERVICE_PROVIDER_NOT_SET);
        }
        org.niis.xroad.cs.admin.api.domain.ClientId clientId = org.niis.xroad.cs.admin.api.domain.ClientId.ensure(serviceProviderClientId);

        auditData.put(SERVER_CODE, securityServerId.getServerCode());
        auditData.put(OWNER_CLASS, securityServerId.getOwner().getMemberClass());
        auditData.put(OWNER_CODE, securityServerId.getOwner().getMemberCode());
        auditData.put(CLIENT_IDENTIFIER, clientId.asEncodedId());

        final SecurityServerClientEntity subsystem = clients.findOneBy(serviceProviderClientId)
                .getOrElseThrow(() -> new NotFoundException(SUBSYSTEM_NOT_FOUND));
        if (!subsystem.getServerClients().isEmpty()) {
            throw new ValidationFailureException(SUBSYSTEM_ALREADY_REGISTERED_TO_SECURITY_SERVER);
        }

        ClientRegistrationRequest clientRegistrationRequest = new ClientRegistrationRequest(CENTER, securityServerId, clientId);
        final ClientRegistrationRequest request = managementRequestService.add(clientRegistrationRequest);
        if (request.getProcessingStatus() != APPROVED) {
            managementRequestService.approve(request.getId());
        }

        return getManagementServicesConfiguration();
    }

    @Override
    public ManagementServicesConfiguration getManagementServicesConfiguration() {
        return Optional.ofNullable(systemParameterService.getManagementServiceProviderId()).map(serviceProviderClientId -> {
            var xRoadMember = memberService.findMember(serviceProviderClientId)
                    .getOrElseThrow(() -> new NotFoundException(ErrorMessage.MEMBER_NOT_FOUND));

            return getFullConfiguration(xRoadMember, serviceProviderClientId);
        }).orElseGet(this::getBasicConfiguration);
    }

    private ManagementServicesConfiguration getBasicConfiguration() {
        var centralServerAddress = systemParameterService.getCentralServerAddress();

        return new ManagementServicesConfiguration()
                .setServicesAddress(formatServicesAddress(centralServerAddress))
                .setWsdlAddress(formatWsdlAddress(centralServerAddress))
                .setSecurityServerOwnersGlobalGroupCode(systemParameterService.getSecurityServerOwnersGroup());
    }

    private ManagementServicesConfiguration getFullConfiguration(XRoadMember xRoadMember,
                                                                 ClientId serviceProviderClientId) {
        return getBasicConfiguration()
                .setServiceProviderId(serviceProviderClientId.asEncodedId(true))
                .setServiceProviderName(xRoadMember.getName())
                .setSecurityServerId(getSecurityServerIds(serviceProviderClientId, xRoadMember));
    }

    private String getSecurityServerIds(ClientId serviceProviderClientId, XRoadMember xRoadMember) {
        SecurityServerClient securityServerClient;
        Set<SecurityServerId> securityServers = new HashSet<>();
        if (serviceProviderClientId.getSubsystemCode() != null) {
            securityServerClient = subsystemService.findByIdentifier(serviceProviderClientId)
                    .orElseThrow(() -> new NotFoundException(SUBSYSTEM_NOT_FOUND));
        } else {
            securityServerClient = xRoadMember;

            var ownedServers = memberService.getMemberOwnedServers(xRoadMember.getIdentifier());
            if (ownedServers != null && !ownedServers.isEmpty()) {
                securityServers.addAll(ownedServers.stream()
                        .map(SecurityServer::getServerId)
                        .toList());
            }
        }

        securityServers.addAll(securityServerClient.getServerClients().stream()
                .filter(ServerClient::isEnabled)
                .map(ServerClient::getServerId)
                .toList());

        return securityServers.stream()
                .map(securityServerId -> securityServerId.asEncodedId(true))
                .sorted()
                .collect(joining("; "));
    }


    private String formatWsdlAddress(String centralServerAddress) {
        if (StringUtils.isBlank(centralServerAddress)) {
            return StringUtils.EMPTY;
        }
        return String.format("http://%s/managementservices.wsdl", centralServerAddress);
    }

    private String formatServicesAddress(String centralServerAddress) {
        if (StringUtils.isBlank(centralServerAddress)) {
            return StringUtils.EMPTY;
        }
        return String.format("https://%s:4002/managementservice/manage/", centralServerAddress);
    }

}
