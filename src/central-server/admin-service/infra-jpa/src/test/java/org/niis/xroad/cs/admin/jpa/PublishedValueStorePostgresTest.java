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
import org.niis.xroad.cs.admin.api.domain.PublishedValueResult;
import org.niis.xroad.cs.admin.api.service.PublishedValueStore;
import org.niis.xroad.cs.admin.core.entity.MemberDidRow;
import org.niis.xroad.cs.admin.core.entity.SecurityServerEntity;
import org.niis.xroad.cs.admin.core.repository.MemberDidRepository;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;
import org.niis.xroad.cs.admin.jpa.CentralServerSeed.Server;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.niis.xroad.cs.admin.api.domain.PublishedValueRejection.DID_HELD_BY_ANOTHER_SERVER;
import static org.niis.xroad.cs.admin.api.domain.PublishedValueRejection.VALUE_DIFFERS;
import static org.niis.xroad.cs.admin.jpa.CentralServerSeed.dspBaseUrl;
import static org.niis.xroad.cs.admin.jpa.CentralServerSeed.memberDid;
import static org.niis.xroad.cs.admin.jpa.CentralServerSeed.newHost;
import static org.niis.xroad.cs.admin.jpa.CentralServerSeed.systemDid;
import static org.niis.xroad.restapi.config.audit.RestApiAuditEvent.REJECT_CONNECTOR_PUBLISHED_VALUE;

/**
 * The published value store, the server-row lock and the member DID read model against Postgres.
 */
class PublishedValueStorePostgresTest extends AbstractCentralServerPostgresTest {

    @Autowired
    private PublishedValueStore store;
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
            Future<PublishedValueResult> waiter = executor.submit(inTransaction(waiterPid, () -> {
                entityManager.find(SecurityServerEntity.class, server.id());
                return store.writeSystemPairOnce(server.id(), second, url);
            }));
            awaitLockedSession(waiterPid);
            assertThat(waiter.isDone()).isFalse();

            release.countDown();

            assertThat(holder.get(WAIT_SECONDS, TimeUnit.SECONDS)).isEqualTo(PublishedValueResult.STORED);
            assertThat(waiter.get(WAIT_SECONDS, TimeUnit.SECONDS)).isEqualTo(PublishedValueResult.rejected(VALUE_DIFFERS));
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
                Map.entry(secondOwner.identifier(), PublishedValueResult.STORED),
                Map.entry(firstOwner.identifier(), PublishedValueResult.rejected(DID_HELD_BY_ANOTHER_SERVER)));
        assertThat(countMemberDids(serverB.id())).isEqualTo(1);
        verify(auditLogging).auditLogFailWithData(eq(REJECT_CONNECTOR_PUBLISHED_VALUE), anyString(), anyMap());
    }

    private static MemberDidRow row(Server server, ClientId member) {
        return new MemberDidRow(server.id(), member.getXRoadInstance(), member.getMemberClass(), member.getMemberCode(),
                memberDid(server.address(), member));
    }
}
