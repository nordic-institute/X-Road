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
import org.niis.xroad.ss.test.api.admin.ClientsAdminClient;
import org.niis.xroad.ss.test.api.admin.EndpointsAdminClient;
import org.niis.xroad.ss.test.api.admin.ServiceDescriptionsAdminClient;
import org.niis.xroad.ss.test.api.admin.ServicesAdminClient;
import org.niis.xroad.ss.test.api.seeding.SsBaselineSeeder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * OPENAPI3 service endpoint management and service-description lifecycle scenarios
 * migrated from the legacy Cucumber system-test suite.
 *
 * <p>Each test seeds its own namespaced subsystem client so tests run independently in any order,
 * warm or cold, without interfering with each other.
 */
@DisplayName("OPENAPI3 service endpoint and lifecycle management")
@SuppressWarnings("checkstyle:magicnumber")
class OpenApiServiceEndpointsAndLifecycleTest extends SsApiTest {

    private static final String YAML_SPEC_URL = "http://mock-server:1080/test-services/testopenapi1.yaml";
    private static final String JSON_SPEC_URL = "http://mock-server:1080/test-services/testopenapi2.json";
    private static final String OAS31_SPEC_URL = "http://mock-server:1080/test-services/testopenapi_v310.json";
    private static final String INVALID_VERSION_SPEC_URL =
            "http://mock-server:1080/test-services/testopenapi_invalid_version.yaml";

    private static final String YAML_SERVICE_CODE = "s4c1";
    private static final String JSON_SERVICE_CODE = "s4c2";

    @Test
    @DisplayName("Adding an endpoint to an OPENAPI3 service persists it; a duplicate method+path is rejected with 409")
    void newEndpointAddedAndDuplicateRejected(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var endpoints = new EndpointsAdminClient(session);
        var seed = seeder.seedClientWithOpenApiService("oasEpAdd", JSON_SPEC_URL, JSON_SERVICE_CODE);
        var serviceId = seed.clientId() + ":" + JSON_SERVICE_CODE;

        when("endpoint PATCH /new/path/ is added to the OPENAPI3 service", () ->
                endpoints.addEndpoint(serviceId,
                                new EndpointDto(JSON_SERVICE_CODE, EndpointDto.MethodEnum.PATCH, "/new/path/"))
                        .statusCode(201));

        then("the endpoint appears in the service endpoint list", () -> {
            var list = endpoints.listEndpoints(serviceId);
            assertThat(hasEndpoint(list, "PATCH", "/new/path/")).isTrue();
        });

        and("adding the same endpoint again is rejected with 409", () ->
                endpoints.addEndpoint(serviceId,
                                new EndpointDto(JSON_SERVICE_CODE, EndpointDto.MethodEnum.PATCH, "/new/path/"))
                        .statusCode(409));
    }

    @Test
    @DisplayName("Editing a manually added OPENAPI3 endpoint path is persisted; original path is gone")
    void manualEndpointEdited(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var endpoints = new EndpointsAdminClient(session);
        var seed = seeder.seedClientWithOpenApiService("oasEpEdit", JSON_SPEC_URL, JSON_SERVICE_CODE);
        var serviceId = seed.clientId() + ":" + JSON_SERVICE_CODE;

        given("a manual endpoint PATCH /new/path/ is added", () ->
                endpoints.addEndpoint(serviceId,
                                new EndpointDto(JSON_SERVICE_CODE, EndpointDto.MethodEnum.PATCH, "/new/path/"))
                        .statusCode(201));

        var endpointId = when("the endpoint id is retrieved", () ->
                findEndpointId(endpoints, serviceId, "PATCH", "/new/path/"));

        and("the endpoint path is changed to /new/path/edited", () ->
                endpoints.updateEndpoint(endpointId,
                                new EndpointUpdateDto(EndpointUpdateDto.MethodEnum.PATCH, "/new/path/edited"))
                        .statusCode(200)
                        .body("path", equalTo("/new/path/edited")));

        then("the updated endpoint is present in the list", () -> {
            var list = endpoints.listEndpoints(serviceId);
            assertThat(hasEndpoint(list, "PATCH", "/new/path/edited")).isTrue();
        });

        and("the original path is no longer present", () -> {
            var list = endpoints.listEndpoints(serviceId);
            assertThat(hasEndpoint(list, "PATCH", "/new/path/")).isFalse();
        });
    }

    @Test
    @DisplayName("Deleting a manually added OPENAPI3 endpoint removes it from the list")
    void manualEndpointsDeleted(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var endpoints = new EndpointsAdminClient(session);
        var seed = seeder.seedClientWithOpenApiService("oasEpDel", JSON_SPEC_URL, JSON_SERVICE_CODE);
        var serviceId = seed.clientId() + ":" + JSON_SERVICE_CODE;

        given("a manual endpoint PATCH /new/path/edited is added", () ->
                endpoints.addEndpoint(serviceId,
                                new EndpointDto(JSON_SERVICE_CODE, EndpointDto.MethodEnum.PATCH, "/new/path/edited"))
                        .statusCode(201));

        var endpointId = when("the endpoint id is retrieved", () ->
                findEndpointId(endpoints, serviceId, "PATCH", "/new/path/edited"));

        and("the endpoint is deleted", () ->
                endpoints.deleteEndpoint(endpointId)
                        .statusCode(204));

        then("the endpoint is no longer present in the list", () -> {
            var list = endpoints.listEndpoints(serviceId);
            assertThat(hasEndpoint(list, "PATCH", "/new/path/edited")).isFalse();
        });
    }

    @Test
    @DisplayName("Enabling both OPENAPI3 service descriptions then disabling one persists state correctly")
    void openApiServicesEnabledAndOneDisabled(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var clients = new ClientsAdminClient(session);
        var serviceDescriptions = new ServiceDescriptionsAdminClient(session);
        var clientId = seeder.seedSubsystem(session, "oasToggle");

        given("YAML and JSON OPENAPI3 service descriptions are added to the client", () -> {
            clients.addServiceDescription(clientId,
                            new ServiceDescriptionAddDto(YAML_SPEC_URL, ServiceTypeDto.OPENAPI3)
                                    .restServiceCode(YAML_SERVICE_CODE)
                                    .ignoreWarnings(true))
                    .statusCode(201);
            clients.addServiceDescription(clientId,
                            new ServiceDescriptionAddDto(JSON_SPEC_URL, ServiceTypeDto.OPENAPI3)
                                    .restServiceCode(JSON_SERVICE_CODE)
                                    .ignoreWarnings(true))
                    .statusCode(201);
        });

        var yamlSdId = when("the YAML service description id is retrieved", () ->
                findServiceDescriptionIdByUrl(clients, clientId, YAML_SPEC_URL));

        var jsonSdId = and("the JSON service description id is retrieved", () ->
                findServiceDescriptionIdByUrl(clients, clientId, JSON_SPEC_URL));

        and("both service descriptions are enabled", () -> {
            serviceDescriptions.enableServiceDescription(yamlSdId).statusCode(200);
            serviceDescriptions.enableServiceDescription(jsonSdId).statusCode(200);
        });

        then("both service descriptions show disabled=false", () -> {
            serviceDescriptions.getServiceDescription(yamlSdId)
                    .statusCode(200).body("disabled", equalTo(false));
            serviceDescriptions.getServiceDescription(jsonSdId)
                    .statusCode(200).body("disabled", equalTo(false));
        });

        when("the JSON service description is disabled with notice 'just disabled.'", () ->
                serviceDescriptions.disableServiceDescription(jsonSdId, "just disabled.")
                        .statusCode(200));

        then("the JSON service description shows disabled=true with the expected notice", () ->
                serviceDescriptions.getServiceDescription(jsonSdId)
                        .statusCode(200)
                        .body("disabled", equalTo(true))
                        .body("disabled_notice", equalTo("just disabled.")));

        and("the YAML service description remains enabled", () ->
                serviceDescriptions.getServiceDescription(yamlSdId)
                        .statusCode(200)
                        .body("disabled", equalTo(false)));
    }

    @Test
    @DisplayName("Editing an OPENAPI3 service description's URL and code is persisted; old URL disappears from the list")
    void newlyAddedOpenApiServiceEdited(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var clients = new ClientsAdminClient(session);
        var serviceDescriptions = new ServiceDescriptionsAdminClient(session);
        var seed = seeder.seedClientWithOpenApiService("oasSdEdit", YAML_SPEC_URL, YAML_SERVICE_CODE);
        var clientId = seed.clientId();
        var sdId = seed.serviceDescriptionId();

        var updatedUrl = "http://mock-server:1080/test-services/testopenapi11.yaml";
        var newServiceCode = "s4c100";

        when("the service description is updated to a new URL and service code", () ->
                serviceDescriptions.updateServiceDescription(sdId,
                                new ServiceDescriptionUpdateDto(updatedUrl, ServiceTypeDto.OPENAPI3)
                                        .restServiceCode(YAML_SERVICE_CODE)
                                        .newRestServiceCode(newServiceCode)
                                        .ignoreWarnings(true))
                        .statusCode(200)
                        .body("url", equalTo(updatedUrl)));

        then("the updated URL appears in the client's service description list", () -> {
            var urls = clients.listServiceDescriptions(clientId).stream()
                    .map(ClientsAdminClient.ServiceDescriptionView::url)
                    .toList();
            assertThat(urls).contains(updatedUrl);
        });

        and("the original YAML spec URL is no longer in the list", () -> {
            var urls = clients.listServiceDescriptions(clientId).stream()
                    .map(ClientsAdminClient.ServiceDescriptionView::url)
                    .toList();
            assertThat(urls).doesNotContain(YAML_SPEC_URL);
        });
    }

    @Test
    @DisplayName("Deleting an OPENAPI3 service description removes it from the client list")
    void newlyAddedOpenApiServiceDeleted(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var clients = new ClientsAdminClient(session);
        var serviceDescriptions = new ServiceDescriptionsAdminClient(session);
        var seed = seeder.seedClientWithOpenApiService("oasSdDel", JSON_SPEC_URL, JSON_SERVICE_CODE);
        var clientId = seed.clientId();
        var sdId = seed.serviceDescriptionId();

        when("the service description is deleted", () ->
                serviceDescriptions.deleteServiceDescription(sdId)
                        .statusCode(204));

        then("the JSON spec URL is no longer visible in the client's service description list", () -> {
            var urls = clients.listServiceDescriptions(clientId).stream()
                    .map(ClientsAdminClient.ServiceDescriptionView::url)
                    .toList();
            assertThat(urls).doesNotContain(JSON_SPEC_URL);
        });
    }

    @Test
    @DisplayName("Adding an OPENAPI3 3.1 JSON spec parses the server URL and all declared endpoints correctly")
    void openApi31JsonSpecAdded(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var clients = new ClientsAdminClient(session);
        var endpoints = new EndpointsAdminClient(session);
        var services = new ServicesAdminClient(session);
        var clientId = seeder.seedSubsystem(session, "oas31");
        var serviceCode = "testOas31";
        var serviceId = clientId + ":" + serviceCode;

        when("the OPENAPI3 3.1 JSON spec is added", () ->
                clients.addServiceDescription(clientId,
                                new ServiceDescriptionAddDto(OAS31_SPEC_URL, ServiceTypeDto.OPENAPI3)
                                        .restServiceCode(serviceCode)
                                        .ignoreWarnings(true))
                        .statusCode(201));

        then("the service URL is parsed from the spec's servers block", () ->
                services.getService(serviceId)
                        .statusCode(200)
                        .body("url", equalTo("https://example.org/api")));

        and("the generated endpoints include GET /test, POST /test, and POST /file", () -> {
            var list = endpoints.listEndpoints(serviceId);
            assertThat(hasEndpoint(list, "GET", "/test")).isTrue();
            assertThat(hasEndpoint(list, "POST", "/test")).isTrue();
            assertThat(hasEndpoint(list, "POST", "/file")).isTrue();
        });
    }

    @Test
    @DisplayName("Adding an OPENAPI3 service with an unsupported version is rejected with unsupported_openapi_version")
    void invalidOpenApiVersionRejected(SsBaselineSeeder seeder) {
        var session = seeder.newSession();
        var clients = new ClientsAdminClient(session);
        var clientId = seeder.seedSubsystem(session, "oasInvVer");

        when("an OPENAPI3 service with a 3.1.1 spec (unsupported version) is submitted", () ->
                clients.addServiceDescription(clientId,
                                new ServiceDescriptionAddDto(INVALID_VERSION_SPEC_URL, ServiceTypeDto.OPENAPI3)
                                        .restServiceCode("testOas31x"))
                        .statusCode(400)
                        .body("error.code", equalTo("unsupported_openapi_version")));
    }

    private String findEndpointId(EndpointsAdminClient endpoints, String serviceId, String method, String path) {
        return endpoints.listEndpoints(serviceId).stream()
                .filter(e -> method.equals(e.method()) && path.equals(e.path()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Endpoint " + method + " " + path + " not found on " + serviceId))
                .id();
    }

    private String findServiceDescriptionIdByUrl(ClientsAdminClient clients, String clientId, String url) {
        return clients.listServiceDescriptions(clientId).stream()
                .filter(sd -> url.equals(sd.url()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Service description for URL " + url + " not found"))
                .id();
    }

    private boolean hasEndpoint(List<EndpointsAdminClient.EndpointView> list, String method, String path) {
        return list.stream().anyMatch(e -> method.equals(e.method()) && path.equals(e.path()));
    }
}
