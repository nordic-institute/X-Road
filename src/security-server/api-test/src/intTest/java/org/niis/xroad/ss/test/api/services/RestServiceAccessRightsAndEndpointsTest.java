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
import org.niis.xroad.securityserver.restapi.openapi.model.EndpointDto;
import org.niis.xroad.securityserver.restapi.openapi.model.EndpointUpdateDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceDescriptionAddDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceDescriptionUpdateDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ServiceTypeDto;
import org.niis.xroad.ss.test.api.SsApiTest;
import org.niis.xroad.ss.test.api.admin.AccessRightsAdminClient;
import org.niis.xroad.ss.test.api.admin.ClientsAdminClient;
import org.niis.xroad.ss.test.api.admin.EndpointsAdminClient;
import org.niis.xroad.ss.test.api.admin.ServiceDescriptionsAdminClient;
import org.niis.xroad.ss.test.api.seeding.SsBaselineSeeder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * REST service access-rights and endpoint management scenarios migrated from the legacy Cucumber system-test suite.
 *
 * <p>Each test seeds its own namespaced subsystem and REST service so tests run independently in any order,
 * warm or cold, without interfering with each other.
 */
@DisplayName("REST service access rights and endpoint management")
@SuppressWarnings("checkstyle:magicnumber")
class RestServiceAccessRightsAndEndpointsTest extends SsApiTest {

    private static final String SUBJECT_TEST_SAVED = "DEV:COM:1234:TestSaved";
    private static final String SUBJECT_TEST_CONSUMER = "DEV:COM:1234:test-consumer";
    private static final String SUBJECT_TEST_SERVICE = "DEV:COM:1234:TestService";

    // MIGRATED-FROM: 0550-ss-client-rest-services.feature :: "Client service has access rights added to it"
    @Test
    @DisplayName("Access rights added to a REST service are persisted")
    void accessRightsAddedToServiceArePersisted(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var accessRights = new AccessRightsAdminClient(session);
        var seed = seedServiceForTest(seeder, session, "aclAdd");
        var serviceId = seed.serviceId();

        when("access rights for TestSaved and test-consumer are added to the service", () ->
                accessRights.addServiceClients(serviceId, SUBJECT_TEST_SAVED, SUBJECT_TEST_CONSUMER)
                        .statusCode(200));

        then("both subjects appear in the service clients list", () -> {
            var ids = accessRights.listServiceClientIds(serviceId);
            assertThat(ids).contains(SUBJECT_TEST_SAVED, SUBJECT_TEST_CONSUMER);
        });
    }

    // MIGRATED-FROM: 0550-ss-client-rest-services.feature :: "Client service has one access rights removed"
    @Test
    @DisplayName("Removing one access right leaves the remaining subject in place")
    void removingOneAccessRightLeavesOthers(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var accessRights = new AccessRightsAdminClient(session);
        var seed = seedServiceForTest(seeder, session, "aclRem1");
        var serviceId = seed.serviceId();

        given("two access rights are present", () ->
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

    // MIGRATED-FROM: 0550-ss-client-rest-services.feature :: "Client service has all access rights removed"
    @Test
    @DisplayName("Removing all access rights leaves the ACL empty")
    void removingAllAccessRightsLeavesEmptyAcl(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var accessRights = new AccessRightsAdminClient(session);
        var seed = seedServiceForTest(seeder, session, "aclRemAll");
        var serviceId = seed.serviceId();

        given("three access rights are present", () ->
                accessRights.addServiceClients(serviceId, SUBJECT_TEST_SAVED, SUBJECT_TEST_CONSUMER, SUBJECT_TEST_SERVICE)
                        .statusCode(200));

        when("all access rights are removed", () ->
                accessRights.removeServiceClients(serviceId, SUBJECT_TEST_SAVED, SUBJECT_TEST_CONSUMER, SUBJECT_TEST_SERVICE)
                        .statusCode(204));

        then("the service client list is empty", () -> {
            var ids = accessRights.listServiceClientIds(serviceId);
            assertThat(ids).doesNotContain(SUBJECT_TEST_SAVED, SUBJECT_TEST_CONSUMER, SUBJECT_TEST_SERVICE);
        });
    }

    // MIGRATED-FROM: 0550-ss-client-rest-services.feature :: "Client service has new endpoint added to it"
    @Test
    @DisplayName("Adding an endpoint persists it; duplicate method+path is rejected")
    void newEndpointAddedAndDuplicateRejected(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var endpoints = new EndpointsAdminClient(session);
        var seed = seedServiceForTest(seeder, session, "epAdd");
        var serviceId = seed.serviceId();

        when("endpoint PATCH /new/path/ is added", () ->
                endpoints.addEndpoint(serviceId,
                                new EndpointDto("s3c1", EndpointDto.MethodEnum.PATCH, "/new/path/"))
                        .statusCode(201));

        then("the endpoint appears in the service endpoint list", () -> {
            var list = endpoints.listEndpoints(serviceId);
            assertThat(hasEndpoint(list, "PATCH", "/new/path/")).isTrue();
        });

        and("adding the same endpoint again is rejected with 409", () ->
                endpoints.addEndpoint(serviceId,
                                new EndpointDto("s3c1", EndpointDto.MethodEnum.PATCH, "/new/path/"))
                        .statusCode(409));
    }

    // MIGRATED-FROM: 0550-ss-client-rest-services.feature :: "Manually added endpoints can be edited"
    @Test
    @DisplayName("Editing an endpoint's path is persisted")
    void manualEndpointCanBeEdited(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var endpoints = new EndpointsAdminClient(session);
        var seed = seedServiceForTest(seeder, session, "epEdit");
        var serviceId = seed.serviceId();

        given("endpoint PATCH /new/path/ is added", () ->
                endpoints.addEndpoint(serviceId,
                                new EndpointDto("s3c1", EndpointDto.MethodEnum.PATCH, "/new/path/"))
                        .statusCode(201));

        var endpointId = when("the endpoint id is retrieved from the service", () ->
                findEndpointId(endpoints, serviceId, "PATCH", "/new/path/"));

        and("the path is changed to /new/path/edited", () ->
                endpoints.updateEndpoint(endpointId,
                                new EndpointUpdateDto(EndpointUpdateDto.MethodEnum.PATCH, "/new/path/edited"))
                        .statusCode(200)
                        .body("path", equalTo("/new/path/edited")));

        then("the edited endpoint is present in the list", () -> {
            var list = endpoints.listEndpoints(serviceId);
            assertThat(hasEndpoint(list, "PATCH", "/new/path/edited")).isTrue();
        });

        and("the original path is gone", () -> {
            var list = endpoints.listEndpoints(serviceId);
            assertThat(hasEndpoint(list, "PATCH", "/new/path/")).isFalse();
        });
    }

    // MIGRATED-FROM: 0550-ss-client-rest-services.feature :: "Manually added endpoints can be deleted"
    @Test
    @DisplayName("Deleting an endpoint removes it from the list")
    void manualEndpointCanBeDeleted(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var endpoints = new EndpointsAdminClient(session);
        var seed = seedServiceForTest(seeder, session, "epDel");
        var serviceId = seed.serviceId();

        given("endpoint PATCH /new/path/edited is added", () ->
                endpoints.addEndpoint(serviceId,
                                new EndpointDto("s3c1", EndpointDto.MethodEnum.PATCH, "/new/path/edited"))
                        .statusCode(201));

        var endpointId = when("the endpoint id is retrieved from the service", () ->
                findEndpointId(endpoints, serviceId, "PATCH", "/new/path/edited"));

        and("the endpoint is deleted", () ->
                endpoints.deleteEndpoint(endpointId)
                        .statusCode(204));

        then("the endpoint is no longer present in the list", () -> {
            var list = endpoints.listEndpoints(serviceId);
            assertThat(hasEndpoint(list, "PATCH", "/new/path/edited")).isFalse();
        });
    }

    // MIGRATED-FROM: 0550-ss-client-rest-services.feature :: "Newly added one service is disabled and the second is enabled with one added endpoint"
    @Test
    @DisplayName("Disabling one service description and enabling another, then adding an endpoint, persists state correctly")
    void serviceDisableEnableAndEndpointAdd(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var clients = new ClientsAdminClient(session);
        var serviceDescriptions = new ServiceDescriptionsAdminClient(session);
        var endpoints = new EndpointsAdminClient(session);
        var clientId = seeder.seedSubsystem(session, "sdToggle");

        given("two REST services are added to the client", () -> {
            clients.addServiceDescription(clientId,
                            new ServiceDescriptionAddDto("http://example.com/v2", ServiceTypeDto.REST)
                                    .restServiceCode("s3c1"))
                    .statusCode(201);
            clients.addServiceDescription(clientId,
                            new ServiceDescriptionAddDto("http://example2.com", ServiceTypeDto.REST)
                                    .restServiceCode("s3c2"))
                    .statusCode(201);
        });

        var sd1Id = when("s3c1 service description id is retrieved", () ->
                findServiceDescriptionId(clients, clientId, "s3c1"));

        var sd2Id = and("s3c2 service description id is retrieved", () ->
                findServiceDescriptionId(clients, clientId, "s3c2"));

        and("s3c1 (http://example.com/v2) is disabled with a notice", () ->
                serviceDescriptions.disableServiceDescription(sd1Id, "just disabled.")
                        .statusCode(200));

        then("s3c1 service description shows disabled=true", () ->
                serviceDescriptions.getServiceDescription(sd1Id)
                        .statusCode(200)
                        .body("disabled", equalTo(true))
                        .body("disabled_notice", equalTo("just disabled.")));

        when("s3c2 is enabled", () ->
                serviceDescriptions.enableServiceDescription(sd2Id)
                        .statusCode(200));

        then("s3c2 service description shows disabled=false", () ->
                serviceDescriptions.getServiceDescription(sd2Id)
                        .statusCode(200)
                        .body("disabled", equalTo(false)));

        and("endpoint GET /*/pets/* is added to s3c2", () -> {
            var s3c2ServiceId = clientId + ":s3c2";
            endpoints.addEndpoint(s3c2ServiceId,
                            new EndpointDto("s3c2", EndpointDto.MethodEnum.GET, "/*/pets/*"))
                    .statusCode(201);
            var list = endpoints.listEndpoints(s3c2ServiceId);
            assertThat(hasEndpoint(list, "GET", "/*/pets/*")).isTrue();
        });
    }

    // MIGRATED-FROM: 0550-ss-client-rest-services.feature :: "Newly added service is edited"
    @Test
    @DisplayName("Editing a REST service's URL and code is persisted; old URL disappears from the list")
    void newlyAddedServiceCanBeEdited(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var clients = new ClientsAdminClient(session);
        var serviceDescriptions = new ServiceDescriptionsAdminClient(session);
        var clientId = seeder.seedSubsystem(session, "sdEdit");

        given("a REST service s3c1 at http://example.com/v2 is added", () ->
                clients.addServiceDescription(clientId,
                                new ServiceDescriptionAddDto("http://example.com/v2", ServiceTypeDto.REST)
                                        .restServiceCode("s3c1"))
                        .statusCode(201));

        var sdId = when("the service description ID for s3c1 is retrieved", () ->
                findServiceDescriptionId(clients, clientId, "s3c1"));

        and("the service description is updated to url=http://example.com/v3 code=s5c200", () ->
                serviceDescriptions.updateServiceDescription(sdId,
                                new ServiceDescriptionUpdateDto("http://example.com/v3", ServiceTypeDto.REST)
                                        .restServiceCode("s3c1")
                                        .newRestServiceCode("s5c200"))
                        .statusCode(200)
                        .body("url", equalTo("http://example.com/v3")));

        then("http://example.com/v3 appears in the client's service description list", () -> {
            var urls = clients.listServiceDescriptions(clientId).stream()
                    .flatMap(sd -> sd.services().stream())
                    .map(ClientsAdminClient.ServiceView::url)
                    .toList();
            assertThat(urls).contains("http://example.com/v3");
        });

        and("http://example.com/v2 is no longer in the list", () -> {
            var urls = clients.listServiceDescriptions(clientId).stream()
                    .flatMap(sd -> sd.services().stream())
                    .map(ClientsAdminClient.ServiceView::url)
                    .toList();
            assertThat(urls).doesNotContain("http://example.com/v2");
        });
    }

    // MIGRATED-FROM: 0550-ss-client-rest-services.feature :: "Newly added service is deleted"
    @Test
    @DisplayName("Deleting a REST service description removes it from the client list")
    void newlyAddedServiceCanBeDeleted(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var clients = new ClientsAdminClient(session);
        var serviceDescriptions = new ServiceDescriptionsAdminClient(session);
        var clientId = seeder.seedSubsystem(session, "sdDel");

        given("a REST service s3c1 at http://example.com/v3 is added", () ->
                clients.addServiceDescription(clientId,
                                new ServiceDescriptionAddDto("http://example.com/v3", ServiceTypeDto.REST)
                                        .restServiceCode("s3c1"))
                        .statusCode(201));

        var sdId = when("the service description ID is retrieved", () ->
                findServiceDescriptionId(clients, clientId, "s3c1"));

        and("the service description is deleted", () ->
                serviceDescriptions.deleteServiceDescription(sdId)
                        .statusCode(204));

        then("the URL is no longer visible in the client's service description list", () -> {
            var urls = clients.listServiceDescriptions(clientId).stream()
                    .flatMap(sd -> sd.services().stream())
                    .map(ClientsAdminClient.ServiceView::url)
                    .toList();
            assertThat(urls).doesNotContain("http://example.com/v3");
        });
    }

    private ServiceSeed seedServiceForTest(SsBaselineSeeder seeder, org.niis.xroad.ss.test.api.admin.AdminApiSession session,
                                           String ns) {
        var result = seeder.seedClientWithRestService(ns, "http://example.com/v2", "s3c1");
        var serviceId = result.clientId() + ":s3c1";
        return new ServiceSeed(result.clientId(), result.serviceDescriptionId(), serviceId);
    }

    private String findServiceDescriptionId(ClientsAdminClient clients, String clientId, String serviceCode) {
        return clients.listServiceDescriptions(clientId).stream()
                .filter(sd -> sd.services().stream().anyMatch(s -> serviceCode.equals(s.serviceCode())))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Service description for code " + serviceCode + " not found"))
                .id();
    }

    private String findEndpointId(EndpointsAdminClient endpoints, String serviceId, String method, String path) {
        return endpoints.listEndpoints(serviceId).stream()
                .filter(e -> method.equals(e.method()) && path.equals(e.path()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Endpoint " + method + " " + path + " not found on " + serviceId))
                .id();
    }

    private boolean hasEndpoint(List<EndpointsAdminClient.EndpointView> list, String method, String path) {
        return list.stream().anyMatch(e -> method.equals(e.method()) && path.equals(e.path()));
    }

    private record ServiceSeed(String clientId, String serviceDescriptionId, String serviceId) {
    }
}
