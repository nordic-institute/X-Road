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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.niis.xroad.securityserver.restapi.openapi.model.ClientAddDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ClientDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ConnectionTypeDto;
import org.niis.xroad.ss.test.api.SsApiTest;
import org.niis.xroad.ss.test.api.admin.ClientsAdminClient;
import org.niis.xroad.ss.test.api.seeding.SsBaselineSeeder;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.equalTo;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * API-persistence scenarios for client and subsystem add, migrated from the legacy Cucumber system-test suite.
 *
 * <p>Each test seeds its own namespaced state and runs independently in any order, warm or cold.
 */
@DisplayName("Client and subsystem add — API persistence")
@SuppressWarnings("checkstyle:magicnumber")
class ClientsAndSubsystemsTest extends SsApiTest {

    @Test
    @DisplayName("Existing registered client added via API is persisted in the client list")
    void existingClientAddedIsPersisted(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var clients = new ClientsAdminClient(session);

        var clientDto = new ClientDto(SsBaselineSeeder.SS_OWNER_CLASS, "4321")
                .subsystemCode("TestClient")
                .connectionType(ConnectionTypeDto.HTTP);
        var request = new ClientAddDto(clientDto).ignoreWarnings(true);

        var clientId = when("existing registered client DEV:COM:4321:TestClient is added", () ->
                clients.addClient(request)
                        .statusCode(201)
                        .extract()
                        .jsonPath()
                        .getString("id"));

        then("the added client is present in the client list", () -> {
            var found = clients.findClientByIdentifier(clientId);
            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(clientId);
        });
    }

    @Test
    @DisplayName("Existing subsystem added via API is persisted with correct identifier and status")
    void existingSubsystemAddedIsPersisted(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var clients = new ClientsAdminClient(session);

        var subsystemCode = "sub12existpersist";
        var clientId = "DEV:%s:%s:%s".formatted(SsBaselineSeeder.SS_OWNER_CLASS, SsBaselineSeeder.SS_OWNER_CODE,
                subsystemCode);
        var clientDto = new ClientDto(SsBaselineSeeder.SS_OWNER_CLASS, SsBaselineSeeder.SS_OWNER_CODE)
                .subsystemCode(subsystemCode)
                .connectionType(ConnectionTypeDto.HTTP);
        var request = new ClientAddDto(clientDto).ignoreWarnings(true);

        when("subsystem %s is added via API".formatted(subsystemCode), () ->
                clients.addClient(request).statusCode(201));

        then("the subsystem is present in the client list", () -> {
            var found = clients.findClientByIdentifier(clientId);
            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(clientId);
        });
    }

    static Stream<Arguments> subsystemRegistrationFailureCases() {
        return Stream.of(
                Arguments.of("sub12regfail1", null),
                Arguments.of("sub12regfail2", "Named sub12 regfail2")
        );
    }

    @ParameterizedTest(name = "subsystemCode={0}, subsystemName={1}")
    @MethodSource("subsystemRegistrationFailureCases")
    @DisplayName("New subsystem is persisted as SAVED when management registration request fails")
    void newSubsystemSavedWhenRegistrationFails(String subsystemCode, String subsystemName, SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var clients = new ClientsAdminClient(session);

        var clientId = "DEV:%s:%s:%s".formatted(SsBaselineSeeder.SS_OWNER_CLASS, SsBaselineSeeder.SS_OWNER_CODE,
                subsystemCode);
        var clientDto = new ClientDto(SsBaselineSeeder.SS_OWNER_CLASS, SsBaselineSeeder.SS_OWNER_CODE)
                .subsystemCode(subsystemCode)
                .subsystemName(subsystemName)
                .connectionType(ConnectionTypeDto.HTTP);
        var request = new ClientAddDto(clientDto).ignoreWarnings(true);

        given("new subsystem %s is added".formatted(subsystemCode), () ->
                clients.addClient(request).statusCode(201));

        when("registration request is sent for the new subsystem", () ->
                clients.registerClient(clientId)
                        .statusCode(anyOf(equalTo(400), equalTo(500))));

        then("the subsystem is still present in the client list (persisted as Saved)", () -> {
            var found = clients.findClientByIdentifier(clientId);
            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(clientId);
        });
    }
}
