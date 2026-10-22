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

import org.junit.jupiter.api.Test;
import org.niis.xroad.cs.admin.api.domain.PublishedValueResult;
import org.niis.xroad.cs.admin.api.service.PublishedValueStore;
import org.niis.xroad.cs.admin.core.entity.SecurityServerEntity;
import org.niis.xroad.cs.admin.core.repository.SecurityServerRepository;
import org.niis.xroad.cs.admin.jpa.CentralServerSeed.Server;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.cs.admin.jpa.CentralServerSeed.dspBaseUrl;
import static org.niis.xroad.cs.admin.jpa.CentralServerSeed.systemDid;

/**
 * Writers of the server row that overlap with a publication of the SYSTEM pair keep each other's columns.
 */
class ServerRowConcurrentWritersPostgresTest extends AbstractCentralServerPostgresTest {

    private static final String MAINTENANCE_MESSAGE = "planned upgrade";

    @Autowired
    private PublishedValueStore store;
    @Autowired
    private SecurityServerRepository servers;

    @Test
    void maintenanceEditLoadedBeforePublicationAndCommittedAfterItKeepsThePublishedPair() throws Exception {
        var server = seed.server(seed.member());
        var did = systemDid(server.address(), 7183);
        var url = dspBaseUrl(server.address());
        var loaded = new CountDownLatch(1);
        var publicationCommitted = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(1);
        try {
            var edit = executor.submit(() -> transactions.execute(status -> {
                var entity = servers.findById(server.id()).orElseThrow();
                loaded.countDown();
                await(publicationCommitted);
                entity.setInMaintenanceMode(true);
                entity.setMaintenanceModeMessage(MAINTENANCE_MESSAGE);
                return servers.save(entity).getId();
            }));
            awaitHolding(loaded, edit);

            var result = transactions.execute(status -> store.writeSystemPairOnce(server.id(), did, url));
            publicationCommitted.countDown();
            edit.get(WAIT_SECONDS, TimeUnit.SECONDS);

            assertThat(result).isEqualTo(PublishedValueResult.STORED);
            assertRowHoldsEditAndPair(server, did, url);
        } finally {
            publicationCommitted.countDown();
            executor.shutdownNow();
            executor.close();
        }
    }

    @Test
    void publicationLoadedBeforeMaintenanceEditAndCommittedAfterItKeepsTheEdit() throws Exception {
        var server = seed.server(seed.member());
        var did = systemDid(server.address(), 7183);
        var url = dspBaseUrl(server.address());
        var loaded = new CountDownLatch(1);
        var editCommitted = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(1);
        try {
            var publication = executor.submit(() -> transactions.execute(status -> {
                entityManager.find(SecurityServerEntity.class, server.id());
                loaded.countDown();
                await(editCommitted);
                return store.writeSystemPairOnce(server.id(), did, url);
            }));
            awaitHolding(loaded, publication);

            transactions.executeWithoutResult(status -> {
                var entity = servers.findById(server.id()).orElseThrow();
                entity.setInMaintenanceMode(true);
                entity.setMaintenanceModeMessage(MAINTENANCE_MESSAGE);
                servers.save(entity);
            });
            editCommitted.countDown();

            assertThat(publication.get(WAIT_SECONDS, TimeUnit.SECONDS)).isEqualTo(PublishedValueResult.STORED);
            assertRowHoldsEditAndPair(server, did, url);
        } finally {
            editCommitted.countDown();
            executor.shutdownNow();
            executor.close();
        }
    }

    private void assertRowHoldsEditAndPair(Server server, String did, String url) {
        var row = jdbc.queryForMap(
                "select in_maintenance_mode, maintenance_mode_message, system_did, dsp_base_url "
                        + "from security_servers where id = ?", server.id());

        assertThat(row.get("in_maintenance_mode")).isEqualTo(true);
        assertThat(row.get("maintenance_mode_message")).isEqualTo(MAINTENANCE_MESSAGE);
        assertThat(row.get("system_did")).isEqualTo(did);
        assertThat(row.get("dsp_base_url")).isEqualTo(url);
    }
}
