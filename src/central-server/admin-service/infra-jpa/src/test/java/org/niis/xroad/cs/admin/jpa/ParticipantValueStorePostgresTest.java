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

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;

import org.junit.jupiter.api.Test;
import org.niis.xroad.cs.admin.api.domain.ParticipantValueResult;
import org.niis.xroad.cs.admin.api.service.ParticipantValueStore;
import org.niis.xroad.cs.admin.core.entity.MemberDidRow;
import org.niis.xroad.cs.admin.core.entity.SecurityServerEntity;
import org.niis.xroad.cs.admin.core.repository.MemberDidRepository;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;
import org.niis.xroad.cs.admin.jpa.CentralServerSeed.ClientRef;
import org.niis.xroad.cs.admin.jpa.CentralServerSeed.Server;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.UncategorizedSQLException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.DID_HELD_BY_ANOTHER_SERVER;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.DID_OF_OTHER_MEMBER;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.DID_TOO_LONG;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.HOST_DIFFERS;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.MEMBER_NOT_SERVED;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.NOT_CANONICAL;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.VALUE_DIFFERS;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.WRONG_SCHEME;
import static org.niis.xroad.cs.admin.core.dataspace.ParticipantValueValidator.MAX_DID_LENGTH;
import static org.niis.xroad.cs.admin.jpa.CentralServerSeed.dspBaseUrl;
import static org.niis.xroad.cs.admin.jpa.CentralServerSeed.memberDid;
import static org.niis.xroad.cs.admin.jpa.CentralServerSeed.newHost;
import static org.niis.xroad.cs.admin.jpa.CentralServerSeed.systemDid;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.REJECT_CONNECTOR_PUBLISHED_VALUE;

/**
 * The published value store, the server-row lock and the member DID read model against Postgres.
 */
class ParticipantValueStorePostgresTest extends AbstractCentralServerPostgresTest {

    private static final String LOCK_NOT_AVAILABLE = "55P03";
    private static final int SYSTEM_DRY_RUN_STATEMENT_BOUND = 2;
    private static final int SYSTEM_WRITE_STATEMENT_BOUND = 5;

    @Autowired
    private ParticipantValueStore store;
    @Autowired
    private SecurityServerRepository servers;
    @Autowired
    private MemberDidRepository memberDids;

    @Test
    void findAllRowsReturnsOneRowPerStoredMemberDidInOneStatement() {
        var firstOwner = seed.member();
        var secondOwner = seed.member();
        var client = seed.member();
        var first = seed.server(firstOwner);
        var second = seed.server(secondOwner);
        seed.addClient(first, client);
        seed.addClient(second, client);
        insertMemberDid(first.id(), firstOwner.id(), memberDid(first.address(), firstOwner.identifier()));
        insertMemberDid(first.id(), client.id(), memberDid(first.address(), client.identifier()));
        insertMemberDid(second.id(), client.id(), memberDid(second.address(), client.identifier()));

        StatementRecorder.start();
        var rows = memberDids.findAllRows();
        var statements = StatementRecorder.stop();

        assertThat(statements).hasSize(1);
        assertThat(rows.stream().filter(row -> row.securityServerId() == first.id() || row.securityServerId() == second.id()))
                .containsExactlyInAnyOrder(
                        row(first, firstOwner.identifier()),
                        row(first, client.identifier()),
                        row(second, client.identifier()));
    }

    @Test
    void lockForUpdateByRunsAndWaitsForTheHolderOfTheServerRow() throws Exception {
        var server = seed.server(seed.member());
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var holder = executor.submit(() -> transactions.execute(status -> {
                servers.lockForUpdateById(server.id());
                locked.countDown();
                await(release);
                return null;
            }));
            awaitHolding(locked, holder);
            var waiterPid = new AtomicInteger();
            Future<Optional<Integer>> waiter = executor.submit(
                    inTransaction(waiterPid, () -> servers.lockForUpdateBy(server.identifier())));
            awaitLockedSession(waiterPid);
            assertThat(waiter.isDone()).isFalse();

            release.countDown();

            holder.get(WAIT_SECONDS, TimeUnit.SECONDS);
            assertThat(waiter.get(WAIT_SECONDS, TimeUnit.SECONDS)).contains(server.id());
        } finally {
            release.countDown();
            executor.shutdownNow();
            executor.close();
        }
    }

    @Test
    void lockForUpdateByFindsNothingForAnUnknownServer() {
        var unknown = SecurityServerId.Conf.create(CentralServerSeed.INSTANCE, CentralServerSeed.MEMBER_CLASS, "nobody", "none");

        var locked = transactions.execute(status -> servers.lockForUpdateBy(unknown));

        assertThat(locked).isEmpty();
    }

    @Test
    void lockForUpdateByLocksTheServerRowOfTheOwnerAndServerCode() {
        var server = seed.server(seed.member());

        var locked = transactions.execute(status -> servers.lockForUpdateBy(server.identifier()));

        assertThat(locked).contains(server.id());
    }

    @Test
    void writerWaitsForLockHolderAndThenSeesTheValueTheHolderCommitted() throws Exception {
        var server = seed.server(seed.member());
        var first = systemDid(server.address(), 7183);
        var second = systemDid(server.address(), 7184);
        var url = dspBaseUrl(server.address());
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var holder = executor.submit(() -> transactions.execute(status -> {
                var result = store.writeSystemPairOnce(server.id(), first, url);
                locked.countDown();
                await(release);
                return result;
            }));
            awaitHolding(locked, holder);
            var waiterPid = new AtomicInteger();
            Future<ParticipantValueResult> waiter = executor.submit(inTransaction(waiterPid, () -> {
                entityManager.find(SecurityServerEntity.class, server.id());
                return store.writeSystemPairOnce(server.id(), second, url);
            }));
            awaitLockedSession(waiterPid);
            assertThat(waiter.isDone()).isFalse();

            release.countDown();

            assertThat(holder.get(WAIT_SECONDS, TimeUnit.SECONDS)).isEqualTo(ParticipantValueResult.STORED);
            assertThat(waiter.get(WAIT_SECONDS, TimeUnit.SECONDS)).isEqualTo(ParticipantValueResult.rejected(VALUE_DIFFERS));
            assertThat(jdbc.queryForObject("select system_did from security_servers where id = ?", String.class, server.id()))
                    .isEqualTo(first);
        } finally {
            release.countDown();
            executor.shutdownNow();
            executor.close();
        }
    }

    @Test
    void batchHittingTheExclusionConstraintRollsBackWholeAndTheRepeatIsRejectedPerEntry() throws Exception {
        var sharedHost = newHost();
        var firstOwner = seed.member();
        var secondOwner = seed.member();
        var serverA = seed.server(firstOwner, sharedHost);
        var serverB = seed.server(secondOwner, sharedHost);
        seed.addClient(serverB, firstOwner);
        var didOfFirst = memberDid(sharedHost, firstOwner.identifier());
        var didOfSecond = memberDid(sharedHost, secondOwner.identifier());
        var batch = new LinkedHashMap<ClientId, String>();
        batch.put(secondOwner.identifier(), didOfSecond);
        batch.put(firstOwner.identifier(), didOfFirst);
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
            var racing = executor.submit(inTransaction(racingPid, () -> store.writeMemberDidsOnce(serverB.id(), batch)));
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

        var repeated = store.writeMemberDidsOnce(serverB.id(), batch);

        assertThat(repeated).containsExactly(
                Map.entry(secondOwner.identifier(), ParticipantValueResult.STORED),
                Map.entry(firstOwner.identifier(), ParticipantValueResult.rejected(DID_HELD_BY_ANOTHER_SERVER)));
        assertThat(countMemberDids(serverB.id())).isEqualTo(1);
        verify(auditLogging).auditLogFailWithData(eq(REJECT_CONNECTOR_PUBLISHED_VALUE), anyString(), anyMap());
    }

    @Test
    void memberDryRunAnswersLikeTheWriteForEachReasonAndTouchesNothing() {
        var owner = seed.member();
        var server = seed.server(owner);
        var otherServer = seed.server(seed.member());
        var equal = clientOf(server);
        var stale = clientOf(server);
        var differs = clientOf(server);
        var heldElsewhere = clientOf(server);
        var malformed = clientOf(server);
        var wrongMember = clientOf(server);
        var notCanonical = clientOf(server);
        var tooLong = clientOf(server);
        var otherHost = clientOf(server);
        var notServed = seed.member();
        var host = server.address();
        var staleStored = memberDid(newHost(), stale.identifier());
        var currentStored = memberDid(host, differs.identifier());
        insertMemberDid(server.id(), equal.id(), memberDid(host, equal.identifier()));
        insertMemberDid(server.id(), stale.id(), staleStored);
        insertMemberDid(server.id(), differs.id(), currentStored);
        insertMemberDid(otherServer.id(), heldElsewhere.id(), memberDid(host, heldElsewhere.identifier()));
        var entries = new LinkedHashMap<ClientId, String>();
        entries.put(notServed.identifier(), memberDid(host, notServed.identifier()));
        entries.put(owner.identifier(), memberDid(host, owner.identifier()));
        entries.put(equal.identifier(), memberDid(host, equal.identifier()));
        entries.put(stale.identifier(), memberDid(host, stale.identifier()));
        entries.put(differs.identifier(), currentStored.replace("%3A7183", "%3A7184"));
        entries.put(heldElsewhere.identifier(), memberDid(host, heldElsewhere.identifier()));
        entries.put(malformed.identifier(), "not-a-did");
        entries.put(wrongMember.identifier(), memberDid(host, owner.identifier()));
        entries.put(notCanonical.identifier(), memberDid(host, notCanonical.identifier()).replace(host, host.toUpperCase(Locale.ROOT)));
        entries.put(tooLong.identifier(), memberDid(host, tooLong.identifier()) + "a".repeat(MAX_DID_LENGTH));
        entries.put(otherHost.identifier(), memberDid(newHost(), otherHost.identifier()));
        var expected = new LinkedHashMap<ClientId, ParticipantValueResult>();
        expected.put(notServed.identifier(), ParticipantValueResult.rejected(MEMBER_NOT_SERVED));
        expected.put(owner.identifier(), ParticipantValueResult.STORED);
        expected.put(equal.identifier(), ParticipantValueResult.UNCHANGED);
        expected.put(stale.identifier(), ParticipantValueResult.STORED);
        expected.put(differs.identifier(), ParticipantValueResult.rejected(VALUE_DIFFERS));
        expected.put(heldElsewhere.identifier(), ParticipantValueResult.rejected(DID_HELD_BY_ANOTHER_SERVER));
        expected.put(malformed.identifier(), ParticipantValueResult.rejected(WRONG_SCHEME));
        expected.put(wrongMember.identifier(), ParticipantValueResult.rejected(DID_OF_OTHER_MEMBER));
        expected.put(notCanonical.identifier(), ParticipantValueResult.rejected(NOT_CANONICAL));
        expected.put(tooLong.identifier(), ParticipantValueResult.rejected(DID_TOO_LONG));
        expected.put(otherHost.identifier(), ParticipantValueResult.rejected(HOST_DIFFERS));
        var before = jdbc.queryForList("select * from member_dids order by id");

        StatementRecorder.start();
        var dryRun = store.checkMemberDids(server.id(), entries);
        var statements = StatementRecorder.stop();

        assertThat(dryRun).containsExactlyEntriesOf(expected);
        assertThat(jdbc.queryForList("select * from member_dids order by id")).isEqualTo(before);
        assertThat(statements).isNotEmpty().noneMatch(ParticipantValueStorePostgresTest::writesOrLocks);
        verifyNoInteractions(auditLogging);

        var written = store.writeMemberDidsOnce(server.id(), entries);

        assertThat(written).containsExactlyEntriesOf(expected);
        verify(auditLogging, times(1)).auditLogFailWithData(eq(REJECT_CONNECTOR_PUBLISHED_VALUE), anyString(), anyMap());
    }

    @Test
    void systemPairDryRunAndWriteLoadNoClientEntitiesOfAServerWithSeveralClients() {
        var server = seed.server(seed.member());
        for (int i = 0; i < 10; i++) {
            clientOf(server);
        }
        var did = systemDid(server.address(), 7183);
        var url = dspBaseUrl(server.address());

        StatementRecorder.start();
        store.checkSystemPair(server.id(), did, url);
        var dryRun = StatementRecorder.stop();
        StatementRecorder.start();
        var written = store.writeSystemPairOnce(server.id(), did, url);
        var write = StatementRecorder.stop();

        assertThat(written).isEqualTo(ParticipantValueResult.STORED);
        assertThat(dryRun).as("dry run statements").hasSizeLessThanOrEqualTo(SYSTEM_DRY_RUN_STATEMENT_BOUND);
        assertThat(write).as("write statements").hasSizeLessThanOrEqualTo(SYSTEM_WRITE_STATEMENT_BOUND);
    }

    @Test
    void disabledClientStillCountsAsServed() {
        var server = seed.server(seed.member());
        var disabled = clientOf(server);
        var disabledRows = jdbc.update(
                "update server_clients set enabled = false where security_server_id = ? and security_server_client_id = ?",
                server.id(), disabled.id());
        assertThat(disabledRows).isOne();
        var entries = Map.of(disabled.identifier(), memberDid(server.address(), disabled.identifier()));

        var dryRun = store.checkMemberDids(server.id(), entries);
        var written = store.writeMemberDidsOnce(server.id(), entries);

        assertThat(dryRun).containsExactlyEntriesOf(Map.of(disabled.identifier(), ParticipantValueResult.STORED));
        assertThat(written).isEqualTo(dryRun);
        assertThat(countMemberDids(server.id())).isOne();
    }

    @Test
    void memberServedOnlyThroughASubsystemClientIsAccepted() {
        var server = seed.server(seed.member());
        var member = seed.member();
        seed.addClient(server, seed.subsystem(member));
        var entries = Map.of(member.identifier(), memberDid(server.address(), member.identifier()));

        var dryRun = store.checkMemberDids(server.id(), entries);
        var written = store.writeMemberDidsOnce(server.id(), entries);

        assertThat(dryRun).containsExactlyEntriesOf(Map.of(member.identifier(), ParticipantValueResult.STORED));
        assertThat(written).isEqualTo(dryRun);
        assertThat(countMemberDids(server.id())).isOne();
    }

    @Test
    void memberDryRunTakesNoRowLockWhileTheWriteDoes() throws Exception {
        var owner = seed.member();
        var server = seed.server(owner);
        var entries = Map.of(owner.identifier(), memberDid(server.address(), owner.identifier()));
        var executor = Executors.newSingleThreadExecutor();
        try {
            var probes = transactions.execute(status -> {
                store.checkMemberDids(server.id(), entries);
                var afterDryRun = probe(executor, server.id());
                store.writeMemberDidsOnce(server.id(), entries);
                return List.of(afterDryRun, probe(executor, server.id()));
            });

            assertThat(probes).containsExactly(true, false);
        } finally {
            executor.shutdownNow();
            executor.close();
        }
    }

    private boolean probe(ExecutorService executor, int serverId) {
        try {
            return executor.submit(() -> canLockServerRow(serverId)).get(WAIT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException(e);
        }
    }

    private boolean canLockServerRow(int serverId) {
        try {
            return !transactions.execute(status ->
                    jdbc.queryForList("select id from security_servers where id = ? for update nowait", Integer.class, serverId))
                    .isEmpty();
        } catch (UncategorizedSQLException e) {
            if (LOCK_NOT_AVAILABLE.equals(e.getSQLException().getSQLState())) {
                return false;
            }
            throw e;
        }
    }

    private static boolean writesOrLocks(String sql) {
        var statement = sql.strip().toLowerCase(Locale.ROOT);
        return statement.startsWith("insert") || statement.startsWith("update") || statement.startsWith("delete")
                || statement.contains(" for update") || statement.contains(" for no key update")
                || statement.contains(" for share") || statement.contains(" for key share");
    }

    private ClientRef clientOf(Server server) {
        var client = seed.member();
        seed.addClient(server, client);
        return client;
    }

    private static MemberDidRow row(Server server, ClientId member) {
        return new MemberDidRow(server.id(), member.getXRoadInstance(), member.getMemberClass(), member.getMemberCode(),
                memberDid(server.address(), member));
    }
}
