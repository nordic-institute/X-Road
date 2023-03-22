/**
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

import ee.ria.xroad.common.identifier.SecurityServerId;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.common.managementrequest.model.ManagementRequestType;
import org.niis.xroad.cs.admin.api.domain.AuthenticationCertificateDeletionRequest;
import org.niis.xroad.cs.admin.api.domain.ClientDeletionRequest;
import org.niis.xroad.cs.admin.api.domain.FlattenedSecurityServerClientView;
import org.niis.xroad.cs.admin.api.domain.ManagementRequestStatus;
import org.niis.xroad.cs.admin.api.domain.ManagementRequestView;
import org.niis.xroad.cs.admin.api.domain.MemberId;
import org.niis.xroad.cs.admin.api.domain.Origin;
import org.niis.xroad.cs.admin.api.domain.SecurityServer;
import org.niis.xroad.cs.admin.api.domain.XRoadMember;
import org.niis.xroad.cs.admin.api.dto.SecurityServerAuthenticationCertificateDetails;
import org.niis.xroad.cs.admin.api.service.ClientService;
import org.niis.xroad.cs.admin.api.service.GroupMemberService;
import org.niis.xroad.cs.admin.api.service.ManagementRequestService;
import org.niis.xroad.cs.admin.api.service.SecurityServerService;
import org.niis.xroad.cs.admin.api.service.StableSortHelper;
import org.niis.xroad.cs.admin.core.converter.CertificateConverter;
import org.niis.xroad.cs.admin.core.entity.AuthCertEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerEntity;
import org.niis.xroad.cs.admin.core.entity.XRoadMemberEntity;
import org.niis.xroad.cs.admin.core.entity.mapper.SecurityServerMapper;
import org.niis.xroad.cs.admin.core.repository.AuthCertRepository;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.niis.xroad.cs.admin.api.domain.Origin.CENTER;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.AUTH_CERTIFICATE_NOT_FOUND;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.SECURITY_SERVER_NOT_FOUND;
import static org.niis.xroad.cs.admin.core.service.SystemParameterServiceImpl.DEFAULT_SECURITY_SERVER_OWNERS_GROUP;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.ADDRESS;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OWNER_CLASS;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OWNER_CODE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.SERVER_CODE;

@Service
@Transactional
@RequiredArgsConstructor
public class SecurityServerServiceImpl implements SecurityServerService {

    private final StableSortHelper stableSortHelper;
    private final AuthCertRepository authCertRepository;
    private final SecurityServerRepository securityServerRepository;
    private final ManagementRequestService managementRequestService;
    private final ClientService clientService;
    private final GroupMemberService groupMemberService;
    private final SecurityServerMapper securityServerMapper;
    private final CertificateConverter certificateConverter;
    private final AuditDataHelper auditDataHelper;

    @Override
    public Page<SecurityServer> findSecurityServers(String q, Pageable pageable) {
        return securityServerRepository
                .findAllByQuery(q, stableSortHelper.addSecondaryIdSort(pageable))
                .map(securityServerMapper::toTarget);
    }

    @Override
    public Optional<SecurityServer> find(SecurityServerId id) {
        return securityServerRepository.findBy(id).toJavaOptional()
                .map(securityServerMapper::toTarget);
    }

    @Override
    public ManagementRequestStatus findSecurityServerRegistrationStatus(SecurityServerId serverId) {
        return managementRequestService.findRequests(
                        ManagementRequestService.Criteria.builder()
                                .origin(Origin.SECURITY_SERVER)
                                .serverId(serverId)
                                .types(List.of(ManagementRequestType.CLIENT_REGISTRATION_REQUEST))
                                .build(), Pageable.unpaged())
                .stream()
                .map(ManagementRequestView::getStatus)
                .findFirst()
                .orElse(null);
    }

    @Override
    public Optional<SecurityServer> findByOwnerAndServerCode(XRoadMember owner, String serverCode) {
        //TODO we should map back to entities just for lookups.
        return securityServerRepository.findByOwnerIdAndServerCode(owner.getId(), serverCode).toJavaOptional()
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
                .getOrElseThrow(() -> new NotFoundException(SECURITY_SERVER_NOT_FOUND));
    }

    @Override
    public Set<SecurityServerAuthenticationCertificateDetails> findAuthCertificates(SecurityServerId id) {
        return securityServerRepository.findBy(id)
                .getOrElseThrow(() -> new NotFoundException(SECURITY_SERVER_NOT_FOUND))
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

        return securityServerRepository.findBy(serverId).toJavaOptional()
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
                .getOrElseThrow(() -> new NotFoundException(SECURITY_SERVER_NOT_FOUND));

        registerClientDeletionRequests(securityServerEntity);
        registerAuthCertsDeleteRequests(securityServerEntity);
        updateServerOwnersGroup(securityServerEntity);
        securityServerEntity.getOwner().getOwnedServers().remove(securityServerEntity);
        securityServerRepository.delete(securityServerEntity);
    }

    private void registerClientDeletionRequests(SecurityServerEntity securityServerEntity) {
        final String comment = String.format("%s deletion", securityServerEntity.getServerId().toString());
        final List<ClientDeletionRequest> clientDeletionRequests =
                securityServerEntity.getServerClients().stream()
                        .map(client -> new ClientDeletionRequest(CENTER, securityServerEntity.getServerId(),
                                client.getSecurityServerClient().getIdentifier()))
                        .peek(req -> req.setComments(comment))
                        .collect(toList());
        clientDeletionRequests.forEach(managementRequestService::add);
    }

    private void registerAuthCertsDeleteRequests(SecurityServerEntity securityServerEntity) {
        final String comment = String.format("%s deletion", securityServerEntity.getServerId().toString());
        final List<AuthenticationCertificateDeletionRequest> certDeletionRequests =
                securityServerEntity.getAuthCerts().stream()
                        .map(cert -> new AuthenticationCertificateDeletionRequest(CENTER,
                                securityServerEntity.getServerId(), cert.getCert()))
                        .peek(req -> req.setComments(comment))
                        .collect(toList());
        certDeletionRequests.forEach(managementRequestService::add);
    }

    private void updateServerOwnersGroup(SecurityServerEntity securityServerEntity) {
        final XRoadMemberEntity owner = securityServerEntity.getOwner();
        if (owner.getOwnedServers().size() == 1) {
            groupMemberService.removeMemberFromGlobalGroup(
                    MemberId.create(owner.getIdentifier()), DEFAULT_SECURITY_SERVER_OWNERS_GROUP);
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
                .orElseThrow(() -> new NotFoundException(AUTH_CERTIFICATE_NOT_FOUND));

        auditDataHelper.putCertificateHash(authCertificate.getCert());

        final var certDeletionRequest = new AuthenticationCertificateDeletionRequest(CENTER, serverId, authCertificate.getCert());
        managementRequestService.add(certDeletionRequest);
    }

}
