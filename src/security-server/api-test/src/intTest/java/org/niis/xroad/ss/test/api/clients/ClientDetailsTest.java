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

import io.restassured.http.ContentType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.niis.xroad.securityserver.restapi.openapi.model.ClientNameDto;
import org.niis.xroad.ss.test.api.SsApiTest;
import org.niis.xroad.ss.test.api.admin.ClientsAdminClient;
import org.niis.xroad.ss.test.api.seeding.SsBaselineSeeder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * API tests for client details, sign certificates, and client lifecycle management.
 */
@DisplayName("Client details — API persistence and management")
@SuppressWarnings("checkstyle:magicnumber")
class ClientDetailsTest extends SsApiTest {

    private static final String OWNER_CLIENT_ID = "DEV:%s:%s".formatted(
            SsBaselineSeeder.SS_OWNER_CLASS, SsBaselineSeeder.SS_OWNER_CODE);

    // MIGRATED-FROM: 0520-ss-client-details.feature :: "Client details are displayed"
    @Test
    @DisplayName("Client details and sign certificate information are returned by the API")
    void clientDetailsAndSignCertDisplayed(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var clients = new ClientsAdminClient(session);

        var client = when("the owner client details are retrieved", () ->
                clients.findClientByIdentifier(OWNER_CLIENT_ID));

        then("member name, class and code match the registered owner", () -> {
            assertThat(client).isNotNull();
            assertThat(client.getMemberName()).isEqualTo("Test member");
            assertThat(client.getMemberClass()).isEqualTo(SsBaselineSeeder.SS_OWNER_CLASS);
            assertThat(client.getMemberCode()).isEqualTo(SsBaselineSeeder.SS_OWNER_CODE);
        });

        and("the sign-certificates endpoint returns a list for the owner (empty in baseline — no sign cert seeded)", () -> {
            var certs = session.given()
                    .get("/clients/{id}/sign-certificates", OWNER_CLIENT_ID)
                    .then()
                    .statusCode(200)
                    .extract()
                    .jsonPath()
                    .getList("$", Object.class);
            assertThat(certs).isNotNull().isInstanceOf(List.class);
        });
    }

    // MIGRATED-FROM: 0520-ss-client-details.feature :: "Client Disable button is clicked"
    @Test
    @DisplayName("Disabling a registered client triggers a management request that fails without a CS")
    void disableClientTriggersManagementRequestFailure(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var clients = new ClientsAdminClient(session);

        var clientId = given("a subsystem client is added", () -> {
            var id = seeder.seedSubsystem(session, "cd13disable");
            clients.registerClient(id).statusCode(anyOf(equalTo(400), equalTo(500)));
            return id;
        });

        when("disable is called on the client", () ->
                session.given()
                        .put("/clients/{id}/disable", clientId)
                        .then()
                        .statusCode(409));
    }

    // MIGRATED-FROM: 0520-ss-client-details.feature :: "Subsystem rename allowed multiple times on saved client"
    @Test
    @DisplayName("Subsystem can be renamed multiple times on a saved client — each rename is accepted (API backend slice)")
    void subsystemRenamedMultipleTimesPersists(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var clientId = seeder.seedSubsystem(session, "cd13rename1");

        when("the subsystem is renamed to 'Updated1'", () ->
                session.given()
                        .contentType(ContentType.JSON)
                        .body(new ClientNameDto("Updated1"))
                        .put("/clients/{id}/rename", clientId)
                        .then()
                        .statusCode(204));

        and("the subsystem is renamed again to 'Updated2'", () ->
                session.given()
                        .contentType(ContentType.JSON)
                        .body(new ClientNameDto("Updated2"))
                        .put("/clients/{id}/rename", clientId)
                        .then()
                        .statusCode(204));

        then("the client still exists in the client list with a pending rename", () -> {
            var found = new ClientsAdminClient(session).findClientByIdentifier(clientId);
            assertThat(found).isNotNull();
            assertThat(found.getRenameStatus()).isNotNull();
        });
    }

    // MIGRATED-FROM: 0520-ss-client-details.feature :: "Subsystem rename request is sent imidiately"
    @Test
    @DisplayName("Rename API on a saved client returns a pending rename status (API backend slice)")
    void renameRequestSetsRenameStatus(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var clientId = seeder.seedSubsystem(session, "cd13rename2");

        when("rename is called with 'NewName' on the saved client", () ->
                session.given()
                        .contentType(ContentType.JSON)
                        .body(new ClientNameDto("NewName"))
                        .put("/clients/{id}/rename", clientId)
                        .then()
                        .statusCode(204));

        then("the client's rename_status is set indicating a pending name change", () -> {
            var found = new ClientsAdminClient(session).findClientByIdentifier(clientId);
            assertThat(found).isNotNull();
            assertThat(found.getRenameStatus()).isNotNull();
        });
    }
}
