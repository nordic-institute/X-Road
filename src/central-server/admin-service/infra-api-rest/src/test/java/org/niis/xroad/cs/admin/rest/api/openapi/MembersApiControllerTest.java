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
package org.niis.xroad.cs.admin.rest.api.openapi;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.common.exception.SecurityServerNotFoundException;
import org.niis.xroad.cs.admin.api.domain.DidCheck;
import org.niis.xroad.cs.admin.api.domain.DidResolutionReason;
import org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection;
import org.niis.xroad.cs.admin.api.service.MemberDidCheckService;
import org.niis.xroad.cs.admin.rest.api.converter.DidCheckDtoConverter;
import org.niis.xroad.cs.admin.rest.api.converter.DidCheckDtoConverterImpl;
import org.niis.xroad.cs.openapi.model.DidCheckStatusDto;
import org.niis.xroad.cs.openapi.model.DidRefusalReasonDto;
import org.niis.xroad.cs.openapi.model.DidResolutionReasonDto;
import org.niis.xroad.cs.openapi.model.MemberDidEditDto;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.config.audit.AuditEventMethod;
import org.niis.xroad.restapi.config.audit.RestApiAuditEvent;
import org.niis.xroad.restapi.converter.ClientIdConverter;
import org.niis.xroad.restapi.converter.SecurityServerIdConverter;
import org.springframework.security.access.prepost.PreAuthorize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.MEMBER_CLASS;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.MEMBER_CODE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.MEMBER_DID;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.MEMBER_DID_CHECK_STATUS;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OWNER_CLASS;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OWNER_CODE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.SERVER_CODE;

@ExtendWith(MockitoExtension.class)
class MembersApiControllerTest {

    private static final String MEMBER_ID = "DEV:COM:5678";
    private static final String SERVER_ID = "DEV:COM:1234:SS1";
    private static final String PROPOSED_DID = "did:web:ss.example.org%3A7183:v1:member:COM:5678";

    private final ClientId.Conf memberId = ClientId.Conf.create("DEV", "COM", "5678");
    private final SecurityServerId serverId = SecurityServerId.Conf.create("DEV", "COM", "1234", "SS1");

    @Mock
    private MemberDidCheckService memberDidCheckService;
    @Mock
    private ClientIdConverter clientIdConverter;
    @Mock
    private SecurityServerIdConverter securityServerIdConverter;
    @Mock
    private AuditDataHelper auditDataHelper;

    @Spy
    private DidCheckDtoConverter didCheckDtoConverter = new DidCheckDtoConverterImpl();
    @InjectMocks
    private MembersApiController controller;

    @BeforeEach
    void resolveIds() {
        lenient().when(clientIdConverter.isEncodedMemberId(MEMBER_ID)).thenReturn(true);
        lenient().when(clientIdConverter.convertId(MEMBER_ID)).thenReturn(memberId);
        lenient().when(securityServerIdConverter.convertId(SERVER_ID)).thenReturn(serverId);
    }

    @Test
    void memberDidCheckPassesTheProposedDidAndAnswersResolved() {
        when(memberDidCheckService.check(serverId, memberId, PROPOSED_DID)).thenReturn(DidCheck.RESOLVED);

        var body = controller.checkMemberDid(MEMBER_ID, SERVER_ID, editRequest()).getBody();

        assertThat(body).isNotNull();
        assertThat(body.getStatus()).isEqualTo(DidCheckStatusDto.RESOLVED);
        assertThat(body.getRefusalReason()).isNull();
        assertThat(body.getResolutionReason()).isNull();
        verify(memberDidCheckService).check(serverId, memberId, PROPOSED_DID);
        verifyCheckAudit(DidCheck.Status.RESOLVED);
    }

    @Test
    void memberDidCheckMapsARefusalToItsReason() {
        when(memberDidCheckService.check(serverId, memberId, PROPOSED_DID))
                .thenReturn(DidCheck.refused(ParticipantValueRejection.DID_OF_OTHER_MEMBER));

        var body = controller.checkMemberDid(MEMBER_ID, SERVER_ID, editRequest()).getBody();

        assertThat(body.getStatus()).isEqualTo(DidCheckStatusDto.REFUSED);
        assertThat(body.getRefusalReason()).isEqualTo(DidRefusalReasonDto.DID_OF_OTHER_MEMBER);
        assertThat(body.getResolutionReason()).isNull();
        verifyCheckAudit(DidCheck.Status.REFUSED);
    }

    @Test
    void memberDidCheckMapsAResolutionReason() {
        when(memberDidCheckService.check(serverId, memberId, PROPOSED_DID))
                .thenReturn(DidCheck.notResolved(DidResolutionReason.NO_DOCUMENT));

        var body = controller.checkMemberDid(MEMBER_ID, SERVER_ID, editRequest()).getBody();

        assertThat(body.getStatus()).isEqualTo(DidCheckStatusDto.NOT_RESOLVED);
        assertThat(body.getRefusalReason()).isNull();
        assertThat(body.getResolutionReason()).isEqualTo(DidResolutionReasonDto.NO_DOCUMENT);
        verifyCheckAudit(DidCheck.Status.NOT_RESOLVED);
    }

    @Test
    void memberDidCheckMapsAnUnavailableResolver() {
        when(memberDidCheckService.check(serverId, memberId, PROPOSED_DID)).thenReturn(DidCheck.RESOLVER_UNAVAILABLE);

        var body = controller.checkMemberDid(MEMBER_ID, SERVER_ID, editRequest()).getBody();

        assertThat(body.getStatus()).isEqualTo(DidCheckStatusDto.RESOLVER_UNAVAILABLE);
        assertThat(body.getRefusalReason()).isNull();
        assertThat(body.getResolutionReason()).isNull();
        verifyCheckAudit(DidCheck.Status.RESOLVER_UNAVAILABLE);
    }

    @Test
    void memberDidCheckOfAnUnknownServerAuditsTheRequestWithoutAStatus() {
        when(memberDidCheckService.check(serverId, memberId, PROPOSED_DID))
                .thenThrow(new SecurityServerNotFoundException(serverId));

        var request = editRequest();

        assertThatThrownBy(() -> controller.checkMemberDid(MEMBER_ID, SERVER_ID, request))
                .isInstanceOf(SecurityServerNotFoundException.class);

        verifyCheckAuditRequest();
        verifyNoMoreInteractions(auditDataHelper);
    }

    @Test
    void memberDidCheckOfAMalformedMemberIdIsRefusedBeforeAnythingIsAudited() {
        var request = editRequest();

        assertThatThrownBy(() -> controller.checkMemberDid("not-a-member-id", SERVER_ID, request))
                .isInstanceOf(BadRequestException.class);

        verifyNoInteractions(memberDidCheckService, auditDataHelper);
    }

    @Test
    void memberDidCheckNeedsTheEditMemberDidPermission() throws NoSuchMethodException {
        var method = MembersApiController.class.getMethod("checkMemberDid", String.class, String.class, MemberDidEditDto.class);

        assertThat(method.getAnnotation(PreAuthorize.class).value()).isEqualTo("hasAuthority('EDIT_MEMBER_DID')");
        assertThat(method.getAnnotation(AuditEventMethod.class).event()).isEqualTo(RestApiAuditEvent.CHECK_MEMBER_DID);
    }

    private void verifyCheckAudit(DidCheck.Status status) {
        verifyCheckAuditRequest();
        verify(auditDataHelper).put(MEMBER_DID_CHECK_STATUS, status);
        verifyNoMoreInteractions(auditDataHelper);
    }

    private void verifyCheckAuditRequest() {
        verify(auditDataHelper).put(MEMBER_CLASS, "COM");
        verify(auditDataHelper).put(MEMBER_CODE, "5678");
        verify(auditDataHelper).put(SERVER_CODE, "SS1");
        verify(auditDataHelper).put(OWNER_CLASS, "COM");
        verify(auditDataHelper).put(OWNER_CODE, "1234");
        verify(auditDataHelper).put(MEMBER_DID, PROPOSED_DID);
    }

    private static MemberDidEditDto editRequest() {
        return new MemberDidEditDto().memberDid(PROPOSED_DID);
    }
}
