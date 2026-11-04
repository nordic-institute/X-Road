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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.apicatalog.did.Did;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.ds.identity.ParticipantIdentifierScheme;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties;
import org.niis.xroad.securityserver.restapi.config.AdminServiceProperties.Dataspace;
import org.niis.xroad.securityserver.restapi.repository.DsParticipantRepository;
import org.niis.xroad.serverconf.impl.entity.DsParticipantEntity;
import org.niis.xroad.serverconf.impl.ownserver.OwnAddress;
import org.niis.xroad.serverconf.impl.ownserver.OwnSecurityServerResolver;
import org.niis.xroad.serverconf.model.ParticipantState;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataspaceParticipantBindingServiceTest {

    private static final ClientId MEMBER = ClientId.Conf.create("TEST", "ORG", "MEMBER");
    private static final ClientId OTHER_MEMBER = ClientId.Conf.create("TEST", "ORG", "OTHER");
    private static final SecurityServerId SERVER_ID = SecurityServerId.Conf.create(MEMBER, "SS1");
    private static final String SS_HOST = "ss.example.test:7183";
    private static final String OLD_SS_HOST = "old.example.test:7183";
    private static final Did SYSTEM_DID_AT_OLD_ADDRESS = ParticipantIdentifierScheme.systemDid(OLD_SS_HOST);

    @Mock
    private AdminServiceProperties adminServiceProperties;
    @Mock
    private Dataspace dataspace;
    @Mock
    private DsParticipantRepository dsParticipantRepository;
    @Mock
    private OwnSecurityServerResolver ownSecurityServerResolver;

    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();
    private final Logger serviceLogger = (Logger) LoggerFactory.getLogger(DataspaceParticipantBindingService.class);

    private DataspaceDidAuthority didAuthority;
    private DataspaceParticipantBindingService service;

    @BeforeEach
    void setUp() {
        lenient().when(dataspace.getDidPort()).thenReturn(7183);
        lenient().when(adminServiceProperties.getDataspace()).thenReturn(dataspace);
        lenient().when(ownSecurityServerResolver.address()).thenReturn(new OwnAddress.Registered(SERVER_ID, "ss.example.test"));

        didAuthority = new DataspaceDidAuthority(ownSecurityServerResolver, adminServiceProperties);
        service = new DataspaceParticipantBindingService(dsParticipantRepository, didAuthority);
        appender.start();
        serviceLogger.addAppender(appender);
        serviceLogger.setLevel(Level.INFO);
    }

    @AfterEach
    void tearDown() {
        serviceLogger.detachAppender(appender);
        appender.stop();
        serviceLogger.setLevel(null);
    }

    @Test
    void bindsDerivedIdentifiersForEveryUnboundMember() {
        when(dsParticipantRepository.findByMemberIdentifier(any())).thenReturn(Optional.empty());

        assertThat(service.bindMembersIfAbsent(checkedDids(MEMBER, OTHER_MEMBER))).containsExactly(MEMBER, OTHER_MEMBER);

        verify(dsParticipantRepository).bindMemberParticipant(MEMBER,
                ParticipantIdentifierScheme.memberCtxId(MEMBER),
                ParticipantIdentifierScheme.memberDid(MEMBER, SS_HOST).toString());
        verify(dsParticipantRepository).bindMemberParticipant(OTHER_MEMBER,
                ParticipantIdentifierScheme.memberCtxId(OTHER_MEMBER),
                ParticipantIdentifierScheme.memberDid(OTHER_MEMBER, SS_HOST).toString());
    }

    @Test
    void leavesAlreadyBoundMembersUntouchedAndReportsThemBound() {
        var boundRow = rowIn(ParticipantState.ACTIVE);
        when(dsParticipantRepository.findByMemberIdentifier(MEMBER)).thenReturn(Optional.of(boundRow));
        when(dsParticipantRepository.findByMemberIdentifier(OTHER_MEMBER)).thenReturn(Optional.empty());

        assertThat(service.bindMembersIfAbsent(checkedDids(MEMBER, OTHER_MEMBER))).containsExactly(MEMBER, OTHER_MEMBER);

        verify(dsParticipantRepository, never()).bindMemberParticipant(eq(MEMBER), anyString(), anyString());
        verify(dsParticipantRepository).bindMemberParticipant(eq(OTHER_MEMBER), anyString(), anyString());
    }

    @Test
    void writesNothingWhenEveryMemberIsAlreadyBound() {
        var boundRow = rowIn(ParticipantState.ACTIVE);
        when(dsParticipantRepository.findByMemberIdentifier(any())).thenReturn(Optional.of(boundRow));

        assertThat(service.bindMembersIfAbsent(checkedDids(MEMBER))).containsExactly(MEMBER);

        verify(dsParticipantRepository, never()).bindMemberParticipant(any(), anyString(), anyString());
    }

    @Test
    void neitherRebindsNorReportsAMemberWhoseRowIsDecommissioned() {
        var tombstone = rowIn(ParticipantState.DECOMMISSIONED);
        when(dsParticipantRepository.findByMemberIdentifier(any())).thenReturn(Optional.of(tombstone));

        assertThat(service.bindMembersIfAbsent(checkedDids(MEMBER))).isEmpty();

        verify(dsParticipantRepository, never()).bindMemberParticipant(any(), anyString(), anyString());
    }

    @Test
    void keepsBindingTheRemainingMembersWhenOneFails() {
        when(dsParticipantRepository.findByMemberIdentifier(any())).thenReturn(Optional.empty());
        when(dsParticipantRepository.bindMemberParticipant(eq(MEMBER), anyString(), anyString()))
                .thenThrow(new DataIntegrityViolationException("uniq_ds_participant_member_identifier"));

        assertThat(service.bindMembersIfAbsent(checkedDids(MEMBER, OTHER_MEMBER))).containsExactly(OTHER_MEMBER);

        verify(dsParticipantRepository).bindMemberParticipant(eq(OTHER_MEMBER), anyString(), anyString());
    }

    @Test
    void bindsNoMemberWhileTheRegisteredAddressIsUnknown() {
        when(dsParticipantRepository.findByMemberIdentifier(any())).thenReturn(Optional.empty());
        when(ownSecurityServerResolver.address()).thenReturn(new OwnAddress.NotRegistered(SERVER_ID));

        assertThat(service.bindMembersIfAbsent(Map.of(MEMBER, memberDidAtOldAddress(MEMBER)))).isEmpty();

        verify(dsParticipantRepository, never()).bindMemberParticipant(any(), anyString(), anyString());
        assertThat(logged("could not bind participant identity for member")).hasSize(1);
        assertThat(logged("differs from the checked DID")).isEmpty();
    }

    @Test
    void bindsTheDerivedSystemIdentifierWhenNoSystemRowExists() {
        when(dsParticipantRepository.findSystemParticipant()).thenReturn(Optional.empty());

        assertThat(service.bindSystemIfAbsent(checkedSystemDid())).isTrue();

        verify(dsParticipantRepository).bindSystemParticipant(ParticipantIdentifierScheme.SYSTEM_SEGMENT,
                ParticipantIdentifierScheme.systemDid(SS_HOST).toString());
    }

    @Test
    void leavesAnActiveSystemRowUntouchedAndReportsItBound() {
        var boundRow = rowIn(ParticipantState.ACTIVE);
        when(dsParticipantRepository.findSystemParticipant()).thenReturn(Optional.of(boundRow));

        assertThat(service.bindSystemIfAbsent(checkedSystemDid())).isTrue();

        verify(dsParticipantRepository, never()).bindSystemParticipant(anyString(), anyString());
    }

    @Test
    void writesOneSystemRowAcrossTwoPasses() {
        var boundRow = rowIn(ParticipantState.ACTIVE);
        when(dsParticipantRepository.findSystemParticipant())
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(boundRow));

        assertThat(service.bindSystemIfAbsent(checkedSystemDid())).isTrue();
        assertThat(service.bindSystemIfAbsent(checkedSystemDid())).isTrue();

        verify(dsParticipantRepository, times(1)).bindSystemParticipant(anyString(), anyString());
    }

    @Test
    void neitherRebindsNorReportsASystemRowThatIsDecommissioned() {
        var tombstone = rowIn(ParticipantState.DECOMMISSIONED);
        when(dsParticipantRepository.findSystemParticipant()).thenReturn(Optional.of(tombstone));

        assertThat(service.bindSystemIfAbsent(checkedSystemDid())).isFalse();

        verify(dsParticipantRepository, never()).bindSystemParticipant(anyString(), anyString());
    }

    @Test
    void reportsTheSystemIdentifierNotBoundWhenTheWriteFails() {
        when(dsParticipantRepository.findSystemParticipant()).thenReturn(Optional.empty());
        when(dsParticipantRepository.bindSystemParticipant(anyString(), anyString()))
                .thenThrow(new DataIntegrityViolationException("uniq_ds_participant_system"));

        assertThat(service.bindSystemIfAbsent(checkedSystemDid())).isFalse();
    }

    @Test
    void bindsNoSystemIdentifierWhileTheRegisteredAddressIsUnknown() {
        when(dsParticipantRepository.findSystemParticipant()).thenReturn(Optional.empty());
        when(ownSecurityServerResolver.address()).thenReturn(new OwnAddress.NotRegistered(SERVER_ID));

        assertThat(service.bindSystemIfAbsent(SYSTEM_DID_AT_OLD_ADDRESS)).isFalse();

        verify(dsParticipantRepository, never()).bindSystemParticipant(anyString(), anyString());
        assertThat(logged("could not bind participant identity for the SYSTEM context")).hasSize(1);
        assertThat(logged("differs from the checked DID")).isEmpty();
    }

    @Test
    void writesNothingForAMemberWhoseDidMintingIsRejected() {
        when(dsParticipantRepository.findByMemberIdentifier(any())).thenReturn(Optional.empty());
        when(ownSecurityServerResolver.address()).thenReturn(new OwnAddress.Registered(SERVER_ID, "b\u00fccher.example"));

        assertThat(service.bindMembersIfAbsent(checkedDids(MEMBER, OTHER_MEMBER))).isEmpty();

        verify(dsParticipantRepository, never()).bindMemberParticipant(any(), anyString(), anyString());
        assertThat(logged("no DID can be minted")).hasSize(2);
        assertThat(logged("differs from the checked DID")).isEmpty();
    }

    @Test
    void keepsBindingTheRemainingMembersWhenOneDidIsRejected() {
        when(dsParticipantRepository.findByMemberIdentifier(any())).thenReturn(Optional.empty());
        var host = DidLengthTestHosts.hostForMemberDidLength(OTHER_MEMBER, ParticipantIdentifierScheme.MAX_DID_LENGTH);
        when(ownSecurityServerResolver.address()).thenReturn(new OwnAddress.Registered(SERVER_ID, host));
        when(dataspace.getDidPort()).thenReturn(443);

        assertThat(service.bindMembersIfAbsent(checkedDids(MEMBER, OTHER_MEMBER))).containsExactly(OTHER_MEMBER);

        verify(dsParticipantRepository, never()).bindMemberParticipant(eq(MEMBER), anyString(), anyString());
        verify(dsParticipantRepository).bindMemberParticipant(eq(OTHER_MEMBER), anyString(), anyString());
        assertThat(logged("no DID can be minted")).hasSize(1).first().asString().contains("member " + MEMBER);
        assertThat(logged("differs from the checked DID")).isEmpty();
    }

    @Test
    void writesNothingForTheSystemContextWhenItsDidMintingIsRejected() {
        when(dsParticipantRepository.findSystemParticipant()).thenReturn(Optional.empty());
        when(ownSecurityServerResolver.address()).thenReturn(new OwnAddress.Registered(SERVER_ID, "b\u00fccher.example"));

        assertThat(service.bindSystemIfAbsent(checkedSystemDid())).isFalse();

        verify(dsParticipantRepository, never()).bindSystemParticipant(anyString(), anyString());
        assertThat(logged("no DID can be minted")).hasSize(1);
        assertThat(logged("differs from the checked DID")).isEmpty();
    }

    @Test
    void bindsNoMemberWhoseFreshlyMintedDidDiffersFromTheCheckedOne() {
        when(dsParticipantRepository.findByMemberIdentifier(any())).thenReturn(Optional.empty());
        Map<ClientId, Did> checked = new LinkedHashMap<>();
        checked.put(MEMBER, memberDidAtOldAddress(MEMBER));
        checked.putAll(checkedDids(OTHER_MEMBER));

        assertThat(service.bindMembersIfAbsent(checked)).containsExactly(OTHER_MEMBER);

        verify(dsParticipantRepository, never()).bindMemberParticipant(eq(MEMBER), anyString(), anyString());
        verify(dsParticipantRepository).bindMemberParticipant(eq(OTHER_MEMBER), anyString(), anyString());
        assertThat(logged("differs from the checked DID")).hasSize(1).first().asString().contains("member " + MEMBER);
    }

    @Test
    void bindsNoSystemIdentifierWhoseFreshlyMintedDidDiffersFromTheCheckedOne() {
        when(dsParticipantRepository.findSystemParticipant()).thenReturn(Optional.empty());

        assertThat(service.bindSystemIfAbsent(SYSTEM_DID_AT_OLD_ADDRESS)).isFalse();

        verify(dsParticipantRepository, never()).bindSystemParticipant(anyString(), anyString());
        assertThat(logged("differs from the checked DID")).hasSize(1).first().asString().contains("SYSTEM context");
    }

    private List<String> logged(String fragment) {
        return appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.contains(fragment))
                .toList();
    }

    private static Did memberDidAtOldAddress(ClientId member) {
        return ParticipantIdentifierScheme.memberDid(member, OLD_SS_HOST);
    }

    private Map<ClientId, Did> checkedDids(ClientId... members) {
        Map<ClientId, Did> checked = new LinkedHashMap<>();
        for (var member : members) {
            checked.put(member, didAuthority.mintMemberDid(member).did().orElseGet(() -> memberDidAtOldAddress(member)));
        }
        return checked;
    }

    private Did checkedSystemDid() {
        return didAuthority.mintSystemDid().did().orElse(SYSTEM_DID_AT_OLD_ADDRESS);
    }

    private static DsParticipantEntity rowIn(ParticipantState state) {
        var row = mock(DsParticipantEntity.class);
        when(row.getState()).thenReturn(state);
        return row;
    }
}
