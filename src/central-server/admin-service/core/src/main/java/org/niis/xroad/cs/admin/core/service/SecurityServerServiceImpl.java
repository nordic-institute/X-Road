/*
 * The MIT License
 * <p>
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
import ee.ria.xroad.common.identifier.SecurityServerId;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.common.managementrequest.model.ManagementRequestType;
import org.niis.xroad.cs.admin.api.domain.AuthenticationCertificateDeletionRequest;
import org.niis.xroad.cs.admin.api.domain.ClientDeletionRequest;
import org.niis.xroad.cs.admin.api.domain.FlattenedSecurityServerClientView;
import org.niis.xroad.cs.admin.api.domain.ManagementRequestStatus;
import org.niis.xroad.cs.admin.api.domain.ManagementRequestView;
import org.niis.xroad.cs.admin.api.domain.MemberId;
import org.niis.xroad.cs.admin.api.domain.SecurityServer;
import org.niis.xroad.cs.admin.api.domain.ServerClient;
import org.niis.xroad.cs.admin.api.domain.XRoadMember;
import org.niis.xroad.cs.admin.api.dto.SecurityServerAuthenticationCertificateDetails;
import org.niis.xroad.cs.admin.api.paging.Page;
import org.niis.xroad.cs.admin.api.paging.PageRequestDto;
import org.niis.xroad.cs.admin.api.service.ClientService;
import org.niis.xroad.cs.admin.api.service.GlobalGroupMemberService;
import org.niis.xroad.cs.admin.api.service.ManagementRequestService;
import org.niis.xroad.cs.admin.api.service.SecurityServerService;
import org.niis.xroad.cs.admin.api.service.SubsystemService;
import org.niis.xroad.cs.admin.core.converter.CertificateConverter;
import org.niis.xroad.cs.admin.core.converter.PageConverter;
import org.niis.xroad.cs.admin.core.converter.PageRequestDtoConverter;
import org.niis.xroad.cs.admin.core.entity.AuthCertEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerClientEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerIdEntity;
import org.niis.xroad.cs.admin.core.entity.ServerClientEntity;
import org.niis.xroad.cs.admin.core.entity.XRoadMemberEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.SecurityServerMapper;
import org.niis.xroad.cs.admin.core.repository.AuthCertRepository;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;
import org.niis.xroad.cs.admin.core.repository.paging.StableSortHelper;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.niis.xroad.cs.admin.api.domain.Origin.CENTER;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MEMBER_NOT_REGISTERED_TO_SECURITY_SERVER;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.SECURITY_SERVER_NOT_FOUND;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.SS_AUTH_CERTIFICATE_NOT_FOUND;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.SUBSYSTEM_NOT_REGISTERED_TO_SECURITY_SERVER;
import static org.niis.xroad.cs.admin.core.service.SystemParameterServiceImpl.DEFAULT_SECURITY_SERVER_OWNERS_GROUP;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.ADDRESS;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.CLIENT_IDENTIFIER;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OWNER_CLASS;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OWNER_CODE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.SERVER_CODE;

@Service
@Transactional
@RequiredArgsConstructor
public class SecurityServerServiceImpl implements SecurityServerService {

    private static final String DELETE_SS_COMMENT_TPL = "%s deletion";

    private final StableSortHelper stableSortHelper;
    private final AuthCertRepository authCertRepository;
    private final SecurityServerRepository securityServerRepository;
    private final ManagementRequestService managementRequestService;
    private final ClientService clientService;
    private final GlobalGroupMemberService groupMemberService;
    private final SubsystemService subsystemService;
    private final SecurityServerMapper securityServerMapper;
    private final CertificateConverter certificateConverter;
    private final PageRequestDtoConverter pageRequestDtoConverter;
    private final PageConverter pageConverter;
    private final AuditDataHelper auditDataHelper;

    @Override
    public Page<SecurityServer> findSecurityServers(String q, PageRequestDto pageRequest) {
        var pageable = stableSortHelper.addSecondaryIdSort(pageRequestDtoConverter.convert(pageRequest));

        var result = securityServerRepository
                .findAllByQuery(q, pageable)
                .map(securityServerMapper::toTarget);
        return pageConverter.convert(result);
    }

    @Override
    public Optional<SecurityServer> find(SecurityServerId id) {
        return securityServerRepository.findBy(id)
                .map(securityServerMapper::toTarget);
    }

    @Override
    public ManagementRequestStatus findSecurityServerClientRegistrationStatus(SecurityServerId serverId, ClientId clientId) {
        ManagementRequestStatus managementRequestStatus = managementRequestService.findRequests(
                        ManagementRequestService.Criteria.builder()
                                .serverId(serverId)
                                .clientId(clientId)
                                .types(List.of(ManagementRequestType.CLIENT_REGISTRATION_REQUEST))
                                .build(), PageRequestDto.unpaged())
                .stream()
                .map(ManagementRequestView::getStatus)
                .findFirst()
                .orElse(null);

        if (managementRequestStatus == null) {
            final boolean containsAsServerClient =
                    subsystemService.findByIdentifier(clientId).stream()
                            .flatMap(subsystem -> subsystem.getServerClients().stream()
                                    .filter(ServerClient::isEnabled))
                            .map(ServerClient::getServerId)
                            .anyMatch(server -> server.equals(serverId));

            if (containsAsServerClient) {
                return ManagementRequestStatus.APPROVED;
            }
        }

        return managementRequestStatus;
    }

    @Override
    public Optional<SecurityServer> findByOwnerAndServerCode(XRoadMember owner, String serverCode) {
        return securityServerRepository.findByOwnerIdAndServerCode(owner.getId(), serverCode)
                .map(securityServerMapper::toTarget);
    }

    @Override
    public List<SecurityServer> findAll() {
        return securityServerRepository.findAll().stream()
                .map(securityServerMapper::toTarget)
                .collect(toList());
    }

    @Override
    public List<FlattenedSecurityServerClientView> findClients(SecurityServerId serverId) {
        return securityServerRepository.findBy(serverId)
                .map(server -> clientService.find(
                        new ClientService.SearchParameters().setSecurityServerId(server.getId())))
                .orElseThrow(() -> new NotFoundException(SECURITY_SERVER_NOT_FOUND));
    }

    @Override
    public Set<SecurityServerAuthenticationCertificateDetails> findAuthCertificates(SecurityServerId id) {
        return securityServerRepository.findBy(id)
                .orElseThrow(() -> new NotFoundException(SECURITY_SERVER_NOT_FOUND))
                .getAuthCerts().stream()
                .map(certificateConverter::toCertificateDetails)
                .collect(toSet());
    }

    @Override
    public Optional<SecurityServer> updateSecurityServerAddress(SecurityServerId serverId, String newAddress) {
        auditDataHelper.put(SERVER_CODE, serverId.getServerCode());
        auditDataHelper.put(OWNER_CODE, serverId.getOwner().getMemberCode());
        auditDataHelper.put(OWNER_CLASS, serverId.getOwner().getMemberClass());
        auditDataHelper.put(ADDRESS, newAddress);

        return securityServerRepository.findBy(serverId)
                .map(securityServer -> {
                    securityServer.setAddress(newAddress);
                    return securityServer;
                })
                .map(securityServerMapper::toTarget);
    }

    @Override
    public void delete(SecurityServerId serverId) {
        auditDataHelper.put(SERVER_CODE, serverId.getServerCode());
        auditDataHelper.put(OWNER_CODE, serverId.getOwner().getMemberCode());
        auditDataHelper.put(OWNER_CLASS, serverId.getOwner().getMemberClass());

        final SecurityServerEntity securityServerEntity = securityServerRepository.findBy(serverId)
                .orElseThrow(() -> new NotFoundException(SECURITY_SERVER_NOT_FOUND));

        registerClientDeletionRequests(securityServerEntity);
        registerAuthCertsDeleteRequests(securityServerEntity);
        updateServerOwnersGroup(securityServerEntity);

        securityServerRepository.delete(securityServerEntity);
    }

    private void registerClientDeletionRequests(SecurityServerEntity securityServerEntity) {
        final var comment = DELETE_SS_COMMENT_TPL.formatted(securityServerEntity.getServerId().toString());

        securityServerEntity.getServerClients().stream()
                .map(ServerClientEntity::getSecurityServerClient)
                .map(SecurityServerClientEntity::getIdentifier)
                .map(clientId -> createClientDeletionRequest(securityServerEntity.getServerId(), clientId, comment))
                .forEach(managementRequestService::add);
    }

    private ClientDeletionRequest createClientDeletionRequest(SecurityServerId serverId, ClientId clientId, String comment) {
        var request = new ClientDeletionRequest(CENTER, serverId, clientId);
        request.setComments(comment);
        return request;
    }

    private ClientDeletionRequest createClientDeletionRequest(SecurityServerId serverId, ClientId clientId) {
        return createClientDeletionRequest(serverId, clientId, null);
    }

    private void registerAuthCertsDeleteRequests(SecurityServerEntity securityServerEntity) {
        final var comment = DELETE_SS_COMMENT_TPL.formatted(securityServerEntity.getServerId().toString());

        securityServerEntity.getAuthCerts().stream()
                .map(AuthCertEntity::getCert)
                .map(cert -> createAuthenticationCertificateDeletionRequest(securityServerEntity.getServerId(), cert, comment))
                .forEach(managementRequestService::add);
    }

    private static AuthenticationCertificateDeletionRequest createAuthenticationCertificateDeletionRequest(SecurityServerIdEntity serverId,
                                                                                                           byte[] cert,
                                                                                                           String comment) {
        var request = new AuthenticationCertificateDeletionRequest(CENTER, serverId, cert);
        request.setComments(comment);
        return request;
    }

    private void updateServerOwnersGroup(SecurityServerEntity securityServerEntity) {
        final XRoadMemberEntity owner = securityServerEntity.getOwner();
        if (owner.getOwnedServers().size() == 1) {
            groupMemberService.removeMemberFromGlobalGroup(DEFAULT_SECURITY_SERVER_OWNERS_GROUP,
                    MemberId.create(owner.getIdentifier()));
        }
    }

    @Override
    public void deleteAuthCertificate(SecurityServerId serverId, Integer certificateId) {
        auditDataHelper.put(OWNER_CLASS, serverId.getMemberClass());
        auditDataHelper.put(OWNER_CODE, serverId.getMemberCode());
        auditDataHelper.put(SERVER_CODE, serverId.getServerCode());

        if (!securityServerRepository.existsBy(serverId)) {
            throw new NotFoundException(SECURITY_SERVER_NOT_FOUND);
        }

        final AuthCertEntity authCertificate = authCertRepository.findById(certificateId)
                .filter(authCertEntity -> authCertEntity.getSecurityServer().getServerId().equals(serverId))
                .orElseThrow(() -> new NotFoundException(SS_AUTH_CERTIFICATE_NOT_FOUND));

        auditDataHelper.putCertificateHash(authCertificate.getCert());

        final var certDeletionRequest = new AuthenticationCertificateDeletionRequest(CENTER, serverId, authCertificate.getCert());
        managementRequestService.add(certDeletionRequest);
    }

    @Override
    public void deleteClient(SecurityServerId securityServerId, ClientId clientId) {
        auditDataHelper.put(SERVER_CODE, securityServerId.getServerCode());
        auditDataHelper.put(OWNER_CLASS, securityServerId.getOwner().getMemberClass());
        auditDataHelper.put(OWNER_CODE, securityServerId.getOwner().getMemberCode());
        auditDataHelper.put(CLIENT_IDENTIFIER, clientId);

        final var securityServer = securityServerRepository.findBy(securityServerId)
                .orElseThrow(() -> new NotFoundException(SECURITY_SERVER_NOT_FOUND));


        securityServer.getServerClients().stream()
                .filter(client -> client.getSecurityServerClient().getIdentifier().equals(clientId))
                .findFirst().orElseThrow(() -> throwNotRegistered(clientId));

        managementRequestService.add(new ClientDeletionRequest(CENTER, securityServerId, clientId));
    }

    private NotFoundException throwNotRegistered(ClientId clientId) {
        if (clientId.getSubsystemCode() == null) {
            return new NotFoundException(MEMBER_NOT_REGISTERED_TO_SECURITY_SERVER);
        } else {
            return new NotFoundException(SUBSYSTEM_NOT_REGISTERED_TO_SECURITY_SERVER);
        }
    }

}
