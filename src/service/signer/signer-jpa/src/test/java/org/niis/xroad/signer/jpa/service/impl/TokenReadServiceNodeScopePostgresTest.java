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
package org.niis.xroad.signer.jpa.service.impl;

import ee.ria.xroad.common.TestCertUtil;

import liquibase.Scope;
import liquibase.command.CommandScope;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.niis.xroad.common.core.node.NodeIdentity;
import org.niis.xroad.common.identifiers.jpa.mapper.XRoadIdMapper;
import org.niis.xroad.signer.core.model.BasicCertInfo;
import org.niis.xroad.signer.core.model.BasicKeyInfo;
import org.niis.xroad.signer.core.model.BasicTokenInfo;
import org.niis.xroad.signer.core.model.CertRequestData;
import org.niis.xroad.signer.core.service.TokenReadService.LoadedTokens;
import org.niis.xroad.signer.jpa.SignerDatabaseCtx;
import org.niis.xroad.signer.jpa.dao.impl.SignerKeyCertDaoImpl;
import org.niis.xroad.signer.jpa.dao.impl.SignerKeyCertRequestDaoImpl;
import org.niis.xroad.signer.jpa.dao.impl.SignerKeyDaoImpl;
import org.niis.xroad.signer.jpa.dao.impl.SignerTokenDaoImpl;
import org.niis.xroad.signer.jpa.mapper.CertMapper;
import org.niis.xroad.signer.jpa.mapper.CertRequestMapper;
import org.niis.xroad.signer.jpa.mapper.KeyMapper;
import org.niis.xroad.signer.jpa.mapper.TokenMapper;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Loads the signer tables through {@link TokenReadServiceImpl} against a real PostgreSQL database that holds
 * the schema of the real {@code serverconf-changelog.xml}, with rows of two nodes that both own a token with
 * external id {@code 0}.
 */
class TokenReadServiceNodeScopePostgresTest {

    private static final String NODE_A = "node_a";
    private static final String NODE_B = "node_b";

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16-alpine");

    private static SignerDatabaseCtx databaseCtx;
    private static NodeRows nodeARows;
    private static NodeRows nodeBRows;

    private record NodeRows(long tokenId, List<Long> keyIds, long certId, long certRequestId) {
    }

    @BeforeAll
    static void startDatabaseAndSeedBothNodes() throws Exception {
        assumeTrue(DockerClientFactory.instance().isDockerAvailable(),
                "Docker is not available, skipping the Postgres-backed test");
        POSTGRES.start();
        migrateSchema();
        nodeARows = seedNode(NODE_A);
        nodeBRows = seedNode(NODE_B);
        databaseCtx = new SignerDatabaseCtx(Optional.of(Map.of(
                "connection.url", POSTGRES.getJdbcUrl(),
                "connection.username", POSTGRES.getUsername(),
                "connection.password", POSTGRES.getPassword())), Optional.empty(), Optional.empty());
    }

    @AfterAll
    static void stopDatabase() {
        if (databaseCtx != null) {
            databaseCtx.destroy();
        }
        if (POSTGRES.isRunning()) {
            POSTGRES.stop();
        }
    }

    @Test
    void shouldLoadOnlyTheRowsOfNodeA() {
        assertLoadedRowsAre(loadTokensOf(NODE_A), nodeARows);
    }

    @Test
    void shouldLoadOnlyTheRowsOfNodeB() {
        assertLoadedRowsAre(loadTokensOf(NODE_B), nodeBRows);
    }

    private static void assertLoadedRowsAre(LoadedTokens loaded, NodeRows expected) {
        assertThat(loaded.tokens()).extracting(BasicTokenInfo::id).containsExactly(expected.tokenId());
        assertThat(loaded.keys().values().stream().flatMap(List::stream).map(BasicKeyInfo::id))
                .as("loaded keys").containsExactlyInAnyOrderElementsOf(expected.keyIds());
        assertThat(loaded.certs().values().stream().flatMap(List::stream).map(BasicCertInfo::id))
                .as("loaded certificates").containsExactly(expected.certId());
        assertThat(loaded.certRequests().values().stream().flatMap(List::stream).map(CertRequestData::id))
                .as("loaded certificate requests").containsExactly(expected.certRequestId());
    }

    private static LoadedTokens loadTokensOf(String node) {
        var xroadIdMapper = XRoadIdMapper.get();
        return new TokenReadServiceImpl(databaseCtx, new SignerTokenDaoImpl(), new SignerKeyDaoImpl(),
                new SignerKeyCertDaoImpl(), new SignerKeyCertRequestDaoImpl(), new TokenMapper(), new KeyMapper(),
                new CertMapper(xroadIdMapper), new CertRequestMapper(xroadIdMapper), new NodeIdentity(node, true))
                .loadAllTokens();
    }

    private static NodeRows seedNode(String node) throws Exception {
        long tokenId = insert("INSERT INTO signer_tokens (external_id, ha_node_name, type, created_at, updated_at)"
                + " VALUES ('0', ?, 'hsm', now(), now()) RETURNING id", node);
        long keyWithCertId = insertKey(node, tokenId, "cert-key");
        long keyWithRequestId = insertKey(node, tokenId, "request-key");
        long certId = insert("INSERT INTO signer_certificates (external_id, ha_node_name, key_id, data, active, created_at,"
                + " updated_at) VALUES (?, ?, ?, ?, true, now(), now()) RETURNING id",
                node + "-cert", node, keyWithCertId, TestCertUtil.getCaCert().getEncoded());
        long certRequestId = insert("INSERT INTO signer_certificate_requests (external_id, ha_node_name, key_id,"
                + " subject_name, created_at, updated_at) VALUES (?, ?, ?, 'CN=test', now(), now()) RETURNING id",
                node + "-request", node, keyWithRequestId);
        return new NodeRows(tokenId, List.of(keyWithCertId, keyWithRequestId), certId, certRequestId);
    }

    private static long insertKey(String node, long tokenId, String name) throws SQLException {
        return insert("INSERT INTO signer_keys (external_id, ha_node_name, token_id, type, sign_mechanism_name, created_at,"
                + " updated_at) VALUES (?, ?, ?, 'HARDWARE', 'CKM_RSA_PKCS', now(), now()) RETURNING id",
                node + "-" + name, node, tokenId);
    }

    private static long insert(String sql, Object... parameters) throws SQLException {
        try (var connection = DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
                var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            try (var resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    private static void migrateSchema() throws Exception {
        Scope.child(Scope.Attr.resourceAccessor, new ClassLoaderResourceAccessor(), () -> {
            var update = new CommandScope("update");
            update.addArgumentValue("changelogFile", "liquibase/serverconf-changelog.xml");
            update.addArgumentValue("url", POSTGRES.getJdbcUrl());
            update.addArgumentValue("username", POSTGRES.getUsername());
            update.addArgumentValue("password", POSTGRES.getPassword());
            update.addArgumentValue("contexts", "!admin");
            update.execute();
        });
    }
}
