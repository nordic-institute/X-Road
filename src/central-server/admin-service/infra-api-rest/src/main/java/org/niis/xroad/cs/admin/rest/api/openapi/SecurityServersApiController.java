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
package org.niis.xroad.cs.admin.rest.api.openapi;

import ee.ria.xroad.common.identifier.SecurityServerId;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.common.exception.SecurityServerNotFoundException;
import org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection;
import org.niis.xroad.cs.admin.api.domain.SystemDidCheck;
import org.niis.xroad.cs.admin.api.domain.SystemPairEdit;
import org.niis.xroad.cs.admin.api.paging.Page;
import org.niis.xroad.cs.admin.api.service.SecurityServerService;
import org.niis.xroad.cs.admin.api.service.SystemDidCheckService;
import org.niis.xroad.cs.admin.rest.api.converter.CertificateDetailsDtoConverter;
import org.niis.xroad.cs.admin.rest.api.converter.PageRequestConverter;
import org.niis.xroad.cs.admin.rest.api.converter.SecurityServerConverter;
import org.niis.xroad.cs.admin.rest.api.converter.db.ClientDtoConverter;
import org.niis.xroad.cs.admin.rest.api.converter.db.SecurityServerDtoConverter;
import org.niis.xroad.cs.openapi.SecurityServersApi;
import org.niis.xroad.cs.openapi.model.ClientDto;
import org.niis.xroad.cs.openapi.model.PagedSecurityServersDto;
import org.niis.xroad.cs.openapi.model.PagingSortingParametersDto;
import org.niis.xroad.cs.openapi.model.SecurityServerAuthenticationCertificateDetailsDto;
import org.niis.xroad.cs.openapi.model.SecurityServerDto;
import org.niis.xroad.cs.openapi.model.SecurityServerEditDto;
import org.niis.xroad.cs.openapi.model.SystemDidCheckDto;
import org.niis.xroad.cs.openapi.model.SystemDidCheckRequestDto;
import org.niis.xroad.cs.openapi.model.SystemDidCheckStatusDto;
import org.niis.xroad.cs.openapi.model.SystemDidRefusalReasonDto;
import org.niis.xroad.cs.openapi.model.SystemDidResolutionReasonDto;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.niis.xroad.restapi.converter.SecurityServerIdConverter;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

import static java.util.Map.entry;
import static java.util.stream.Collectors.toList;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.SYSTEM_PAIR_INCOMPLETE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.DELETE_SECURITY_SERVER;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.DELETE_SECURITY_SERVER_AUTH_CERT;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.EDIT_SECURITY_SERVER;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.ADDRESS;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.DSP_BASE_URL;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OWNER_CLASS;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OWNER_CODE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.SERVER_CODE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.SYSTEM_DID;
import static org.springframework.http.ResponseEntity.noContent;
import static org.springframework.http.ResponseEntity.ok;

@RestController
@RequestMapping(ControllerUtil.API_V1_PREFIX)
@PreAuthorize("denyAll")
@RequiredArgsConstructor
public class SecurityServersApiController implements SecurityServersApi {

    private final SecurityServerConverter serverConverter;
    private final SecurityServerDtoConverter securityServerDtoConverter;
    private final PageRequestConverter pageRequestConverter;
    private final CertificateDetailsDtoConverter certificateDetailsDtoConverter;

    private final SecurityServerService securityServerService;
    private final SystemDidCheckService systemDidCheckService;
    private final SecurityServerIdConverter securityServerIdConverter;
    private final ClientDtoConverter.Flattened flattenedSecurityServerClientViewDtoConverter;
    private final AuditDataHelper auditDataHelper;

    private final PageRequestConverter.MappableSortParameterConverter findSortParameterConverter =
            new PageRequestConverter.MappableSortParameterConverter(
                    entry("owner_name", "owner.name"),
                    entry("server_id.member_class", "owner.memberClass.code"),
                    entry("server_id.member_code", "owner.memberCode"),
                    entry("server_id.server_code", "serverCode")
            );

    @Override
    @PreAuthorize("hasAuthority('DELETE_SECURITY_SERVER')")
    @AuditEventMethod(event = DELETE_SECURITY_SERVER)
    public ResponseEntity<Void> deleteSecurityServer(String id) {
        securityServerService.delete(securityServerIdConverter.convertId(id));
        return noContent().build();
    }

    @Override
    @AuditEventMethod(event = DELETE_SECURITY_SERVER_AUTH_CERT)
    @PreAuthorize("hasAuthority('DELETE_SECURITY_SERVER_AUTH_CERT')")
    public ResponseEntity<Void> deleteSecurityServerAuthCert(String id, Integer certificateId) {
        securityServerService.deleteAuthCertificate(securityServerIdConverter.convertId(id), certificateId);
        return noContent().build();
    }

    @Override
    @Validated
    @PreAuthorize("hasAuthority('VIEW_SECURITY_SERVERS')")
    public ResponseEntity<PagedSecurityServersDto> findSecurityServers(String query,
                                                                       PagingSortingParametersDto pagingSorting) {
        var pageRequest = pageRequestConverter.convert(
                pagingSorting, findSortParameterConverter);

        Page<SecurityServerDto> servers = securityServerService.findSecurityServers(query, pageRequest)
                .map(securityServerDtoConverter::toDto);

        return ok(serverConverter.convert(servers));
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_SECURITY_SERVER_DETAILS')")
    public ResponseEntity<SecurityServerDto> getSecurityServer(String id) {
        SecurityServerId serverId = securityServerIdConverter.convertId(id);
        return securityServerService.find(serverId)
                .map(securityServerDtoConverter::toDto)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new SecurityServerNotFoundException(serverId));
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_SECURITY_SERVER_DETAILS')")
    public ResponseEntity<List<SecurityServerAuthenticationCertificateDetailsDto>> getSecurityServerAuthCerts(String id) {
        var result = securityServerService.findAuthCertificates(securityServerIdConverter.convertId(id)).stream()
                .map(certificateDetailsDtoConverter::convert)
                .collect(toList());
        return ResponseEntity.ok(result);
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_SECURITY_SERVER_DETAILS')")
    public ResponseEntity<List<ClientDto>> getSecurityServerClients(String id) {

        return ok(securityServerService.findClients(securityServerIdConverter.convertId(id))
                .stream().map(flattenedSecurityServerClientViewDtoConverter::toDto)
                .collect(toList()));
    }

    @Override
    @PreAuthorize("hasAuthority('EDIT_SECURITY_SERVER_ADDRESS')")
    @AuditEventMethod(event = EDIT_SECURITY_SERVER)
    public ResponseEntity<SecurityServerDto> updateSecurityServer(String id, SecurityServerEditDto securityServerEdit) {
        var securityServerId = securityServerIdConverter.convertId(id);
        auditDataHelper.put(SERVER_CODE, securityServerId.getServerCode());
        auditDataHelper.put(OWNER_CODE, securityServerId.getOwner().getMemberCode());
        auditDataHelper.put(OWNER_CLASS, securityServerId.getOwner().getMemberClass());
        auditDataHelper.put(ADDRESS, securityServerEdit.getServerAddress());
        if (securityServerEdit.getSystemDid() != null) {
            auditDataHelper.put(SYSTEM_DID, securityServerEdit.getSystemDid());
        }
        if (securityServerEdit.getDspBaseUrl() != null) {
            auditDataHelper.put(DSP_BASE_URL, securityServerEdit.getDspBaseUrl());
        }

        return securityServerService.editSecurityServer(securityServerId, securityServerEdit.getServerAddress(),
                        toSystemPairEdit(securityServerEdit))
                .map(securityServerDtoConverter::toDto)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new SecurityServerNotFoundException(securityServerId));
    }

    @Override
    @PreAuthorize("hasAuthority('EDIT_SECURITY_SERVER_ADDRESS')")
    public ResponseEntity<SystemDidCheckDto> checkSystemDid(String id, SystemDidCheckRequestDto request) {
        var check = systemDidCheckService.check(securityServerIdConverter.convertId(id),
                request.getServerAddress(), request.getSystemDid());
        return ok(toDto(check));
    }

    private static SystemDidCheckDto toDto(SystemDidCheck check) {
        return new SystemDidCheckDto()
                .status(SystemDidCheckStatusDto.valueOf(check.status().name()))
                .refusalReason(check.refusal() == null ? null : toDto(check.refusal()))
                .resolutionReason(check.resolutionReason() == null
                        ? null
                        : SystemDidResolutionReasonDto.valueOf(check.resolutionReason().name()));
    }

    private static SystemDidRefusalReasonDto toDto(ParticipantValueRejection refusal) {
        return switch (refusal) {
            case WRONG_SCHEME -> SystemDidRefusalReasonDto.WRONG_SCHEME;
            case DID_TOO_LONG -> SystemDidRefusalReasonDto.DID_TOO_LONG;
            case NOT_CANONICAL -> SystemDidRefusalReasonDto.NOT_CANONICAL;
            case HOST_DIFFERS -> SystemDidRefusalReasonDto.HOST_DIFFERS;
            case UNPARSEABLE_REGISTERED_ADDRESS -> SystemDidRefusalReasonDto.UNPARSEABLE_REGISTERED_ADDRESS;
            case NON_ASCII_HOST -> SystemDidRefusalReasonDto.NON_ASCII_HOST;
            case FOREIGN_SYSTEM_DID -> SystemDidRefusalReasonDto.FOREIGN_SYSTEM_DID;
            case DID_HELD_BY_ANOTHER_SERVER -> SystemDidRefusalReasonDto.DID_HELD_BY_ANOTHER_SERVER;
            case NO_STORED_VALUE -> SystemDidRefusalReasonDto.NO_STORED_VALUE;
            case DSP_BASE_URL_NOT_HTTPS, MALFORMED_DSP_BASE_URL, UNDECODABLE_DID, DID_OF_OTHER_MEMBER, MEMBER_NOT_SERVED,
                 VALUE_DIFFERS -> throw new IllegalStateException("A SYSTEM DID check cannot be refused as " + refusal);
        };
    }

    private static SystemPairEdit toSystemPairEdit(SecurityServerEditDto edit) {
        if (edit.getSystemDid() == null && edit.getDspBaseUrl() == null) {
            return null;
        }
        if (edit.getSystemDid() == null || edit.getDspBaseUrl() == null) {
            throw new BadRequestException(SYSTEM_PAIR_INCOMPLETE.build());
        }
        return new SystemPairEdit(edit.getSystemDid(), edit.getDspBaseUrl());
    }
}
