/*
 * The MIT License
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
package org.niis.xroad.signer.test;

import ee.ria.xroad.common.crypto.identifier.KeyAlgorithm;

import lombok.SneakyThrows;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.niis.xroad.signer.api.dto.CertRequestInfo;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.niis.xroad.signer.proto.CertificateRequestFormat;
import org.niis.xroad.signer.protocol.dto.KeyUsageInfo;
import org.niis.xroad.test.apitest.core.junit.Step;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static ee.ria.xroad.common.util.CryptoUtils.calculateCertHexHash;
import static ee.ria.xroad.common.util.CryptoUtils.readCertificate;
import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.common.properties.NodeProperties.NodeType.PRIMARY;
import static org.niis.xroad.common.properties.NodeProperties.NodeType.SECONDARY;

/**
 * 0410 - Signer: node-scoped rows. The primary signer runs as the default node {@code node_0}, the secondary
 * node shares that name, and the second Central Server node runs as {@code node_b}, all on one database.
 * Proves that both nodes can hold token {@code 0}, that every row written by a node carries its name in the
 * database, and that the rows a signer loads are its own: the secondary node reads the primary's rows and
 * none of {@code node_b}'s.
 */
@Order(410)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SignerNodeScopedRowsIntTest extends AbstractSignerIntTest {

    private static final String TOKEN_ID_0 = "0";
    private static final String MEMBER = "DEV:COM:1234:MANAGEMENT";
    private static final String DEFAULT_NODE = "node_0";
    private static final String NODE_B = "node_b";
    private static final String TOKEN_PIN = "4321";

    private record NodeRows(String keyWithCertId, String certId, String keyWithRequestId, String certRequestId) {
        List<String> keyIds() {
            return List.of(keyWithCertId, keyWithRequestId);
        }
    }

    @Test
    @Order(1)
    @DisplayName("Both nodes hold token 0 with the same external id")
    void bothNodesHoldTokenWithSameExternalId() {
        Step.given("token '0' is active on both nodes", this::ensureTokensActive);
        Step.then("the database holds one token '0' row per node",
                () -> assertThat(rowNodes("signer_tokens", "external_id = '0'")).containsExactly(DEFAULT_NODE, NODE_B));
    }

    @Test
    @Order(2)
    @DisplayName("Rows stay with the node that created them")
    void rowsStayWithTheNodeThatCreatedThem() {
        try (var cleanups = new Cleanups()) {
            Step.given("token '0' is active on both nodes", this::ensureTokensActive);
            var primaryRows = Step.when("the primary node creates a key with a certificate and a key with a certificate request",
                    () -> createRows(client(PRIMARY), "node-scoped-primary", cleanups));
            var nodeBRows = Step.and("node_b creates a key with a certificate and a key with a certificate request",
                    () -> createRows(nodeBClient(), "node-scoped-node-b", cleanups));

            Step.then("the primary rows carry node_0 in the database", () -> assertRowsOwnedBy(primaryRows, DEFAULT_NODE));
            Step.and("the node_b rows carry node_b in the database", () -> assertRowsOwnedBy(nodeBRows, NODE_B));

            Step.when("the primary node reloads its registry from the database", () -> client(PRIMARY).refreshModules());
            Step.and("secondary node sync is forced", () -> client(SECONDARY).refreshModules());
            Step.then("the secondary node sees the primary rows and none of the node_b rows",
                    () -> assertViewHolds(client(SECONDARY), primaryRows, nodeBRows));
            Step.and("node_b sees its own rows and none of the primary rows",
                    () -> assertViewHolds(nodeBClient(), nodeBRows, primaryRows));
            Step.and("the primary node sees its own rows and none of the node_b rows",
                    () -> assertViewHolds(client(PRIMARY), primaryRows, nodeBRows));
        }
    }

    private void ensureTokensActive() {
        ensureTokenActive(client(PRIMARY), TOKEN_ID_0, TOKEN_PIN);
        ensureTokenActive(nodeBClient(), TOKEN_ID_0, TOKEN_PIN);
    }

    private NodeRows createRows(SignerRpcClient nodeClient, String label, Cleanups cleanups) {
        var keyWithCertId = nodeClient.generateKey(TOKEN_ID_0, label + "-cert-key", KeyAlgorithm.RSA).getId();
        cleanups.add(() -> nodeClient.deleteKey(keyWithCertId, true));
        var certId = importSignedCert(nodeClient, keyWithCertId);
        cleanups.add(() -> nodeClient.deleteCert(certId));
        var keyWithRequestId = nodeClient.generateKey(TOKEN_ID_0, label + "-request-key", KeyAlgorithm.RSA).getId();
        cleanups.add(() -> nodeClient.deleteKey(keyWithRequestId, true));
        var certRequestId = generateCertRequest(nodeClient, keyWithRequestId).certReqId();
        cleanups.add(() -> nodeClient.deleteCertRequest(certRequestId));

        return new NodeRows(keyWithCertId, certId, keyWithRequestId, certRequestId);
    }

    private SignerRpcClient.GeneratedCertRequestInfo generateCertRequest(SignerRpcClient nodeClient, String keyId) {
        return nodeClient.generateCertRequest(keyId, clientId(MEMBER), KeyUsageInfo.SIGNING, "CN=" + keyId,
                CertificateRequestFormat.DER);
    }

    @SneakyThrows
    private String importSignedCert(SignerRpcClient nodeClient, String keyId) {
        var csrFile = File.createTempFile("tmp", "sign_csr");
        byte[] certBytes;
        try {
            Files.write(csrFile.toPath(), generateCertRequest(nodeClient, keyId).certRequest());
            certBytes = Files.readAllBytes(signCsr(csrFile, CsrType.SIGN).toPath());
        } finally {
            Files.deleteIfExists(csrFile.toPath());
        }

        nodeClient.importCert(certBytes, CertificateInfo.STATUS_REGISTERED, clientId(MEMBER));
        return nodeClient.getCertForHash(calculateCertHexHash(readCertificate(certBytes).getEncoded())).getId();
    }

    private void assertRowsOwnedBy(NodeRows rows, String node) {
        assertAllOwnedBy("signer_keys", rows.keyIds(), node);
        assertAllOwnedBy("signer_certificates", List.of(rows.certId()), node);
        assertAllOwnedBy("signer_certificate_requests", List.of(rows.certRequestId()), node);
    }

    private void assertAllOwnedBy(String table, List<String> externalIds, String node) {
        var idList = externalIds.stream().map("'%s'"::formatted).collect(Collectors.joining(", "));
        assertThat(rowNodes(table, "external_id IN (" + idList + ")"))
                .as("owners of %s rows %s", table, externalIds)
                .hasSameSizeAs(externalIds)
                .containsOnly(node);
    }

    private List<String> rowNodes(String table, String condition) {
        var sql = "SELECT ha_node_name FROM %s WHERE %s ORDER BY ha_node_name".formatted(table, condition);
        return containerSetup.queryDatabase(sql).lines().toList();
    }

    private void assertViewHolds(SignerRpcClient viewer, NodeRows expected, NodeRows foreign) {
        var tokensWithId0 = viewer.getTokens().stream().filter(token -> TOKEN_ID_0.equals(token.getId())).toList();
        assertThat(tokensWithId0).as("tokens with id %s in the viewer's registry", TOKEN_ID_0).hasSize(1);
        var keys = tokensWithId0.getFirst().getKeyInfo();
        var certIds = keys.stream().flatMap(key -> key.getCerts().stream()).map(CertificateInfo::getId).toList();
        var certRequestIds = keys.stream().flatMap(key -> key.getCertRequests().stream()).map(CertRequestInfo::getId).toList();

        assertThat(keys).extracting(KeyInfo::getId)
                .containsAll(expected.keyIds())
                .doesNotContainAnyElementsOf(foreign.keyIds());
        assertThat(certIds).contains(expected.certId()).doesNotContain(foreign.certId());
        assertThat(certRequestIds).contains(expected.certRequestId()).doesNotContain(foreign.certRequestId());
    }

    private static final class Cleanups implements AutoCloseable {
        private final List<Runnable> cleanups = new ArrayList<>();

        void add(Runnable cleanup) {
            cleanups.add(cleanup);
        }

        @Override
        public void close() {
            var failures = new ArrayList<RuntimeException>();
            cleanups.reversed().forEach(cleanup -> {
                try {
                    cleanup.run();
                } catch (RuntimeException e) {
                    failures.add(e);
                }
            });
            if (!failures.isEmpty()) {
                var first = failures.getFirst();
                failures.stream().skip(1).forEach(first::addSuppressed);
                throw first;
            }
        }
    }
}
