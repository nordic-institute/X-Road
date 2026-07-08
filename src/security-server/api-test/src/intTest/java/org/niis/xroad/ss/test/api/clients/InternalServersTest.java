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
package org.niis.xroad.ss.test.api.clients;

import lombok.SneakyThrows;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.niis.xroad.securityserver.restapi.openapi.model.ConnectionTypeDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ConnectionTypeWrapperDto;
import org.niis.xroad.ss.test.api.SsApiTest;
import org.niis.xroad.ss.test.api.admin.InternalServersAdminClient;
import org.niis.xroad.ss.test.api.seeding.SsBaselineSeeder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * API tests for internal server connection type and TLS certificate management.
 */
@DisplayName("Internal servers — connection type and TLS certificate management")
@SuppressWarnings("checkstyle:magicnumber")
class InternalServersTest extends SsApiTest {

    @Test
    @DisplayName("Connection type change is persisted via the API")
    void connectionTypeChangePersists(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var clientId = seeder.seedSubsystem(session, "is13conntype");
        var internalServers = new InternalServersAdminClient(session);

        given("the current connection type is HTTP", () -> {
            var current = internalServers.getConnectionType(clientId);
            assertThat(current).isEqualTo("HTTP");
        });

        when("connection type is changed to HTTPS", () ->
                internalServers.updateConnectionType(clientId,
                                new ConnectionTypeWrapperDto().connectionType(ConnectionTypeDto.HTTPS))
                        .statusCode(200));

        then("the persisted connection type is HTTPS", () -> {
            var updated = internalServers.getConnectionType(clientId);
            assertThat(updated).isEqualTo("HTTPS");
        });
    }

    @Test
    @DisplayName("TLS certificate can be uploaded, retrieved, deleted, and the server certificate can be exported")
    @SneakyThrows
    void internalServersTlsCertUploadDeleteExport(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var clientId = seeder.seedSubsystem(session, "is13tlscert");
        var internalServers = new InternalServersAdminClient(session);

        given("connection type is set to HTTPS for the client", () ->
                internalServers.updateConnectionType(clientId,
                                new ConnectionTypeWrapperDto().connectionType(ConnectionTypeDto.HTTPS))
                        .statusCode(200));

        var certHash = when("an information system TLS certificate is uploaded", () -> {
            var certBytes = readCertResource("/files/cert.cer");
            return internalServers.addTlsCertificate(clientId, certBytes, "cert.cer")
                    .statusCode(201)
                    .extract()
                    .jsonPath()
                    .getString("hash");
        });

        then("the uploaded certificate is visible in the TLS certificate list", () -> {
            var hashes = internalServers.listTlsCertificateHashes(clientId);
            assertThat(hashes).contains(certHash);
        });

        when("the TLS certificate is deleted", () ->
                internalServers.deleteTlsCertificate(clientId, certHash)
                        .statusCode(204));

        then("the certificate is no longer in the TLS certificate list", () -> {
            var hashes = internalServers.listTlsCertificateHashes(clientId);
            assertThat(hashes).doesNotContain(certHash);
        });

        then("the server TLS certificate can be exported as a non-empty archive", () -> {
            var exported = internalServers.exportServerTlsCertificate();
            assertThat(exported).isNotEmpty();
        });
    }

    @SneakyThrows
    private byte[] readCertResource(String resourcePath) {
        try (var stream = getClass().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalStateException("Test resource not found: " + resourcePath);
            }
            return stream.readAllBytes();
        }
    }
}
