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
package org.niis.xroad.ss.test.api.services;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceDescriptionAddDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceTypeDto;
import org.niis.xroad.ss.test.api.SsApiTest;
import org.niis.xroad.ss.test.api.admin.AccessRightsAdminClient;
import org.niis.xroad.ss.test.api.admin.ClientsAdminClient;
import org.niis.xroad.ss.test.api.admin.ServiceDescriptionsAdminClient;
import org.niis.xroad.ss.test.api.seeding.SsBaselineSeeder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * Service-client access-right scenarios migrated from the legacy Cucumber system-test suite.
 *
 * <p>Each test seeds its own namespaced client with REST services and manages service-client
 * access rights at both the service-perspective and client-perspective API layers.
 * Tests are order-independent and collision-free.
 */
@DisplayName("Service client access rights management")
@SuppressWarnings("checkstyle:magicnumber")
class ServiceClientsTest extends SsApiTest {

    private static final String SUBJECT_A = "DEV:COM:1234:test-consumer";
    private static final String SUBJECT_B = "DEV:COM:4321:TestClient";
    private static final String SUBJECT_C = "DEV:security-server-owners";

    @Test
    @DisplayName("Multiple service clients added via different services are persisted and visible on the client")
    void multipleServiceClientsAdded(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var clients = new ClientsAdminClient(session);
        var accessRights = new AccessRightsAdminClient(session);

        var clientId = given("a namespaced client with two REST services is seeded", () -> {
            var seed = seeder.seedClientWithRestService("scMulti", "http://example.com/svc1", "svc1");
            clients.addServiceDescription(seed.clientId(),
                            new ServiceDescriptionAddDto("http://example.com/svc2", ServiceTypeDto.REST)
                                    .restServiceCode("svc2"))
                    .statusCode(201);
            return seed.clientId();
        });

        when("SUBJECT_A is granted access to svc1", () -> {
            var svc1Id = clientId + ":svc1";
            accessRights.addServiceClients(svc1Id, SUBJECT_A).statusCode(200);
        });

        and("SUBJECT_B is granted access to svc2", () -> {
            var svc2Id = clientId + ":svc2";
            accessRights.addServiceClients(svc2Id, SUBJECT_B).statusCode(200);
        });

        and("SUBJECT_C is granted access to svc1", () -> {
            var svc1Id = clientId + ":svc1";
            accessRights.addServiceClients(svc1Id, SUBJECT_C).statusCode(200);
        });

        then("all three subjects appear in the client's service clients list", () -> {
            var ids = accessRights.listClientServiceClientIds(clientId);
            assertThat(ids).contains(SUBJECT_A, SUBJECT_B, SUBJECT_C);
        });
    }

    @Test
    @DisplayName("Adding additional service codes to an existing service client is persisted")
    void serviceClientEditedWithAdditionalAccessRights(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var clients = new ClientsAdminClient(session);
        var accessRights = new AccessRightsAdminClient(session);

        var clientId = given("a namespaced client with two REST services is seeded", () -> {
            var seed = seeder.seedClientWithRestService("scEdit", "http://example.com/svc1", "svc1");
            clients.addServiceDescription(seed.clientId(),
                            new ServiceDescriptionAddDto("http://example.com/svc2", ServiceTypeDto.REST)
                                    .restServiceCode("svc2"))
                    .statusCode(201);
            return seed.clientId();
        });

        given("SUBJECT_A has access to svc1 only", () ->
                accessRights.addServiceClients(clientId + ":svc1", SUBJECT_A).statusCode(200));

        var initialCodes = when("the access rights for SUBJECT_A are retrieved", () ->
                accessRights.listClientServiceClientServiceCodes(clientId, SUBJECT_A));

        then("only svc1 is present initially", () ->
                assertThat(initialCodes).containsExactly("svc1"));

        and("svc2 access right is added to SUBJECT_A", () ->
                accessRights.addClientServiceClientAccessRights(clientId, SUBJECT_A, "svc2")
                        .statusCode(201));

        then("both svc1 and svc2 appear in the access rights for SUBJECT_A", () -> {
            var codes = accessRights.listClientServiceClientServiceCodes(clientId, SUBJECT_A);
            assertThat(codes).contains("svc1", "svc2");
        });
    }

    @Test
    @DisplayName("Removing all service codes from a service client and re-adding them is persisted correctly")
    void allServiceCodesRemovedAndReAdded(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var clients = new ClientsAdminClient(session);
        var accessRights = new AccessRightsAdminClient(session);

        var clientId = given("a namespaced client with two REST services and SUBJECT_A having both", () -> {
            var seed = seeder.seedClientWithRestService("scReAdd", "http://example.com/svc1", "svc1");
            clients.addServiceDescription(seed.clientId(),
                            new ServiceDescriptionAddDto("http://example.com/svc2", ServiceTypeDto.REST)
                                    .restServiceCode("svc2"))
                    .statusCode(201);
            var cId = seed.clientId();
            accessRights.addServiceClients(cId + ":svc1", SUBJECT_A).statusCode(200);
            accessRights.addServiceClients(cId + ":svc2", SUBJECT_A).statusCode(200);
            return cId;
        });

        when("all access rights for SUBJECT_A are removed", () -> {
            accessRights.removeClientServiceClientAccessRights(clientId, SUBJECT_A, "svc1", "svc2")
                    .statusCode(204);
        });

        then("SUBJECT_A is no longer present as a service client", () -> {
            var ids = accessRights.listClientServiceClientIds(clientId);
            assertThat(ids).doesNotContain(SUBJECT_A);
        });

        when("svc1 and svc2 are re-added for SUBJECT_A via the service-perspective grant", () -> {
            accessRights.addServiceClients(clientId + ":svc1", SUBJECT_A).statusCode(200);
            accessRights.addServiceClients(clientId + ":svc2", SUBJECT_A).statusCode(200);
        });

        then("both svc1 and svc2 are present again", () -> {
            var codes = accessRights.listClientServiceClientServiceCodes(clientId, SUBJECT_A);
            assertThat(codes).contains("svc1", "svc2");
        });
    }

    @Test
    @DisplayName("Removing a single service code from a service client leaves the remaining code intact")
    void singleServiceCodeRemoved(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var clients = new ClientsAdminClient(session);
        var accessRights = new AccessRightsAdminClient(session);

        var clientId = given("a namespaced client with two REST services and SUBJECT_A having both", () -> {
            var seed = seeder.seedClientWithRestService("scRem1", "http://example.com/svc1", "svc1");
            clients.addServiceDescription(seed.clientId(),
                            new ServiceDescriptionAddDto("http://example.com/svc2", ServiceTypeDto.REST)
                                    .restServiceCode("svc2"))
                    .statusCode(201);
            var cId = seed.clientId();
            accessRights.addServiceClients(cId + ":svc1", SUBJECT_A).statusCode(200);
            accessRights.addServiceClients(cId + ":svc2", SUBJECT_A).statusCode(200);
            return cId;
        });

        when("svc1 access right is removed from SUBJECT_A", () ->
                accessRights.removeClientServiceClientAccessRights(clientId, SUBJECT_A, "svc1")
                        .statusCode(204));

        then("only svc2 remains in the access rights for SUBJECT_A", () -> {
            var codes = accessRights.listClientServiceClientServiceCodes(clientId, SUBJECT_A);
            assertThat(codes).containsExactly("svc2");
            assertThat(codes).doesNotContain("svc1");
        });
    }

    @Test
    @DisplayName("Removing all service codes from a service client removes that subject from the client's service-clients list")
    void allServiceCodesRemovedMemberGone(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var accessRights = new AccessRightsAdminClient(session);

        var clientId = given("a namespaced client with one REST service and SUBJECT_B having access", () -> {
            var seed = seeder.seedClientWithRestService("scRemAll", "http://example.com/svc1", "svc1");
            accessRights.addServiceClients(seed.clientId() + ":svc1", SUBJECT_B).statusCode(200);
            return seed.clientId();
        });

        when("all access rights for SUBJECT_B are removed", () ->
                accessRights.removeClientServiceClientAccessRights(clientId, SUBJECT_B, "svc1")
                        .statusCode(204));

        then("SUBJECT_B no longer appears in the client's service clients list", () -> {
            var ids = accessRights.listClientServiceClientIds(clientId);
            assertThat(ids).doesNotContain(SUBJECT_B);
        });
    }

    @Test
    @DisplayName("Deleting a service description removes its service clients from the client's service-clients list (cascade)")
    void serviceClientRemovedWhenServiceDeleted(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var clients = new ClientsAdminClient(session);
        var accessRights = new AccessRightsAdminClient(session);
        var serviceDescriptions = new ServiceDescriptionsAdminClient(session);

        var clientId = given("a namespaced client with two REST services", () -> {
            var seed = seeder.seedClientWithRestService("scCascade", "http://example.com/svc1", "svc1");
            clients.addServiceDescription(seed.clientId(),
                            new ServiceDescriptionAddDto("http://example.com/svc2", ServiceTypeDto.REST)
                                    .restServiceCode("svc2"))
                    .statusCode(201);
            return seed.clientId();
        });

        given("SUBJECT_A has access to svc1 only (the service that will be deleted)", () ->
                accessRights.addServiceClients(clientId + ":svc1", SUBJECT_A).statusCode(200));

        given("SUBJECT_B has access to svc2 (a different service, not deleted)", () ->
                accessRights.addServiceClients(clientId + ":svc2", SUBJECT_B).statusCode(200));

        and("SUBJECT_A is present in the client's service clients list before deletion", () -> {
            var ids = accessRights.listClientServiceClientIds(clientId);
            assertThat(ids).contains(SUBJECT_A);
        });

        var svc1DescriptionId = when("the svc1 service description ID is resolved", () ->
                findServiceDescriptionId(clients, clientId, "svc1"));

        and("the svc1 service description is deleted", () ->
                serviceDescriptions.deleteServiceDescription(svc1DescriptionId).statusCode(204));

        then("SUBJECT_A is no longer present in the client's service clients list", () -> {
            var ids = accessRights.listClientServiceClientIds(clientId);
            assertThat(ids).doesNotContain(SUBJECT_A);
        });

        and("SUBJECT_B (whose service was not deleted) is still present", () -> {
            var ids = accessRights.listClientServiceClientIds(clientId);
            assertThat(ids).contains(SUBJECT_B);
        });
    }

    private String findServiceDescriptionId(ClientsAdminClient clients, String clientId, String serviceCode) {
        return clients.listServiceDescriptions(clientId).stream()
                .filter(sd -> sd.services().stream().anyMatch(s -> serviceCode.equals(s.serviceCode())))
                .findFirst()
                .map(ClientsAdminClient.ServiceDescriptionView::id)
                .orElseThrow(() -> new AssertionError("Service description for code " + serviceCode + " not found on " + clientId));
    }
}
