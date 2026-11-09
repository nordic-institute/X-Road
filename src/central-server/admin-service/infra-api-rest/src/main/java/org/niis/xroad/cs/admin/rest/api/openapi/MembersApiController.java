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
package org.niis.xroad.cs.admin.rest.api.openapi;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.cs.admin.api.domain.DidCheck;
import org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection;
import org.niis.xroad.cs.admin.api.service.MemberDidCheckService;
import org.niis.xroad.cs.admin.api.service.MemberService;
import org.niis.xroad.cs.admin.api.service.SecurityServerService;
import org.niis.xroad.cs.admin.api.service.SubsystemService;
import org.niis.xroad.cs.admin.rest.api.converter.GroupMemberConverter;
import org.niis.xroad.cs.admin.rest.api.converter.MemberCreationRequestMapper;
import org.niis.xroad.cs.admin.rest.api.converter.db.ClientDtoConverter;
import org.niis.xroad.cs.admin.rest.api.converter.db.MemberServerDtoConverter;
import org.niis.xroad.cs.admin.rest.api.converter.db.SubsystemDtoConverter;
import org.niis.xroad.cs.openapi.MembersApi;
import org.niis.xroad.cs.openapi.model.ClientDto;
import org.niis.xroad.cs.openapi.model.DidCheckStatusDto;
import org.niis.xroad.cs.openapi.model.DidResolutionReasonDto;
import org.niis.xroad.cs.openapi.model.MemberAddDto;
import org.niis.xroad.cs.openapi.model.MemberDidCheckDto;
import org.niis.xroad.cs.openapi.model.MemberDidCheckRequestDto;
import org.niis.xroad.cs.openapi.model.MemberDidEditDto;
import org.niis.xroad.cs.openapi.model.MemberDidRefusalReasonDto;
import org.niis.xroad.cs.openapi.model.MemberGlobalGroupDto;
import org.niis.xroad.cs.openapi.model.MemberNameDto;
import org.niis.xroad.cs.openapi.model.MemberServerDto;
import org.niis.xroad.cs.openapi.model.SubsystemDto;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.niis.xroad.restapi.converter.ClientIdConverter;
import org.niis.xroad.restapi.converter.SecurityServerIdConverter;
import org.niis.xroad.restapi.openapi.ControllerUtil;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.List;
import java.util.Optional;

import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.INVALID_MEMBER_ID;
import static org.niis.xroad.cs.admin.api.exception.ErrorMessage.MEMBER_NOT_FOUND;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.ADD_MEMBER;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.CHECK_MEMBER_DID;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.DELETE_MEMBER;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.EDIT_MEMBER_DID;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.EDIT_MEMBER_NAME;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.UNREGISTER_MEMBER;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.MEMBER_CLASS;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.MEMBER_CODE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.MEMBER_DID;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.MEMBER_DID_CHECK_STATUS;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OWNER_CLASS;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OWNER_CODE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.SERVER_CODE;
import static org.springframework.http.ResponseEntity.noContent;
import static org.springframework.http.ResponseEntity.ok;

@Controller
@RequestMapping(ControllerUtil.API_V1_PREFIX)
@PreAuthorize("denyAll")
@RequiredArgsConstructor
public class MembersApiController implements MembersApi {

    private final MemberService memberService;
    private final MemberDidCheckService memberDidCheckService;
    private final SubsystemService subsystemService;
    private final SecurityServerService securityServerService;
    private final ClientDtoConverter clientDtoConverter;
    private final ClientIdConverter clientIdConverter;
    private final SubsystemDtoConverter subsystemDtoConverter;
    private final GroupMemberConverter groupMemberConverter;
    private final SecurityServerIdConverter securityServerIdConverter;
    private final MemberServerDtoConverter memberServerDtoConverter;
    private final MemberCreationRequestMapper memberCreationRequestMapper;
    private final AuditDataHelper auditDataHelper;

    @Override
    @PreAuthorize("hasAuthority('ADD_NEW_MEMBER')")
    @AuditEventMethod(event = ADD_MEMBER)
    public ResponseEntity<ClientDto> addMember(MemberAddDto memberAddDto) {

        return Optional.of(memberAddDto)
                .map(memberCreationRequestMapper::toTarget)
                .map(memberService::add)
                .map(clientDtoConverter::toDto)
                .map(ResponseEntity.status(HttpStatus.CREATED)::body)
                .orElseGet(() -> ResponseEntity.badRequest().build());
    }

    @Override
    @PreAuthorize("hasAuthority('DELETE_MEMBER')")
    @AuditEventMethod(event = DELETE_MEMBER)
    public ResponseEntity<Void> deleteMember(String id) {
        verifyMemberId(id);
        ClientId clientId = clientIdConverter.convertId(id);

        memberService.delete(clientId);
        return ResponseEntity.noContent().build();
    }

    @Override
    @PreAuthorize("hasAnyAuthority('VIEW_MEMBER_DETAILS')")
    public ResponseEntity<ClientDto> getMember(String id) {
        verifyMemberId(id);
        return Optional.of(id)
                .map(clientIdConverter::convertId)
                .flatMap(memberService::findMember)
                .map(clientDtoConverter::toDto)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new NotFoundException(MEMBER_NOT_FOUND.build()));
    }

    @Override
    @PreAuthorize("hasAuthority('VIEW_MEMBER_DETAILS')")
    public ResponseEntity<List<SubsystemDto>> getSubsystems(String id) {
        verifyMemberId(id);
        return ResponseEntity.ok(
                subsystemService.findByMemberIdentifier(clientIdConverter.convertId(id)).stream()
                        .map(subsystemDtoConverter::toDto)
                        .toList()
        );
    }

    @Override
    @PreAuthorize("hasAnyAuthority('VIEW_MEMBER_DETAILS')")
    public ResponseEntity<List<MemberGlobalGroupDto>> getMemberGlobalGroups(final String memberId) {
        verifyMemberId(memberId);

        var result = memberService.getMemberGlobalGroups(clientIdConverter.convertId(memberId)).stream()
                .map(groupMemberConverter::convertMemberGlobalGroup)
                .collect(toList());
        return ResponseEntity.ok(result);
    }

    @Override
    @PreAuthorize("hasAnyAuthority('VIEW_MEMBER_DETAILS')")
    public ResponseEntity<List<MemberServerDto>> getOwnedServers(final String memberId) {
        verifyMemberId(memberId);

        return ResponseEntity.ok(
                memberService.getMemberOwnedServers(clientIdConverter.convertId(memberId)).stream()
                        .map(memberServerDtoConverter::toDto)
                        .toList()
        );
    }

    @Override
    @PreAuthorize("hasAnyAuthority('VIEW_MEMBER_DETAILS')")
    public ResponseEntity<List<MemberServerDto>> getUsedServers(String memberId) {
        verifyMemberId(memberId);

        return ResponseEntity.ok(
                memberService.getMemberClientOfServers(clientIdConverter.convertId(memberId)).stream()
                        .map(memberServerDtoConverter::toDto)
                        .toList()
        );
    }

    @Override
    @PreAuthorize("hasAuthority('UNREGISTER_MEMBER')")
    @AuditEventMethod(event = UNREGISTER_MEMBER)
    public ResponseEntity<Void> unregisterMember(String memberId, String serverId) {
        verifyMemberId(memberId);
        ClientId clientId = clientIdConverter.convertId(memberId);
        SecurityServerId securityServerId = securityServerIdConverter.convertId(serverId);

        securityServerService.deleteClient(securityServerId, clientId);
        return noContent().build();
    }

    @Override
    @PreAuthorize("hasAuthority('EDIT_MEMBER_DID')")
    @AuditEventMethod(event = EDIT_MEMBER_DID)
    public ResponseEntity<Void> updateMemberDid(String memberId, String serverId, MemberDidEditDto memberDidEdit) {
        verifyMemberId(memberId);
        ClientId clientId = clientIdConverter.convertId(memberId);
        SecurityServerId securityServerId = requireNonNull(securityServerIdConverter.convertId(serverId));
        memberService.editMemberDid(securityServerId, clientId, memberDidEdit.getMemberDid());
        return noContent().build();
    }

    @Override
    @PreAuthorize("hasAuthority('EDIT_MEMBER_DID')")
    @AuditEventMethod(event = CHECK_MEMBER_DID)
    public ResponseEntity<MemberDidCheckDto> checkMemberDid(String memberId, String serverId, MemberDidCheckRequestDto request) {
        verifyMemberId(memberId);
        ClientId clientId = clientIdConverter.convertId(memberId);
        SecurityServerId securityServerId = requireNonNull(securityServerIdConverter.convertId(serverId));
        auditDataHelper.put(MEMBER_CLASS, clientId.getMemberClass());
        auditDataHelper.put(MEMBER_CODE, clientId.getMemberCode());
        auditDataHelper.put(SERVER_CODE, securityServerId.getServerCode());
        auditDataHelper.put(OWNER_CLASS, securityServerId.getOwner().getMemberClass());
        auditDataHelper.put(OWNER_CODE, securityServerId.getOwner().getMemberCode());
        auditDataHelper.put(MEMBER_DID, request.getMemberDid());

        var check = memberDidCheckService.check(securityServerId, clientId, request.getMemberDid());
        auditDataHelper.put(MEMBER_DID_CHECK_STATUS, check.status());
        return ok(toDto(check));
    }

    @Override
    @PreAuthorize("hasAnyAuthority('EDIT_MEMBER_NAME')")
    @AuditEventMethod(event = EDIT_MEMBER_NAME)
    public ResponseEntity<ClientDto> updateMemberName(String id, MemberNameDto memberName) {
        verifyMemberId(id);
        return Optional.of(id)
                .map(clientIdConverter::convertId)
                .flatMap(clientId -> memberService.updateMemberName(clientId, memberName.getMemberName()))
                .map(clientDtoConverter::toDto)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new NotFoundException(MEMBER_NOT_FOUND.build()));
    }

    private void verifyMemberId(String id) {
        if (!clientIdConverter.isEncodedMemberId(id)) {
            throw new BadRequestException(INVALID_MEMBER_ID.build(id));
        }
    }

    private static MemberDidCheckDto toDto(DidCheck check) {
        return new MemberDidCheckDto()
                .status(DidCheckStatusDto.valueOf(check.status().name()))
                .refusalReason(check.refusal() == null ? null : toDto(check.refusal()))
                .resolutionReason(check.resolutionReason() == null
                        ? null
                        : DidResolutionReasonDto.valueOf(check.resolutionReason().name()));
    }

    private static MemberDidRefusalReasonDto toDto(ParticipantValueRejection refusal) {
        return switch (refusal) {
            case NO_STORED_VALUE -> MemberDidRefusalReasonDto.NO_STORED_VALUE;
            case WRONG_SCHEME -> MemberDidRefusalReasonDto.WRONG_SCHEME;
            case DID_TOO_LONG -> MemberDidRefusalReasonDto.DID_TOO_LONG;
            case NON_ASCII_HOST -> MemberDidRefusalReasonDto.NON_ASCII_HOST;
            case UNDECODABLE_DID -> MemberDidRefusalReasonDto.UNDECODABLE_DID;
            case NOT_CANONICAL -> MemberDidRefusalReasonDto.NOT_CANONICAL;
            case HOST_DIFFERS -> MemberDidRefusalReasonDto.HOST_DIFFERS;
            case DID_OF_OTHER_MEMBER -> MemberDidRefusalReasonDto.DID_OF_OTHER_MEMBER;
            case MEMBER_NOT_SERVED -> MemberDidRefusalReasonDto.MEMBER_NOT_SERVED;
            case UNPARSEABLE_REGISTERED_ADDRESS -> MemberDidRefusalReasonDto.UNPARSEABLE_REGISTERED_ADDRESS;
            case DID_HELD_BY_ANOTHER_SERVER -> MemberDidRefusalReasonDto.DID_HELD_BY_ANOTHER_SERVER;
            case DSP_BASE_URL_NOT_HTTPS, MALFORMED_DSP_BASE_URL, FOREIGN_SYSTEM_DID, VALUE_DIFFERS ->
                    throw new IllegalStateException("A member DID check cannot be refused as " + refusal);
        };
    }
}
