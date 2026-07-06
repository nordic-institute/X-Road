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
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceUpdateDto;
import org.niis.xroad.ss.test.api.SsApiTest;
import org.niis.xroad.ss.test.api.admin.AccessRightsAdminClient;
import org.niis.xroad.ss.test.api.admin.ClientsAdminClient;
import org.niis.xroad.ss.test.api.admin.ServicesAdminClient;
import org.niis.xroad.ss.test.api.seeding.SsBaselineSeeder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * OpenAPI3 service management scenarios migrated from the legacy Cucumber system-test suite.
 *
 * <p>Each test seeds its own namespaced subsystem client so tests run independently in any order,
 * warm or cold, without interfering with each other.
 */
@DisplayName("OpenAPI3 service management")
@SuppressWarnings("checkstyle:magicnumber")
class OpenApiServiceTest extends SsApiTest {

    private static final String YAML_SPEC_URL = "http://mock-server:1080/test-services/testopenapi1.yaml";
    private static final String JSON_SPEC_URL = "http://mock-server:1080/test-services/testopenapi2.json";
    private static final String INVALID_SPEC_URL = "https://www.niis.org/nosuchopenapi.yaml";

    private static final String SUBJECT_TEST_SAVED = "DEV:COM:1234:TestSaved";
    private static final String SUBJECT_TEST_CONSUMER = "DEV:COM:1234:test-consumer";
    private static final String SUBJECT_TEST_SERVICE = "DEV:COM:1234:TestService";

    @Test
    @DisplayName("Adding an OPENAPI3 service with an unreachable or invalid spec is rejected with openapi_parsing_error")
    void invalidOpenApiSpecIsRejected(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var clients = new ClientsAdminClient(session);
        var clientId = seeder.seedSubsystem(session, "oasInvalid");

        when("an OPENAPI3 service with an invalid spec URL is submitted", () ->
                clients.addServiceDescription(clientId,
                                new ServiceDescriptionAddDto(INVALID_SPEC_URL, ServiceTypeDto.OPENAPI3)
                                        .restServiceCode("s4c1"))
                        .statusCode(400)
                        .body("error.code", equalTo("openapi_parsing_error")));
    }

    @Test
    @DisplayName("Adding an OPENAPI3 service via a YAML spec persists the service description")
    void openApiYamlSpecAdded(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var clients = new ClientsAdminClient(session);
        var clientId = seeder.seedSubsystem(session, "oasYaml");

        when("an OPENAPI3 service with the YAML spec is added", () ->
                clients.addServiceDescription(clientId,
                                new ServiceDescriptionAddDto(YAML_SPEC_URL, ServiceTypeDto.OPENAPI3)
                                        .restServiceCode("s4c1")
                                        .ignoreWarnings(true))
                        .statusCode(201));

        then("the service description appears in the client's list", () -> {
            var urls = clients.listServiceDescriptions(clientId).stream()
                    .map(ClientsAdminClient.ServiceDescriptionView::url)
                    .toList();
            assertThat(urls).contains(YAML_SPEC_URL);
        });
    }

    @Test
    @DisplayName("Adding an OPENAPI3 service via a JSON spec persists the service description")
    void openApiJsonSpecAdded(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var clients = new ClientsAdminClient(session);
        var clientId = seeder.seedSubsystem(session, "oasJson");

        when("an OPENAPI3 service with the JSON spec is added", () ->
                clients.addServiceDescription(clientId,
                                new ServiceDescriptionAddDto(JSON_SPEC_URL, ServiceTypeDto.OPENAPI3)
                                        .restServiceCode("s4c2")
                                        .ignoreWarnings(true))
                        .statusCode(201));

        then("the service description appears in the client's list", () -> {
            var urls = clients.listServiceDescriptions(clientId).stream()
                    .map(ClientsAdminClient.ServiceDescriptionView::url)
                    .toList();
            assertThat(urls).contains(JSON_SPEC_URL);
        });
    }

    @Test
    @DisplayName("Editing an OPENAPI3 service's URL, timeout and TLS flag is persisted")
    void openApiServiceEdited(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var services = new ServicesAdminClient(session);
        var seed = seeder.seedClientWithOpenApiService("oasEdit", JSON_SPEC_URL, "s4c2");
        var serviceId = seed.clientId() + ":s4c2";

        when("the service URL is updated to https://petstore.swagger.io/v3, timeout to 30, TLS auth disabled", () ->
                services.updateService(serviceId,
                                new ServiceUpdateDto("https://petstore.swagger.io/v3", 30, false))
                        .statusCode(200));

        then("the updated parameters are persisted", () ->
                services.getService(serviceId)
                        .statusCode(200)
                        .body("url", equalTo("https://petstore.swagger.io/v3"))
                        .body("timeout", equalTo(30))
                        .body("ssl_auth", equalTo(false)));
    }

    @Test
    @DisplayName("Access rights added to an OPENAPI3 service are persisted")
    void accessRightsAddedToOpenApiService(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var accessRights = new AccessRightsAdminClient(session);
        var seed = seeder.seedClientWithOpenApiService("oasAclAdd", JSON_SPEC_URL, "s4c2");
        var serviceId = seed.clientId() + ":s4c2";

        when("access rights for TestSaved and test-consumer are added to the service", () ->
                accessRights.addServiceClients(serviceId, SUBJECT_TEST_SAVED, SUBJECT_TEST_CONSUMER)
                        .statusCode(200));

        then("both subjects appear in the service clients list", () -> {
            var ids = accessRights.listServiceClientIds(serviceId);
            assertThat(ids).contains(SUBJECT_TEST_SAVED, SUBJECT_TEST_CONSUMER);
        });
    }

    @Test
    @DisplayName("Removing one access right from an OPENAPI3 service leaves the remaining subject in place")
    void removingOneAccessRightFromOpenApiService(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var accessRights = new AccessRightsAdminClient(session);
        var seed = seeder.seedClientWithOpenApiService("oasAclRem1", JSON_SPEC_URL, "s4c2");
        var serviceId = seed.clientId() + ":s4c2";

        given("two access rights are present on the service", () ->
                accessRights.addServiceClients(serviceId, SUBJECT_TEST_SAVED, SUBJECT_TEST_CONSUMER)
                        .statusCode(200));

        when("TestSaved access right is removed", () ->
                accessRights.removeServiceClients(serviceId, SUBJECT_TEST_SAVED)
                        .statusCode(204));

        then("TestSaved is no longer in the list", () ->
                accessRights.listServiceClients(serviceId)
                        .statusCode(200)
                        .body("id", not(hasItem(SUBJECT_TEST_SAVED))));

        and("test-consumer is still present", () ->
                accessRights.listServiceClients(serviceId)
                        .statusCode(200)
                        .body("id", hasItem(SUBJECT_TEST_CONSUMER)));
    }

    @Test
    @DisplayName("Removing all access rights from an OPENAPI3 service leaves the ACL empty")
    void removingAllAccessRightsFromOpenApiService(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var accessRights = new AccessRightsAdminClient(session);
        var seed = seeder.seedClientWithOpenApiService("oasAclRemAll", JSON_SPEC_URL, "s4c2");
        var serviceId = seed.clientId() + ":s4c2";

        given("three access rights are added to the service", () ->
                accessRights.addServiceClients(serviceId, SUBJECT_TEST_SAVED, SUBJECT_TEST_CONSUMER, SUBJECT_TEST_SERVICE)
                        .statusCode(200));

        when("all access rights are removed", () ->
                accessRights.removeServiceClients(serviceId, SUBJECT_TEST_SAVED, SUBJECT_TEST_CONSUMER, SUBJECT_TEST_SERVICE)
                        .statusCode(204));

        then("the service client list no longer contains any of the removed subjects", () -> {
            var ids = accessRights.listServiceClientIds(serviceId);
            assertThat(ids).doesNotContain(SUBJECT_TEST_SAVED, SUBJECT_TEST_CONSUMER, SUBJECT_TEST_SERVICE);
        });
    }
}
