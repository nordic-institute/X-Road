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
package org.niis.xroad.cs.admin.core.dataspace;

import ee.ria.xroad.common.identifier.ClientId;

import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.cs.admin.api.domain.PublishedValueResult;
import org.niis.xroad.cs.admin.api.domain.SystemPairCheck;
import org.niis.xroad.cs.admin.core.entity.MemberClassEntity;
import org.niis.xroad.cs.admin.core.entity.MemberDidEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerEntity;
import org.niis.xroad.cs.admin.core.entity.ServerClientEntity;
import org.niis.xroad.cs.admin.core.entity.SubsystemEntity;
import org.niis.xroad.cs.admin.core.entity.XRoadMemberEntity;
import org.niis.xroad.cs.admin.core.repository.MemberDidRepository;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;
import org.niis.xroad.cs.admin.core.repository.XRoadMemberRepository;
import org.niis.xroad.restapi.config.audit.AuditEventLoggingFacade;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.niis.xroad.cs.admin.api.domain.PublishedValueRejection.DID_HELD_BY_ANOTHER_SERVER;
import static org.niis.xroad.cs.admin.api.domain.PublishedValueRejection.HOST_DIFFERS;
import static org.niis.xroad.cs.admin.api.domain.PublishedValueRejection.UNRELATED_MEMBER;
import static org.niis.xroad.cs.admin.api.domain.PublishedValueRejection.VALUE_DIFFERS;
import static org.niis.xroad.cs.admin.api.domain.PublishedValueRejection.WRONG_SCHEME;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.REJECT_CONNECTOR_PUBLISHED_VALUE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.MEMBER_CLASS;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.MEMBER_CODE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OWNER_CLASS;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.OWNER_CODE;
import static org.niis.xroad.restapi.config.audit.RestApiAuditProperty.SERVER_CODE;

@ExtendWith(MockitoExtension.class)
class PublishedValueStoreImplTest {

    private static final int SERVER_ID = 7;
    private static final String ADDRESS = "ss0.example.org";

    private static final String SYSTEM_DID = "did:web:ss0.example.org%3A7183:v1:system";
    private static final String DSP_URL = "https://ss0.example.org:8183/api/dsp";
    private static final String OTHER_SYSTEM_DID = "did:web:ss0.example.org%3A7184:v1:system";
    private static final String OTHER_DSP_URL = "https://ss0.example.org:8184/api/dsp";
    private static final String OLD_SYSTEM_DID = "did:web:old.example.org%3A7183:v1:system";
    private static final String OLD_DSP_URL = "https://old.example.org:8183/api/dsp";

    private static final String OWNER_DID = "did:web:ss0.example.org%3A7183:v1:DEV:COM:222";
    private static final String CLIENT_DID = "did:web:ss0.example.org%3A7183:v1:DEV:COM:333";
    private static final String OTHER_CLIENT_DID = "did:web:ss0.example.org%3A7184:v1:DEV:COM:333";
    private static final String OLD_CLIENT_DID = "did:web:old.example.org%3A7183:v1:DEV:COM:333";

    private static final String HELD_REASON = "Published value rejected: " + DID_HELD_BY_ANOTHER_SERVER;

    private static final ClientId OWNER = member("222");
    private static final ClientId CLIENT = member("333");
    private static final ClientId UNRELATED = member("999");

    @Mock
    private SecurityServerRepository securityServerRepository;
    @Mock
    private MemberDidRepository memberDidRepository;
    @Mock
    private XRoadMemberRepository memberRepository;
    @Mock
    private AuditEventLoggingFacade auditLogging;
    @Mock
    private EntityManager entityManager;

    private final Map<Long, MemberDidEntity> storedRows = new HashMap<>();
    private final Set<String> didsOfOtherServers = new HashSet<>();

    private SecurityServerEntity server;
    private XRoadMemberEntity ownerEntity;
    private XRoadMemberEntity clientEntity;
    private PublishedValueStoreImpl store;

    @BeforeEach
    void setUp() {
        var memberClass = new MemberClassEntity("COM", "Companies");
        ownerEntity = memberEntity(memberClass, OWNER, 1L);
        clientEntity = memberEntity(memberClass, CLIENT, 2L);

        server = new SecurityServerEntity(ownerEntity, "SS0");
        ReflectionTestUtils.setField(server, "id", SERVER_ID);
        server.setAddress(ADDRESS);
        server.getServerClients().add(new ServerClientEntity(server,
                new SubsystemEntity(clientEntity, ClientId.Conf.create("DEV", "COM", "333", "sub"))));

        lenient().when(securityServerRepository.lockForUpdateById(SERVER_ID)).thenReturn(Optional.of(SERVER_ID));
        lenient().when(securityServerRepository.findById(SERVER_ID)).thenReturn(Optional.of(server));
        lenient().when(securityServerRepository.existsBySystemDidAndIdNot(anyString(), anyInt())).thenReturn(false);
        lenient().when(memberRepository.findMember(any())).thenAnswer(invocation -> {
            var requested = (ClientId) invocation.getArgument(0);
            return Stream.of(ownerEntity, clientEntity)
                    .filter(entity -> entity.getIdentifier().getMemberCode().equals(requested.getMemberCode()))
                    .findFirst();
        });
        lenient().when(memberDidRepository.findBySecurityServerIdAndMemberId(eq(SERVER_ID), anyLong()))
                .thenAnswer(invocation -> Optional.ofNullable(storedRows.get((Long) invocation.getArgument(1))));
        lenient().when(memberDidRepository.existsByDidAndSecurityServerIdNot(anyString(), eq(SERVER_ID)))
                .thenAnswer(invocation -> didsOfOtherServers.contains((String) invocation.getArgument(0)));
        lenient().when(memberDidRepository.saveAndFlush(any())).thenAnswer(invocation -> {
            var row = (MemberDidEntity) invocation.getArgument(0);
            storedRows.put(row.getMember().getId(), row);
            return row;
        });

        store = new PublishedValueStoreImpl(securityServerRepository, memberDidRepository, memberRepository,
                auditLogging, entityManager);
    }

    @Test
    void shouldWriteSystemPairWhenServerHasNone() {
        var result = store.writeSystemPairOnce(SERVER_ID, SYSTEM_DID, DSP_URL);

        assertThat(result).isEqualTo(PublishedValueResult.STORED);
        assertThat(server.getSystemDid()).isEqualTo(SYSTEM_DID);
        assertThat(server.getDspBaseUrl()).isEqualTo(DSP_URL);
        verify(securityServerRepository).saveAndFlush(server);
    }

    @Test
    void shouldCheckSystemPairAsAbsentWhenServerHasNoneAndWriteNothing() {
        var result = store.checkSystemPair(SERVER_ID, SYSTEM_DID, DSP_URL);

        assertThat(result).isEqualTo(SystemPairCheck.ABSENT);
        assertThat(server.getSystemDid()).isNull();
        verify(securityServerRepository, never()).saveAndFlush(any());
        verify(securityServerRepository, never()).lockForUpdateById(anyInt());
        verify(securityServerRepository, never()).lockForUpdateBy(any());
    }

    @Test
    void shouldCheckSystemPairAsEqualWhenStoredPairMatches() {
        server.setSystemDid(SYSTEM_DID);
        server.setDspBaseUrl(DSP_URL);

        assertThat(store.checkSystemPair(SERVER_ID, SYSTEM_DID, DSP_URL)).isEqualTo(SystemPairCheck.EQUAL);
    }

    @Test
    void shouldCheckSystemPairAsStaleReplaceableWhenOnlyTheStoredDspBaseUrlIsStale() {
        server.setSystemDid(SYSTEM_DID);
        server.setDspBaseUrl(OLD_DSP_URL);

        assertThat(store.checkSystemPair(SERVER_ID, SYSTEM_DID, DSP_URL)).isEqualTo(SystemPairCheck.STALE_REPLACEABLE);
    }

    @Test
    void shouldCheckSystemPairAsValueDiffersWhenTheStoredPairIsCurrent() {
        server.setSystemDid(SYSTEM_DID);
        server.setDspBaseUrl(DSP_URL);

        assertThat(store.checkSystemPair(SERVER_ID, SYSTEM_DID, OTHER_DSP_URL)).isEqualTo(SystemPairCheck.VALUE_DIFFERS);
    }

    @Test
    void shouldCheckSystemPairAsRejectedWhenAnotherServerHoldsTheDid() {
        when(securityServerRepository.existsBySystemDidAndIdNot(SYSTEM_DID, SERVER_ID)).thenReturn(true);

        var result = store.checkSystemPair(SERVER_ID, SYSTEM_DID, DSP_URL);

        assertThat(result).isEqualTo(SystemPairCheck.rejected(DID_HELD_BY_ANOTHER_SERVER));
        verifyNoInteractions(auditLogging);
    }

    @ParameterizedTest
    @MethodSource("systemPairCases")
    void shouldAnswerTheSystemPairCheckTheWayTheWriteOnceOperationDecides(SystemPairCase pairCase) {
        pairCase.storedDid().ifPresent(server::setSystemDid);
        pairCase.storedDspBaseUrl().ifPresent(server::setDspBaseUrl);
        lenient().when(securityServerRepository.existsBySystemDidAndIdNot(any(), eq(SERVER_ID))).thenReturn(pairCase.heldByAnother());

        var check = store.checkSystemPair(SERVER_ID, pairCase.did(), pairCase.dspBaseUrl());
        var written = store.writeSystemPairOnce(SERVER_ID, pairCase.did(), pairCase.dspBaseUrl());

        assertThat(check).isEqualTo(pairCase.expectedCheck());
        assertThat(written).isEqualTo(pairCase.expectedWrite());
    }

    private static Stream<SystemPairCase> systemPairCases() {
        return Stream.of(
                new SystemPairCase("absent", SYSTEM_DID, DSP_URL, null, null, false,
                        SystemPairCheck.ABSENT, PublishedValueResult.STORED),
                new SystemPairCase("equal", SYSTEM_DID, DSP_URL, SYSTEM_DID, DSP_URL, false,
                        SystemPairCheck.EQUAL, PublishedValueResult.UNCHANGED),
                new SystemPairCase("stale", SYSTEM_DID, DSP_URL, OLD_SYSTEM_DID, OLD_DSP_URL, false,
                        SystemPairCheck.STALE_REPLACEABLE, PublishedValueResult.STORED),
                new SystemPairCase("value differs", OTHER_SYSTEM_DID, OTHER_DSP_URL, SYSTEM_DID, DSP_URL, false,
                        SystemPairCheck.VALUE_DIFFERS, PublishedValueResult.rejected(VALUE_DIFFERS)),
                new SystemPairCase("validator rejection", null, null, null, null, false,
                        SystemPairCheck.rejected(WRONG_SCHEME), PublishedValueResult.rejected(WRONG_SCHEME)),
                new SystemPairCase("held, absent", SYSTEM_DID, DSP_URL, null, null, true,
                        SystemPairCheck.rejected(DID_HELD_BY_ANOTHER_SERVER),
                        PublishedValueResult.rejected(DID_HELD_BY_ANOTHER_SERVER)),
                new SystemPairCase("held, stale", SYSTEM_DID, DSP_URL, OLD_SYSTEM_DID, OLD_DSP_URL, true,
                        SystemPairCheck.rejected(DID_HELD_BY_ANOTHER_SERVER),
                        PublishedValueResult.rejected(DID_HELD_BY_ANOTHER_SERVER)),
                new SystemPairCase("held, equal", SYSTEM_DID, DSP_URL, SYSTEM_DID, DSP_URL, true,
                        SystemPairCheck.EQUAL, PublishedValueResult.UNCHANGED),
                new SystemPairCase("held, value differs", OTHER_SYSTEM_DID, OTHER_DSP_URL, SYSTEM_DID, DSP_URL, true,
                        SystemPairCheck.VALUE_DIFFERS, PublishedValueResult.rejected(VALUE_DIFFERS)));
    }

    private record SystemPairCase(String name, String did, String dspBaseUrl, String storedDidOrNull,
                                  String storedDspBaseUrlOrNull, boolean heldByAnother,
                                  SystemPairCheck expectedCheck, PublishedValueResult expectedWrite) {

        Optional<String> storedDid() {
            return Optional.ofNullable(storedDidOrNull);
        }

        Optional<String> storedDspBaseUrl() {
            return Optional.ofNullable(storedDspBaseUrlOrNull);
        }

        @Override
        public String toString() {
            return name;
        }
    }

    @Test
    void shouldPassTheValidatorRejectionThroughOnCheck() {
        var result = store.checkSystemPair(SERVER_ID, null, null);

        assertThat(result).isEqualTo(SystemPairCheck.rejected(WRONG_SCHEME));
    }

    @Test
    void shouldFailTheSystemPairCheckForUnknownServer() {
        when(securityServerRepository.findById(99)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> store.checkSystemPair(99, SYSTEM_DID, DSP_URL)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void shouldLeaveSystemPairUntouchedOnEqualResend() {
        store.writeSystemPairOnce(SERVER_ID, SYSTEM_DID, DSP_URL);

        var result = store.writeSystemPairOnce(SERVER_ID, SYSTEM_DID, DSP_URL);

        assertThat(result).isEqualTo(PublishedValueResult.UNCHANGED);
        verify(securityServerRepository, times(1)).saveAndFlush(any());
    }

    @Test
    void shouldRejectDifferentSystemPairForSameAddress() {
        store.writeSystemPairOnce(SERVER_ID, SYSTEM_DID, DSP_URL);

        var result = store.writeSystemPairOnce(SERVER_ID, OTHER_SYSTEM_DID, OTHER_DSP_URL);

        assertThat(result).isEqualTo(PublishedValueResult.rejected(VALUE_DIFFERS));
        assertThat(server.getSystemDid()).isEqualTo(SYSTEM_DID);
        assertThat(server.getDspBaseUrl()).isEqualTo(DSP_URL);
        verify(securityServerRepository, times(1)).saveAndFlush(any());
    }

    @Test
    void shouldRejectSystemPairWhenOnlyTheDspBaseUrlDiffers() {
        store.writeSystemPairOnce(SERVER_ID, SYSTEM_DID, DSP_URL);

        var result = store.writeSystemPairOnce(SERVER_ID, SYSTEM_DID, OTHER_DSP_URL);

        assertThat(result).isEqualTo(PublishedValueResult.rejected(VALUE_DIFFERS));
        assertThat(server.getDspBaseUrl()).isEqualTo(DSP_URL);
    }

    @Test
    void shouldReplaceStaleSystemPair() {
        server.setSystemDid(OLD_SYSTEM_DID);
        server.setDspBaseUrl(OLD_DSP_URL);

        var result = store.writeSystemPairOnce(SERVER_ID, SYSTEM_DID, DSP_URL);

        assertThat(result).isEqualTo(PublishedValueResult.STORED);
        assertThat(server.getSystemDid()).isEqualTo(SYSTEM_DID);
        assertThat(server.getDspBaseUrl()).isEqualTo(DSP_URL);
    }

    @Test
    void shouldReplaceSystemPairAsOneUnitWhenOnlyTheDspBaseUrlIsStale() {
        server.setSystemDid(SYSTEM_DID);
        server.setDspBaseUrl(OLD_DSP_URL);

        var result = store.writeSystemPairOnce(SERVER_ID, OTHER_SYSTEM_DID, DSP_URL);

        assertThat(result).isEqualTo(PublishedValueResult.STORED);
        assertThat(server.getSystemDid()).isEqualTo(OTHER_SYSTEM_DID);
        assertThat(server.getDspBaseUrl()).isEqualTo(DSP_URL);
    }

    @Test
    void shouldReplaceSystemPairAsOneUnitWhenOnlyTheDidIsStale() {
        server.setSystemDid(OLD_SYSTEM_DID);
        server.setDspBaseUrl(DSP_URL);

        var result = store.writeSystemPairOnce(SERVER_ID, SYSTEM_DID, OTHER_DSP_URL);

        assertThat(result).isEqualTo(PublishedValueResult.STORED);
        assertThat(server.getSystemDid()).isEqualTo(SYSTEM_DID);
        assertThat(server.getDspBaseUrl()).isEqualTo(OTHER_DSP_URL);
    }

    @Test
    void shouldFollowStaleSystemPairReplacementWithUnchangedEqualResend() {
        server.setSystemDid(OLD_SYSTEM_DID);
        server.setDspBaseUrl(OLD_DSP_URL);
        store.writeSystemPairOnce(SERVER_ID, SYSTEM_DID, DSP_URL);

        var result = store.writeSystemPairOnce(SERVER_ID, SYSTEM_DID, DSP_URL);

        assertThat(result).isEqualTo(PublishedValueResult.UNCHANGED);
        verify(securityServerRepository, times(1)).saveAndFlush(any());
    }

    @Test
    void shouldRejectSystemPairResendAfterAdminReplace() {
        store.writeSystemPairOnce(SERVER_ID, SYSTEM_DID, DSP_URL);
        var replaced = store.replaceSystemPair(SERVER_ID, OTHER_SYSTEM_DID, OTHER_DSP_URL);

        var resend = store.writeSystemPairOnce(SERVER_ID, SYSTEM_DID, DSP_URL);

        assertThat(replaced).isEqualTo(PublishedValueResult.STORED);
        assertThat(resend).isEqualTo(PublishedValueResult.rejected(VALUE_DIFFERS));
        assertThat(server.getSystemDid()).isEqualTo(OTHER_SYSTEM_DID);
    }

    @Test
    void shouldReplaceExistingSystemPairOnAdminReplace() {
        server.setSystemDid(SYSTEM_DID);
        server.setDspBaseUrl(DSP_URL);

        var result = store.replaceSystemPair(SERVER_ID, OTHER_SYSTEM_DID, OTHER_DSP_URL);

        assertThat(result).isEqualTo(PublishedValueResult.STORED);
        assertThat(server.getSystemDid()).isEqualTo(OTHER_SYSTEM_DID);
        assertThat(server.getDspBaseUrl()).isEqualTo(OTHER_DSP_URL);
    }

    @Test
    void shouldNotAddSystemPairOnAdminReplace() {
        var result = store.replaceSystemPair(SERVER_ID, SYSTEM_DID, DSP_URL);

        assertThat(result).isEqualTo(PublishedValueResult.UNCHANGED);
        assertThat(server.getSystemDid()).isNull();
        assertThat(server.getDspBaseUrl()).isNull();
        verify(securityServerRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldNotClearSystemPairOnAdminReplace() {
        server.setSystemDid(SYSTEM_DID);
        server.setDspBaseUrl(DSP_URL);

        var result = store.replaceSystemPair(SERVER_ID, null, null);

        assertThat(result).isEqualTo(PublishedValueResult.rejected(WRONG_SCHEME));
        assertThat(server.getSystemDid()).isEqualTo(SYSTEM_DID);
        assertThat(server.getDspBaseUrl()).isEqualTo(DSP_URL);
        verify(securityServerRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldRejectSystemPairHeldByAnotherServerAndAudit() {
        when(securityServerRepository.existsBySystemDidAndIdNot(SYSTEM_DID, SERVER_ID)).thenReturn(true);

        var result = store.writeSystemPairOnce(SERVER_ID, SYSTEM_DID, DSP_URL);

        assertThat(result).isEqualTo(PublishedValueResult.rejected(DID_HELD_BY_ANOTHER_SERVER));
        assertThat(server.getSystemDid()).isNull();
        verify(securityServerRepository, never()).saveAndFlush(any());
        verify(auditLogging).auditLogFailWithData(REJECT_CONNECTOR_PUBLISHED_VALUE, HELD_REASON, Map.of(
                SERVER_CODE, "SS0",
                OWNER_CLASS, "COM",
                OWNER_CODE, "222"));
    }

    @Test
    void shouldPropagateRepositoryExceptionOnSystemPairWrite() {
        var failure = new DataIntegrityViolationException("duplicate");
        doThrow(failure).when(securityServerRepository).saveAndFlush(server);

        assertThatThrownBy(() -> store.writeSystemPairOnce(SERVER_ID, SYSTEM_DID, DSP_URL)).isSameAs(failure);
        verifyNoInteractions(auditLogging);
    }

    @Test
    void shouldWriteMemberDidsWhenNoneStored() {
        var results = store.writeMemberDidsOnce(SERVER_ID, batch(OWNER_DID, CLIENT_DID));

        assertThat(results).containsEntry(OWNER, PublishedValueResult.STORED)
                .containsEntry(CLIENT, PublishedValueResult.STORED);
        assertThat(storedRows.get(1L).getDid()).isEqualTo(OWNER_DID);
        assertThat(storedRows.get(2L).getDid()).isEqualTo(CLIENT_DID);
    }

    @Test
    void shouldLeaveMemberDidUntouchedOnEqualResend() {
        store.writeMemberDidsOnce(SERVER_ID, batch(OWNER_DID, CLIENT_DID));

        var results = store.writeMemberDidsOnce(SERVER_ID, batch(OWNER_DID, CLIENT_DID));

        assertThat(results.values()).containsOnly(PublishedValueResult.UNCHANGED);
        verify(memberDidRepository, times(2)).saveAndFlush(any());
    }

    @Test
    void shouldRejectDifferentMemberDidForSameAddress() {
        store.writeMemberDidsOnce(SERVER_ID, batch(OWNER_DID, CLIENT_DID));

        var results = store.writeMemberDidsOnce(SERVER_ID, Map.of(CLIENT, OTHER_CLIENT_DID));

        assertThat(results).containsEntry(CLIENT, PublishedValueResult.rejected(VALUE_DIFFERS));
        assertThat(storedRows.get(2L).getDid()).isEqualTo(CLIENT_DID);
        verify(memberDidRepository, times(2)).saveAndFlush(any());
    }

    @Test
    void shouldReplaceStaleMemberDidInPlace() {
        var staleRow = new MemberDidEntity(server, clientEntity, OLD_CLIENT_DID);
        storedRows.put(2L, staleRow);

        var results = store.writeMemberDidsOnce(SERVER_ID, Map.of(CLIENT, CLIENT_DID));

        assertThat(results).containsEntry(CLIENT, PublishedValueResult.STORED);
        assertThat(storedRows).hasSize(1);
        assertThat(storedRows.get(2L)).isSameAs(staleRow);
        assertThat(staleRow.getDid()).isEqualTo(CLIENT_DID);
        verify(memberDidRepository, never()).delete(any());
    }

    @Test
    void shouldFollowStaleMemberDidReplacementWithUnchangedEqualResend() {
        storedRows.put(2L, new MemberDidEntity(server, clientEntity, OLD_CLIENT_DID));
        store.writeMemberDidsOnce(SERVER_ID, Map.of(CLIENT, CLIENT_DID));

        var results = store.writeMemberDidsOnce(SERVER_ID, Map.of(CLIENT, CLIENT_DID));

        assertThat(results).containsEntry(CLIENT, PublishedValueResult.UNCHANGED);
        verify(memberDidRepository, times(1)).saveAndFlush(any());
    }

    @Test
    void shouldReplaceMemberDidInPlaceOnAdminReplace() {
        var row = new MemberDidEntity(server, clientEntity, CLIENT_DID);
        storedRows.put(2L, row);

        var results = store.replaceMemberDids(SERVER_ID, Map.of(CLIENT, OTHER_CLIENT_DID));

        assertThat(results).containsEntry(CLIENT, PublishedValueResult.STORED);
        assertThat(storedRows).hasSize(1);
        assertThat(storedRows.get(2L)).isSameAs(row);
        assertThat(row.getDid()).isEqualTo(OTHER_CLIENT_DID);
        verify(memberDidRepository, never()).delete(any());
    }

    @Test
    void shouldNotAddMemberDidOnAdminReplace() {
        storedRows.put(1L, new MemberDidEntity(server, ownerEntity, OWNER_DID));

        var results = store.replaceMemberDids(SERVER_ID, Map.of(CLIENT, CLIENT_DID));

        assertThat(results).containsEntry(CLIENT, PublishedValueResult.UNCHANGED);
        assertThat(storedRows).containsOnlyKeys(1L);
        verify(memberDidRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldRejectMemberDidResendAfterAdminReplace() {
        store.writeMemberDidsOnce(SERVER_ID, Map.of(CLIENT, CLIENT_DID));
        var replaced = store.replaceMemberDids(SERVER_ID, Map.of(CLIENT, OTHER_CLIENT_DID));

        var resend = store.writeMemberDidsOnce(SERVER_ID, Map.of(CLIENT, CLIENT_DID));

        assertThat(replaced).containsEntry(CLIENT, PublishedValueResult.STORED);
        assertThat(resend).containsEntry(CLIENT, PublishedValueResult.rejected(VALUE_DIFFERS));
        assertThat(storedRows.get(2L).getDid()).isEqualTo(OTHER_CLIENT_DID);
    }

    @Test
    void shouldRejectMemberDidHeldByAnotherServerStoreTheRestAndAudit() {
        didsOfOtherServers.add(CLIENT_DID);

        var results = store.writeMemberDidsOnce(SERVER_ID, batch(OWNER_DID, CLIENT_DID));

        assertThat(results).containsEntry(OWNER, PublishedValueResult.STORED)
                .containsEntry(CLIENT, PublishedValueResult.rejected(DID_HELD_BY_ANOTHER_SERVER));
        assertThat(storedRows).containsOnlyKeys(1L);
        verify(auditLogging, times(1)).auditLogFailWithData(REJECT_CONNECTOR_PUBLISHED_VALUE, HELD_REASON, Map.of(
                SERVER_CODE, "SS0",
                OWNER_CLASS, "COM",
                OWNER_CODE, "222",
                MEMBER_CLASS, "COM",
                MEMBER_CODE, "333"));
    }

    @Test
    void shouldPropagateRepositoryExceptionOnMemberDidWriteWithoutPartialResult() {
        var failure = new DataIntegrityViolationException("duplicate");
        doThrow(failure).when(memberDidRepository)
                .saveAndFlush(argThat((MemberDidEntity row) -> row.getMember() == clientEntity));

        assertThatThrownBy(() -> store.writeMemberDidsOnce(SERVER_ID, batch(OWNER_DID, CLIENT_DID))).isSameAs(failure);
        verifyNoInteractions(auditLogging);
    }

    @Test
    void shouldPropagateRepositoryExceptionOnMemberDidUpdate() {
        var row = new MemberDidEntity(server, clientEntity, OLD_CLIENT_DID);
        storedRows.put(2L, row);
        var failure = new DataIntegrityViolationException("duplicate");
        doThrow(failure).when(memberDidRepository).saveAndFlush(row);

        assertThatThrownBy(() -> store.writeMemberDidsOnce(SERVER_ID, Map.of(CLIENT, CLIENT_DID))).isSameAs(failure);
    }

    @Test
    void shouldLockServerRowBeforeReadingOrWritingAnyDidRow() {
        store.writeMemberDidsOnce(SERVER_ID, batch(OWNER_DID, CLIENT_DID));

        InOrder order = inOrder(securityServerRepository, entityManager, memberDidRepository);
        order.verify(securityServerRepository).lockForUpdateById(SERVER_ID);
        order.verify(securityServerRepository).findById(SERVER_ID);
        order.verify(entityManager).refresh(server);
        order.verify(memberDidRepository).findBySecurityServerIdAndMemberId(eq(SERVER_ID), anyLong());
        order.verify(memberDidRepository).existsByDidAndSecurityServerIdNot(anyString(), eq(SERVER_ID));
        order.verify(memberDidRepository).saveAndFlush(any());
    }

    @Test
    void shouldLockServerRowBeforeCheckingSystemDidUniqueness() {
        store.writeSystemPairOnce(SERVER_ID, SYSTEM_DID, DSP_URL);

        InOrder order = inOrder(securityServerRepository, entityManager);
        order.verify(securityServerRepository).lockForUpdateById(SERVER_ID);
        order.verify(securityServerRepository).findById(SERVER_ID);
        order.verify(entityManager).refresh(server);
        order.verify(securityServerRepository).existsBySystemDidAndIdNot(SYSTEM_DID, SERVER_ID);
        order.verify(securityServerRepository).saveAndFlush(server);
    }

    @Test
    void shouldTouchNoValueWhenServerRowIsGone() {
        when(securityServerRepository.lockForUpdateById(SERVER_ID)).thenReturn(Optional.empty());
        var memberDids = Map.of(CLIENT, CLIENT_DID);

        assertThatThrownBy(() -> store.writeMemberDidsOnce(SERVER_ID, memberDids))
                .isInstanceOf(NotFoundException.class);
        assertThatThrownBy(() -> store.writeSystemPairOnce(SERVER_ID, SYSTEM_DID, DSP_URL))
                .isInstanceOf(NotFoundException.class);

        verifyNoInteractions(memberDidRepository);
        verify(securityServerRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldValidateAgainstClientsAsCommittedUnderTheLock() {
        doAnswer(invocation -> {
            server.getServerClients().clear();
            return null;
        }).when(entityManager).refresh(server);

        var results = store.writeMemberDidsOnce(SERVER_ID, Map.of(CLIENT, CLIENT_DID));

        assertThat(results).containsEntry(CLIENT, PublishedValueResult.rejected(UNRELATED_MEMBER));
        verify(memberDidRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldCompareSystemPairWithTheValueCommittedUnderTheLock() {
        doAnswer(invocation -> {
            server.setSystemDid(SYSTEM_DID);
            server.setDspBaseUrl(DSP_URL);
            return null;
        }).when(entityManager).refresh(server);

        var result = store.writeSystemPairOnce(SERVER_ID, OTHER_SYSTEM_DID, OTHER_DSP_URL);

        assertThat(result).isEqualTo(PublishedValueResult.rejected(VALUE_DIFFERS));
        assertThat(server.getSystemDid()).isEqualTo(SYSTEM_DID);
        verify(securityServerRepository, never()).saveAndFlush(any());
    }

    @Test
    void shouldRejectMemberDidWhenClientWasRemovedBeforeTheStoreCall() {
        server.getServerClients().clear();

        var results = store.writeMemberDidsOnce(SERVER_ID, batch(OWNER_DID, CLIENT_DID));

        assertThat(results).containsEntry(OWNER, PublishedValueResult.STORED)
                .containsEntry(CLIENT, PublishedValueResult.rejected(UNRELATED_MEMBER));
        assertThat(storedRows).containsOnlyKeys(1L);
    }

    @Test
    void shouldRejectMemberDidForMemberThatIsNeitherOwnerNorClient() {
        var results = store.writeMemberDidsOnce(SERVER_ID,
                Map.of(UNRELATED, "did:web:ss0.example.org%3A7183:v1:DEV:COM:999"));

        assertThat(results).containsEntry(UNRELATED, PublishedValueResult.rejected(UNRELATED_MEMBER));
        assertThat(storedRows).isEmpty();
    }

    @Test
    void shouldRejectValuesWhenAddressChangedBeforeTheStoreCall() {
        server.setAddress("moved.example.org");

        var pair = store.writeSystemPairOnce(SERVER_ID, SYSTEM_DID, DSP_URL);
        var members = store.replaceMemberDids(SERVER_ID, Map.of(CLIENT, CLIENT_DID));

        assertThat(pair).isEqualTo(PublishedValueResult.rejected(HOST_DIFFERS));
        assertThat(members).containsEntry(CLIENT, PublishedValueResult.rejected(HOST_DIFFERS));
        assertThat(server.getSystemDid()).isNull();
        verify(securityServerRepository, never()).saveAndFlush(any());
        verify(memberDidRepository, never()).saveAndFlush(any());
    }

    private static Map<ClientId, String> batch(String ownerDid, String clientDid) {
        var batch = new LinkedHashMap<ClientId, String>();
        batch.put(OWNER, ownerDid);
        batch.put(CLIENT, clientDid);
        return batch;
    }

    private static ClientId member(String code) {
        return ClientId.Conf.create("DEV", "COM", code);
    }

    private static XRoadMemberEntity memberEntity(MemberClassEntity memberClass, ClientId identifier, long id) {
        var entity = new XRoadMemberEntity("Member " + identifier.getMemberCode(), identifier, memberClass);
        ReflectionTestUtils.setField(entity, "id", id);
        return entity;
    }
}
