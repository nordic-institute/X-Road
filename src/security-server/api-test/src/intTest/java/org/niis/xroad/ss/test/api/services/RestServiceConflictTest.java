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
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceDescriptionUpdateDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceTypeDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceUpdateDto;
import org.niis.xroad.ss.test.api.SsApiTest;
import org.niis.xroad.ss.test.api.admin.ClientsAdminClient;
import org.niis.xroad.ss.test.api.admin.ServiceDescriptionsAdminClient;
import org.niis.xroad.ss.test.api.admin.ServicesAdminClient;
import org.niis.xroad.ss.test.api.seeding.SsBaselineSeeder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * REST service conflict validation scenarios migrated from the legacy Cucumber system-test suite.
 *
 * <p>Each test seeds its own namespaced subsystem client so tests run independently in any order,
 * warm or cold, without interfering with each other.
 */
@DisplayName("REST service conflict validation")
@SuppressWarnings("checkstyle:magicnumber")
class RestServiceConflictTest extends SsApiTest {

    // MIGRATED-FROM: 0550-ss-client-rest-services.feature :: "Client service with Base Path is configured"
    @Test
    @DisplayName("REST services with base path are persisted and visible in service description list")
    void basePathConfiguredServiceIsPersisted(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var clients = new ClientsAdminClient(session);
        var clientId = seeder.seedSubsystem(session, "bpcfg");

        when("first REST service (s3c1, http://example.com) is added", () ->
                clients.addServiceDescription(clientId,
                                new ServiceDescriptionAddDto("http://example.com", ServiceTypeDto.REST)
                                        .restServiceCode("s3c1"))
                        .statusCode(201));

        and("second REST service (s3c2, http://example2.com) is added", () ->
                clients.addServiceDescription(clientId,
                                new ServiceDescriptionAddDto("http://example2.com", ServiceTypeDto.REST)
                                        .restServiceCode("s3c2"))
                        .statusCode(201));

        then("both REST services appear in the service description list", () -> {
            var urls = clients.listServiceDescriptions(clientId).stream()
                    .flatMap(sd -> sd.services().stream())
                    .map(ClientsAdminClient.ServiceView::url)
                    .toList();
            assertThat(urls).contains("http://example.com", "http://example2.com");
        });
    }

    // MIGRATED-FROM: 0550-ss-client-rest-services.feature :: "Client Rest service with duplicate service code is not added"
    @Test
    @DisplayName("Adding a REST service with a duplicate service code is rejected")
    void duplicateServiceCodeIsRejected(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var clients = new ClientsAdminClient(session);
        var clientId = seeder.seedSubsystem(session, "dupsvc");

        given("a REST service with code s3c1 already exists on the client", () ->
                clients.addServiceDescription(clientId,
                                new ServiceDescriptionAddDto("http://example.com", ServiceTypeDto.REST)
                                        .restServiceCode("s3c1"))
                        .statusCode(201));

        then("adding another REST service with the same code s3c1 is rejected with service_code_already_exists", () ->
                clients.addServiceDescription(clientId,
                                new ServiceDescriptionAddDto("http://example2.com", ServiceTypeDto.REST)
                                        .restServiceCode("s3c1"))
                        .statusCode(409)
                        .body("error.code", equalTo("service_code_already_exists")));
    }

    // MIGRATED-FROM: 0550-ss-client-rest-services.feature :: "Client Rest service with duplicate url is not added"
    @Test
    @DisplayName("Adding a REST service with a duplicate URL is rejected")
    void duplicateUrlIsRejected(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var clients = new ClientsAdminClient(session);
        var clientId = seeder.seedSubsystem(session, "dupurl");

        given("a REST service with URL http://example.com already exists on the client", () ->
                clients.addServiceDescription(clientId,
                                new ServiceDescriptionAddDto("http://example.com", ServiceTypeDto.REST)
                                        .restServiceCode("s3c1"))
                        .statusCode(201));

        then("adding another REST service with the same URL http://example.com is rejected with url_already_exists", () ->
                clients.addServiceDescription(clientId,
                                new ServiceDescriptionAddDto("http://example.com", ServiceTypeDto.REST)
                                        .restServiceCode("s3c10"))
                        .statusCode(409)
                        .body("error.code", equalTo("url_already_exists")));
    }

    // MIGRATED-FROM: 0550-ss-client-rest-services.feature :: "Updating service url to duplicate url is not allowed"
    @Test
    @DisplayName("Updating a service URL to one already in use is rejected with url_already_exists")
    void updateToDuplicateUrlIsRejected(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var clients = new ClientsAdminClient(session);
        var serviceDescriptions = new ServiceDescriptionsAdminClient(session);
        var clientId = seeder.seedSubsystem(session, "upddup");

        given("two REST services exist on the client (s3c1 at http://example.com, s3c2 at http://example2.com)", () -> {
            clients.addServiceDescription(clientId,
                            new ServiceDescriptionAddDto("http://example.com", ServiceTypeDto.REST)
                                    .restServiceCode("s3c1"))
                    .statusCode(201);
            clients.addServiceDescription(clientId,
                            new ServiceDescriptionAddDto("http://example2.com", ServiceTypeDto.REST)
                                    .restServiceCode("s3c2"))
                    .statusCode(201);
        });

        var sd2Id = when("the service description ID for s3c2 is retrieved", () ->
                clients.listServiceDescriptions(clientId).stream()
                        .filter(sd -> sd.services().stream()
                                .anyMatch(s -> "s3c2".equals(s.serviceCode())))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("Service description for s3c2 not found"))
                        .id());

        then("updating s3c2's URL to http://example.com (already used by s3c1) is rejected with url_already_exists", () ->
                serviceDescriptions.updateServiceDescription(sd2Id,
                                new ServiceDescriptionUpdateDto("http://example.com", ServiceTypeDto.REST)
                                        .restServiceCode("s3c2")
                                        .newRestServiceCode("s3c1x"))
                        .statusCode(409)
                        .body("error.code", equalTo("url_already_exists")));
    }

    // MIGRATED-FROM: 0550-ss-client-rest-services.feature :: "Client service is edited"
    @Test
    @DisplayName("REST service parameters (URL, timeout, TLS) are persisted after edit, and updating to a duplicate URL fails")
    void serviceParametersArePersistedAndDuplicateUpdateFails(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var clients = new ClientsAdminClient(session);
        var services = new ServicesAdminClient(session);
        var clientId = seeder.seedSubsystem(session, "svcedit");

        given("a REST service (s3c1 at http://example.com) exists on the client", () ->
                clients.addServiceDescription(clientId,
                                new ServiceDescriptionAddDto("http://example.com", ServiceTypeDto.REST)
                                        .restServiceCode("s3c1"))
                        .statusCode(201));

        var serviceId = clientId + ":s3c1";

        when("the service URL is updated to http://example.com/v2, timeout to 30, TLS auth disabled", () ->
                services.updateService(serviceId,
                                new ServiceUpdateDto("http://example.com/v2", 30, false))
                        .statusCode(200));

        then("the updated parameters are persisted", () ->
                session.given()
                        .get("/services/{id}", serviceId)
                        .then()
                        .statusCode(200)
                        .body("url", equalTo("http://example.com/v2"))
                        .body("timeout", equalTo(30))
                        .body("ssl_auth", equalTo(false)));

        and("a second REST service (s3c2 at http://example2.com) exists on the client", () ->
                clients.addServiceDescription(clientId,
                                new ServiceDescriptionAddDto("http://example2.com", ServiceTypeDto.REST)
                                        .restServiceCode("s3c2"))
                        .statusCode(201));

        and("updating s3c1's URL to http://example2.com (already used by s3c2) is rejected with url_already_exists", () ->
                services.updateService(serviceId,
                                new ServiceUpdateDto("http://example2.com", 30, false))
                        .statusCode(409)
                        .body("error.code", equalTo("url_already_exists")));
    }

}
