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

import org.junit.jupiter.api.Test;
import org.niis.xroad.cs.admin.api.domain.ClientDeletionRequest;
import org.niis.xroad.cs.admin.api.domain.Origin;
import org.niis.xroad.cs.admin.api.domain.ParticipantValueResult;
import org.niis.xroad.cs.admin.api.service.ParticipantValueStore;
import org.niis.xroad.cs.admin.core.service.managementrequest.ClientDeletionRequestHandler;
import org.niis.xroad.cs.admin.jpa.CentralServerSeed.ClientRef;
import org.niis.xroad.cs.admin.jpa.CentralServerSeed.Server;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.cs.admin.api.domain.ParticipantValueRejection.UNRELATED_MEMBER;
import static org.niis.xroad.cs.admin.jpa.CentralServerSeed.memberDid;

/**
 * Removing the last client of a member from a server must leave no DID row of that member on the server,
 * however the removal interleaves with another removal or with a DID write.
 */
class ClientRemovalRacePostgresTest extends AbstractCentralServerPostgresTest {

    private static final int ROUNDS = 25;

    @Autowired
    private ClientDeletionRequestHandler handler;
    @Autowired
    private ParticipantValueStore store;

    private record Fixture(Server server, ClientRef member, ClientRef firstClient, ClientRef secondClient) {
    }

    @Test
    void concurrentRemovalsOfTheLastTwoClientsLeaveNoDidRowForTheMember() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                var fixture = fixtureWithTwoSubsystemClients();
                var barrier = new CyclicBarrier(2);
                var first = executor.submit(removal(barrier, fixture.server(), fixture.firstClient()));
                var second = executor.submit(removal(barrier, fixture.server(), fixture.secondClient()));
                first.get(WAIT_SECONDS, TimeUnit.SECONDS);
                second.get(WAIT_SECONDS, TimeUnit.SECONDS);

                assertThat(countServerClients(fixture.server(), fixture.member())).as("round %d clients", round).isZero();
                assertThat(countMemberDids(fixture.server().id())).as("round %d member DID rows", round).isZero();
            }
        } finally {
            executor.shutdownNow();
            executor.close();
        }
    }

    @Test
    void removalOfTheLastClientRacingADidWriteLeavesNoDidRowForTheMember() throws Exception {
        var executor = Executors.newFixedThreadPool(2);
        try {
            for (int round = 0; round < ROUNDS; round++) {
                var fixture = fixtureWithOneClientAndNoDidRow();
                var barrier = new CyclicBarrier(2);
                var removal = executor.submit(removal(barrier, fixture.server(), fixture.firstClient()));
                var write = executor.submit(() -> {
                    barrier.await(WAIT_SECONDS, TimeUnit.SECONDS);
                    return store.writeMemberDidsOnce(fixture.server().id(),
                            Map.of(fixture.member().identifier(), memberDid(fixture.server().address(), fixture.member().identifier())));
                });
                removal.get(WAIT_SECONDS, TimeUnit.SECONDS);
                assertThat(write.get(WAIT_SECONDS, TimeUnit.SECONDS).values()).as("round %d write", round)
                        .singleElement()
                        .isIn(ParticipantValueResult.STORED, ParticipantValueResult.rejected(UNRELATED_MEMBER));

                assertThat(countMemberDids(fixture.server().id())).as("round %d member DID rows", round).isZero();
            }
        } finally {
            executor.shutdownNow();
            executor.close();
        }
    }

    @Test
    void didWriteBlocksBehindAnUncommittedRemovalAndIsThenRefused() throws Exception {
        var fixture = fixtureWithOneClientAndNoDidRow();
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var holder = executor.submit(() -> transactions.execute(status -> {
                handler.add(deletion(fixture.server(), fixture.firstClient()));
                locked.countDown();
                await(release);
                return null;
            }));
            awaitHolding(locked, holder);
            var writePid = new AtomicInteger();
            Future<Map<ClientId, ParticipantValueResult>> write = executor.submit(inTransaction(writePid,
                    () -> store.writeMemberDidsOnce(fixture.server().id(), Map.of(fixture.member().identifier(),
                            memberDid(fixture.server().address(), fixture.member().identifier())))));
            awaitLockedSession(writePid);
            assertThat(write.isDone()).isFalse();

            release.countDown();

            holder.get(WAIT_SECONDS, TimeUnit.SECONDS);
            assertThat(write.get(WAIT_SECONDS, TimeUnit.SECONDS).values())
                    .containsExactly(ParticipantValueResult.rejected(UNRELATED_MEMBER));
            assertThat(countMemberDids(fixture.server().id())).isZero();
        } finally {
            release.countDown();
            executor.shutdownNow();
            executor.close();
        }
    }

    @Test
    void removalBlocksBehindAnUncommittedDidWriteAndThenRemovesTheRow() throws Exception {
        var fixture = fixtureWithOneClientAndNoDidRow();
        var locked = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            Future<Map<ClientId, ParticipantValueResult>> holder = executor.submit(() -> transactions.execute(status -> {
                var result = store.writeMemberDidsOnce(fixture.server().id(),
                        Map.of(fixture.member().identifier(), memberDid(fixture.server().address(), fixture.member().identifier())));
                locked.countDown();
                await(release);
                return result;
            }));
            awaitHolding(locked, holder);
            var removalPid = new AtomicInteger();
            Future<?> removal = executor.submit(inTransaction(removalPid,
                    () -> handler.add(deletion(fixture.server(), fixture.firstClient()))));
            awaitLockedSession(removalPid);
            assertThat(removal.isDone()).isFalse();

            release.countDown();

            assertThat(holder.get(WAIT_SECONDS, TimeUnit.SECONDS))
                    .containsEntry(fixture.member().identifier(), ParticipantValueResult.STORED);
            removal.get(WAIT_SECONDS, TimeUnit.SECONDS);
            assertThat(countMemberDids(fixture.server().id())).isZero();
        } finally {
            release.countDown();
            executor.shutdownNow();
            executor.close();
        }
    }

    private Callable<Object> removal(CyclicBarrier barrier, Server server, ClientRef client) {
        return () -> {
            barrier.await(WAIT_SECONDS, TimeUnit.SECONDS);
            return handler.add(deletion(server, client));
        };
    }

    private static ClientDeletionRequest deletion(Server server, ClientRef client) {
        return new ClientDeletionRequest(Origin.SECURITY_SERVER, server.identifier(), client.identifier());
    }

    private Fixture fixtureWithOneClientAndNoDidRow() {
        var owner = seed.member();
        var member = seed.member();
        var server = seed.server(owner);
        seed.addClient(server, member);
        return new Fixture(server, member, member, null);
    }

    private Fixture fixtureWithTwoSubsystemClients() {
        var owner = seed.member();
        var member = seed.member();
        var first = seed.subsystem(member);
        var second = seed.subsystem(member);
        var server = seed.server(owner);
        seed.addClient(server, first);
        seed.addClient(server, second);
        insertMemberDid(server.id(), member.id(), memberDid(server.address(), member.identifier()));
        return new Fixture(server, member, first, second);
    }

    private int countServerClients(Server server, ClientRef member) {
        return jdbc.queryForObject("select count(*) from server_clients sc join security_server_clients c "
                        + "on c.id = sc.security_server_client_id where sc.security_server_id = ? "
                        + "and (c.id = ? or c.xroad_member_id = ?)",
                Integer.class, server.id(), member.id(), member.id());
    }
}
