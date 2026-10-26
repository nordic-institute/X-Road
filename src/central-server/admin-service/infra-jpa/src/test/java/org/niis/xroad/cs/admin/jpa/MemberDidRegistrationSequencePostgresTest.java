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
package org.niis.xroad.cs.admin.jpa;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.niis.xroad.common.identifiers.jpa.entity.SecurityServerIdEntity;
import org.niis.xroad.cs.admin.api.domain.DidResolution;
import org.niis.xroad.cs.admin.api.domain.MemberDidRegistrationRequest;
import org.niis.xroad.cs.admin.api.domain.MemberDidRegistrationRequest.Entry;
import org.niis.xroad.cs.admin.api.domain.MemberDidRegistrationRequest.Status;
import org.niis.xroad.cs.admin.api.domain.Origin;
import org.niis.xroad.cs.admin.api.domain.SystemDidRegistrationRequest;
import org.niis.xroad.cs.admin.api.service.ManagementRequestService;
import org.niis.xroad.cs.admin.api.service.ParticipantValueStore;
import org.niis.xroad.cs.admin.core.config.ManagementServiceConfigProperties;
import org.niis.xroad.cs.admin.core.entity.MemberDidRegistrationRequestEntity;
import org.niis.xroad.cs.admin.core.entity.RequestEntity;
import org.niis.xroad.cs.admin.core.entity.SystemDidRegistrationRequestEntity;
import org.niis.xroad.cs.admin.core.repository.IdentifierRepository;
import org.niis.xroad.cs.admin.core.repository.RequestRepository;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;
import org.niis.xroad.cs.admin.core.service.managementrequest.AuthCertServerCheck;
import org.niis.xroad.cs.admin.core.service.managementrequest.AuthCertServerCheck.CheckedServer;
import org.niis.xroad.cs.admin.core.service.managementrequest.MemberDidRegistrationRequestHandler;
import org.niis.xroad.cs.admin.core.service.managementrequest.MemberDidRegistrationServiceImpl;
import org.niis.xroad.cs.admin.core.service.managementrequest.SystemDidRegistrationRequestHandler;
import org.niis.xroad.cs.admin.jpa.CentralServerSeed.ClientRef;
import org.niis.xroad.cs.admin.jpa.CentralServerSeed.Server;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.niis.xroad.cs.admin.api.domain.Origin.SECURITY_SERVER;
import static org.niis.xroad.cs.admin.jpa.CentralServerSeed.dspBaseUrl;
import static org.niis.xroad.cs.admin.jpa.CentralServerSeed.memberDid;
import static org.niis.xroad.cs.admin.jpa.CentralServerSeed.newHost;
import static org.niis.xroad.cs.admin.jpa.CentralServerSeed.systemDid;

/**
 * The pre-transaction service, the request handler and the published value store run together on Postgres,
 * so a step changes what the next step sees and overlapping requests meet the real row locks.
 */
class MemberDidRegistrationSequencePostgresTest extends AbstractCentralServerPostgresTest {

    private static final String OWNER_CHANGED = "OWNER_CHANGED";
    private static final String VALUE_DIFFERS = "VALUE_DIFFERS";
    private static final String SYSTEM_VALUES_NOT_STORED = "SYSTEM_VALUES_NOT_STORED";

    @Autowired
    private ParticipantValueStore store;
    @Autowired
    private SecurityServerRepository servers;
    @Autowired
    private IdentifierRepository<SecurityServerIdEntity> serverIds;
    @Autowired
    private RequestRepository<MemberDidRegistrationRequestEntity> memberRequests;
    @Autowired
    private RequestRepository<SystemDidRegistrationRequestEntity> systemRequests;

    private final List<String> resolvedDids = new CopyOnWriteArrayList<>();
    private volatile Runnable beforeLockedWrite = () -> { };

    private AuthCertServerCheck authCertServerCheck;
    private MemberDidRegistrationRequestHandler memberHandler;
    private SystemDidRegistrationRequestHandler systemHandler;
    private MemberDidRegistrationServiceImpl service;

    @BeforeEach
    void buildServiceAndHandlers() {
        when(requestMapper.toDto(any(MemberDidRegistrationRequestEntity.class))).thenAnswer(inv -> {
            var dto = new MemberDidRegistrationRequest();
            dto.setId(inv.<RequestEntity>getArgument(0).getId());
            return dto;
        });
        when(requestMapper.toDto(any(SystemDidRegistrationRequestEntity.class))).thenAnswer(inv -> {
            var dto = new SystemDidRegistrationRequest();
            dto.setId(inv.<RequestEntity>getArgument(0).getId());
            return dto;
        });
        authCertServerCheck = mock(AuthCertServerCheck.class);
        var auditData = mock(AuditDataHelper.class);
        memberHandler = new MemberDidRegistrationRequestHandler(serverIds, memberRequests, servers, store, requestMapper, auditData);
        systemHandler = new SystemDidRegistrationRequestHandler(serverIds, systemRequests, servers, authCertServerCheck, store,
                requestMapper);

        var managementRequestService = mock(ManagementRequestService.class);
        when(managementRequestService.add(any(MemberDidRegistrationRequest.class))).thenAnswer(inv -> {
            beforeLockedWrite.run();
            return transactions.execute(status -> memberHandler.add(inv.getArgument(0)));
        });
        var configProperties = mock(ManagementServiceConfigProperties.class);
        when(configProperties.getMemberDidRegMaxEntries()).thenReturn(100);
        service = new MemberDidRegistrationServiceImpl(dids -> {
            resolvedDids.addAll(dids);
            return dids.stream().map(DidResolution::resolved).toList();
        }, store, servers, managementRequestService, configProperties);
    }

    @RepeatedTest(2)
    void ownerChangeCommittedBetweenThePreCheckAndTheLockedWriteAnswersEveryEntryOwnerChangedAndWritesNothing() {
        var owner = seed.member();
        var server = seed.server(owner);
        var clients = servedClients(server, 2);
        storeSystemPair(server);
        var newOwner = seed.member();
        beforeLockedWrite = () -> changeOwner(server, newOwner);

        var answer = service.add(request(server, withOwner(owner, clients)));

        assertEveryEntry(answer, Status.FIXABLE_CONFLICT, OWNER_CHANGED);
        assertThat(answer.getId()).isZero();
        assertThat(countMemberDids(server.id())).isZero();
        assertThat(memberRequestRows(server)).isZero();
        assertThat(resolvedDids).hasSize(3);
    }

    @RepeatedTest(2)
    void ownerChangeCommittedWhileTheHandlerWaitsForTheRowLockAnswersEveryEntryOwnerChangedAndWritesNothing() throws Exception {
        var owner = seed.member();
        var server = seed.server(owner);
        var clients = servedClients(server, 2);
        var newOwner = seed.member();
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        MemberDidRegistrationRequest answer;
        try {
            var holder = executor.submit(() -> transactions.execute(status -> {
                changeOwner(server, newOwner);
                locked.countDown();
                await(release);
                return null;
            }));
            awaitHolding(locked, holder);
            var racingPid = new AtomicInteger();
            var racingRequest = request(server, withOwner(owner, clients));
            var racing = executor.submit(inTransaction(racingPid, () -> memberHandler.add(racingRequest)));
            awaitLockedSession(racingPid);
            assertThat(racing.isDone()).isFalse();

            release.countDown();

            holder.get(WAIT_SECONDS, TimeUnit.SECONDS);
            answer = racing.get(WAIT_SECONDS, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
            executor.close();
        }

        assertEveryEntry(answer, Status.FIXABLE_CONFLICT, OWNER_CHANGED);
        assertThat(answer.getId()).isZero();
        assertThat(countMemberDids(server.id())).isZero();
        assertThat(memberRequestRows(server)).isZero();
    }

    @RepeatedTest(2)
    void twoConcurrentEqualRequestsStoreOneRowPerMemberAndBothAnswerStored() throws Exception {
        var owner = seed.member();
        var server = seed.server(owner);
        var clients = servedClients(server, 2);
        storeSystemPair(server);
        var start = new CountDownLatch(1);
        Callable<MemberDidRegistrationRequest> equalRequest = () -> {
            await(start);
            return service.add(request(server, withOwner(owner, clients)));
        };
        var executor = Executors.newFixedThreadPool(2);
        MemberDidRegistrationRequest first;
        MemberDidRegistrationRequest second;
        try {
            var firstCall = executor.submit(equalRequest);
            var secondCall = executor.submit(equalRequest);

            start.countDown();

            first = firstCall.get(WAIT_SECONDS, TimeUnit.SECONDS);
            second = secondCall.get(WAIT_SECONDS, TimeUnit.SECONDS);
        } finally {
            start.countDown();
            executor.shutdownNow();
            executor.close();
        }

        assertEveryEntry(first, Status.STORED, null);
        assertEveryEntry(second, Status.STORED, null);
        assertThat(countMemberDids(server.id())).isEqualTo(3);
        assertThat(memberRequestRows(server)).isEqualTo(1);
        assertThat(List.of(first.getId(), second.getId())).containsOnlyOnce(0);
    }

    @RepeatedTest(2)
    void addressChangeThenDidsForTheNewHostReplaceTheStaleDidsAndAnswerStored() {
        var owner = seed.member();
        var server = seed.server(owner);
        var clients = servedClients(server, 2);
        storeSystemPair(server);
        var oldDids = List.of(storedDid(server, owner), storedDid(server, clients.getFirst()), storedDid(server, clients.getLast()));
        assertEveryEntry(service.add(request(server, withOwner(owner, clients))), Status.STORED, null);
        var newHost = newHost();
        changeAddress(server, newHost);
        resolvedDids.clear();
        var newEntries = entriesAt(newHost, withOwner(owner, clients));

        var answer = service.add(new MemberDidRegistrationRequest(SECURITY_SERVER, server.identifier(), newEntries));

        assertEveryEntry(answer, Status.STORED, null);
        assertThat(resolvedDids).containsExactlyInAnyOrderElementsOf(newEntries.stream().map(Entry::did).toList());
        assertThat(countMemberDids(server.id())).isEqualTo(3);
        assertThat(storedDids(server)).containsExactlyInAnyOrderElementsOf(newEntries.stream().map(Entry::did).toList());
        assertThat(storedDids(server)).doesNotContainAnyElementsOf(oldDids);
        assertThat(answer.getId()).isPositive();
        assertThat(memberRequestRows(server)).isEqualTo(2);
    }

    @RepeatedTest(2)
    void equalResendAnswersStoredWithoutResolutionThenADifferentDidIsFixableAndKeepsTheStoredValue() {
        var owner = seed.member();
        var server = seed.server(owner);
        var member = servedClients(server, 1).getFirst();
        storeSystemPair(server);
        var did = memberDid(server.address(), member.identifier());
        var first = service.add(request(server, List.of(member)));
        assertEveryEntry(first, Status.STORED, null);
        assertThat(resolvedDids).hasSize(1);
        assertThat(memberRequestRows(server)).isEqualTo(1);

        var resend = service.add(request(server, List.of(member)));

        assertEveryEntry(resend, Status.STORED, null);
        assertThat(resend.getId()).isZero();
        assertThat(resolvedDids).hasSize(1);
        assertThat(memberRequestRows(server)).isEqualTo(1);

        var differing = new Entry(member.identifier(), did.replace("%3A7183", "%3A7184"));
        var refused = service.add(new MemberDidRegistrationRequest(SECURITY_SERVER, server.identifier(), List.of(differing)));

        assertEveryEntry(refused, Status.FIXABLE_CONFLICT, VALUE_DIFFERS);
        assertThat(refused.getId()).isZero();
        assertThat(resolvedDids).hasSize(1);
        assertThat(storedDids(server)).containsExactly(did);
        assertThat(memberRequestRows(server)).isEqualTo(1);
    }

    @RepeatedTest(2)
    void systemDidRegistrationThenMemberDidRegistrationPassesTheGateAndStores() {
        var owner = seed.member();
        var server = seed.server(owner);
        var clients = servedClients(server, 2);
        stubAuthCertCheck(server);

        registerSystemPair(server);
        var answer = service.add(request(server, withOwner(owner, clients)));

        assertEveryEntry(answer, Status.STORED, null);
        assertThat(countMemberDids(server.id())).isEqualTo(3);
        assertThat(memberRequestRows(server)).isEqualTo(1);
    }

    @RepeatedTest(2)
    void memberDidRegistrationBeforeSystemDidRegistrationAnswersEveryEntryFixableAndStoresNothing() {
        var owner = seed.member();
        var server = seed.server(owner);
        var clients = servedClients(server, 2);
        stubAuthCertCheck(server);

        var answer = service.add(request(server, withOwner(owner, clients)));

        assertEveryEntry(answer, Status.FIXABLE_CONFLICT, SYSTEM_VALUES_NOT_STORED);
        assertThat(answer.getId()).isZero();
        assertThat(resolvedDids).isEmpty();
        assertThat(countMemberDids(server.id())).isZero();
        assertThat(memberRequestRows(server)).isZero();

        registerSystemPair(server);
        assertEveryEntry(service.add(request(server, withOwner(owner, clients))), Status.STORED, null);
        assertThat(countMemberDids(server.id())).isEqualTo(3);
    }

    @RepeatedTest(2)
    void twoServersRacingOnOneDidFailTheLosingAddAsAWholeAndWriteNothing() throws Exception {
        var sharedHost = newHost();
        var firstOwner = seed.member();
        var secondOwner = seed.member();
        var serverA = seed.server(firstOwner, sharedHost);
        var serverB = seed.server(secondOwner, sharedHost);
        seed.addClient(serverB, firstOwner);
        var didOfFirst = memberDid(sharedHost, firstOwner.identifier());
        var racingRequest = new MemberDidRegistrationRequest(SECURITY_SERVER, serverB.identifier(), List.of(
                new Entry(secondOwner.identifier(), memberDid(sharedHost, secondOwner.identifier())),
                new Entry(firstOwner.identifier(), didOfFirst)));
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var holder = executor.submit(() -> transactions.execute(status -> {
                store.writeMemberDidsOnce(serverA.id(), Map.of(firstOwner.identifier(), didOfFirst));
                locked.countDown();
                await(release);
                return null;
            }));
            awaitHolding(locked, holder);
            var racingPid = new AtomicInteger();
            var racing = executor.submit(inTransaction(racingPid, () -> memberHandler.add(racingRequest)));
            awaitLockedSession(racingPid);
            assertThat(racing.isDone()).isFalse();

            release.countDown();

            holder.get(WAIT_SECONDS, TimeUnit.SECONDS);

            assertThatThrownBy(() -> racing.get(WAIT_SECONDS, TimeUnit.SECONDS))
                    .isInstanceOf(ExecutionException.class)
                    .hasCauseInstanceOf(DataIntegrityViolationException.class)
                    .rootCause().hasMessageContaining("member_dids_did_excl");
        } finally {
            release.countDown();
            executor.shutdownNow();
            executor.close();
        }

        assertThat(countMemberDids(serverA.id())).isEqualTo(1);
        assertThat(countMemberDids(serverB.id())).isZero();
        assertThat(memberRequestRows(serverB)).isZero();
    }

    private List<ClientRef> servedClients(Server server, int count) {
        var clients = new ArrayList<ClientRef>();
        for (int i = 0; i < count; i++) {
            var client = seed.member();
            seed.addClient(server, client);
            clients.add(client);
        }
        return clients;
    }

    private static List<ClientRef> withOwner(ClientRef owner, List<ClientRef> clients) {
        var members = new ArrayList<ClientRef>();
        members.add(owner);
        members.addAll(clients);
        return members;
    }

    private static MemberDidRegistrationRequest request(Server server, List<ClientRef> members) {
        return new MemberDidRegistrationRequest(SECURITY_SERVER, server.identifier(), entriesAt(server.address(), members));
    }

    private static List<Entry> entriesAt(String host, List<ClientRef> members) {
        return members.stream().map(member -> new Entry(member.identifier(), memberDid(host, member.identifier()))).toList();
    }

    private static String storedDid(Server server, ClientRef member) {
        return memberDid(server.address(), member.identifier());
    }

    private void storeSystemPair(Server server) {
        jdbc.update("update security_servers set system_did = ?, dsp_base_url = ? where id = ?",
                systemDid(server.address(), 7183), dspBaseUrl(server.address()), server.id());
    }

    private void stubAuthCertCheck(Server server) {
        when(authCertServerCheck.requireServer(any(), any())).thenReturn(new CheckedServer(server.id(), server.address()));
    }

    private void registerSystemPair(Server server) {
        transactions.executeWithoutResult(status -> systemHandler.add(new SystemDidRegistrationRequest(
                Origin.SECURITY_SERVER, server.identifier(), systemDid(server.address(), 7183), dspBaseUrl(server.address()),
                new byte[] {1})));
    }

    private void changeOwner(Server server, ClientRef newOwner) {
        jdbc.update("update security_servers set owner_id = ? where id = ?", newOwner.id(), server.id());
    }

    private void changeAddress(Server server, String address) {
        jdbc.update("update security_servers set address = ? where id = ?", address, server.id());
    }

    private int memberRequestRows(Server server) {
        return jdbc.queryForObject("select count(*) from requests r join identifier i on i.id = r.security_server_id "
                        + "where r.type = ? and i.server_code = ?",
                Integer.class, MemberDidRegistrationRequestEntity.DISCRIMINATOR_VALUE, server.identifier().getServerCode());
    }

    private List<String> storedDids(Server server) {
        return jdbc.queryForList("select did from member_dids where security_server_id = ?", String.class, server.id());
    }

    private static void assertEveryEntry(MemberDidRegistrationRequest answer, Status status, String reason) {
        assertThat(answer.getResults()).hasSameSizeAs(answer.getEntries()).isNotEmpty()
                .allSatisfy(result -> {
                    assertThat(result.status()).isEqualTo(status);
                    assertThat(result.reason()).isEqualTo(reason);
                });
    }
}
