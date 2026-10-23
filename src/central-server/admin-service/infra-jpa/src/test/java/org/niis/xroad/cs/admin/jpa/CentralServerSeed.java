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

import jakarta.persistence.EntityManager;
import org.niis.xroad.common.identifiers.jpa.entity.MemberIdEntity;
import org.niis.xroad.common.identifiers.jpa.entity.SecurityServerIdEntity;
import org.niis.xroad.common.identifiers.jpa.entity.SubsystemIdEntity;
import org.niis.xroad.cs.admin.core.entity.MemberClassEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerClientEntity;
import org.niis.xroad.cs.admin.core.entity.SecurityServerEntity;
import org.niis.xroad.cs.admin.core.entity.ServerClientEntity;
import org.niis.xroad.cs.admin.core.entity.SubsystemEntity;
import org.niis.xroad.cs.admin.core.entity.XRoadMemberEntity;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Creates servers, members and clients through the entities, each call in its own transaction. Every call
 * produces codes and addresses no earlier call used, because one database serves all tests of a run.
 */
final class CentralServerSeed {

    static final String INSTANCE = "DEV";
    static final String MEMBER_CLASS = "COM";

    private static final AtomicInteger COUNTER = new AtomicInteger();

    private final TransactionTemplate transactions;
    private final EntityManager entityManager;

    CentralServerSeed(TransactionTemplate transactions, EntityManager entityManager) {
        this.transactions = transactions;
        this.entityManager = entityManager;
    }

    record ClientRef(long id, ClientId identifier) {
    }

    record Server(int id, SecurityServerId identifier, String address, ClientRef owner) {
    }

    ClientRef member() {
        var code = "m" + COUNTER.incrementAndGet();
        return transactions.execute(status -> {
            var memberClass = findOrCreateMemberClass();
            var identifier = MemberIdEntity.create(INSTANCE, MEMBER_CLASS, code);
            entityManager.persist(identifier);
            var member = new XRoadMemberEntity("name-" + code, identifier, memberClass);
            entityManager.persist(member);
            return new ClientRef(member.getId(), ClientId.Conf.create(INSTANCE, MEMBER_CLASS, code));
        });
    }

    ClientRef subsystem(ClientRef member) {
        var code = "s" + COUNTER.incrementAndGet();
        return transactions.execute(status -> {
            var memberEntity = entityManager.find(XRoadMemberEntity.class, member.id());
            var identifier = SubsystemIdEntity.create(INSTANCE, MEMBER_CLASS, member.identifier().getMemberCode(), code);
            entityManager.persist(identifier);
            var subsystem = new SubsystemEntity(memberEntity, identifier);
            entityManager.persist(subsystem);
            return new ClientRef(subsystem.getId(),
                    ClientId.Conf.create(INSTANCE, MEMBER_CLASS, member.identifier().getMemberCode(), code));
        });
    }

    Server server(ClientRef owner) {
        return server(owner, newHost());
    }

    Server server(ClientRef owner, String address) {
        var serverCode = "ss" + COUNTER.incrementAndGet();
        return transactions.execute(status -> {
            var ownerEntity = entityManager.find(XRoadMemberEntity.class, owner.id());
            var server = new SecurityServerEntity(ownerEntity, serverCode);
            server.setAddress(address);
            entityManager.persist(server);
            entityManager.persist(SecurityServerIdEntity.create(owner.identifier(), serverCode));
            entityManager.persist(new ServerClientEntity(server, ownerEntity));
            return new Server(server.getId(), SecurityServerId.Conf.create(owner.identifier(), serverCode), address, owner);
        });
    }

    void addClient(Server server, ClientRef client) {
        transactions.executeWithoutResult(status -> {
            var serverEntity = entityManager.find(SecurityServerEntity.class, server.id());
            var clientEntity = entityManager.find(SecurityServerClientEntity.class, client.id());
            entityManager.persist(new ServerClientEntity(serverEntity, clientEntity));
        });
    }

    static String newHost() {
        return "ss" + COUNTER.incrementAndGet() + ".test";
    }

    static String memberDid(String host, ClientId member) {
        return "did:web:" + host + "%3A7183:v1:" + member.getXRoadInstance() + ":" + member.getMemberClass()
                + ":" + member.getMemberCode();
    }

    static String systemDid(String host, int port) {
        return "did:web:" + host + "%3A" + port + ":v1:system";
    }

    static String dspBaseUrl(String host) {
        return "https://" + host + ":8183/api/dsp";
    }

    private MemberClassEntity findOrCreateMemberClass() {
        return entityManager.createQuery("select c from MemberClassEntity c where c.code = :code", MemberClassEntity.class)
                .setParameter("code", MEMBER_CLASS)
                .getResultStream()
                .findFirst()
                .orElseGet(() -> {
                    var created = new MemberClassEntity(MEMBER_CLASS, "test class");
                    entityManager.persist(created);
                    return created;
                });
    }
}
