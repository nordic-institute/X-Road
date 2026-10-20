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
package org.niis.xroad.e2e;

import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.Awaitility;
import org.niis.xroad.e2e.container.SsStackSetup;
import org.niis.xroad.e2e.parity.EndpointIds;
import org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Security Server admin REST API access: session login, service-description CRUD, access rights, the
 * client lifecycle, and service lookup, plus the external RPC CA and op-monitor CA endpoints.
 */
@Slf4j
@UtilityClass
class AdminApi {

    private static final String ADMIN_USERNAME = "xrd";
    private static final String ADMIN_PASSWORD = "secret123!";
    private static final int HTTP_OK = 200;
    private static final int HTTP_CREATED = 201;
    private static final int HTTP_NO_CONTENT = 204;
    private static final int HTTP_NOT_FOUND = 404;
    private static final int HTTP_CONFLICT = 409;
    private static final int HTTP_LAST_SUCCESS = 299;
    private static final int CLIENT_ID_PARTS = 4;
    private static final String REGISTERED_STATUS = "REGISTERED";
    private static final Duration REGISTRATION_POLL_TIMEOUT = Duration.ofMinutes(3);
    private static final Duration REGISTRATION_POLL_INTERVAL = Duration.ofSeconds(3);

    /** The mapped {@code https://host:port} of one environment's admin UI container. */
    static String adminBaseUrl(E2eEnvironment env, String envName) {
        var mapping = env.getContainerMapping(envName, SsStackSetup.UI, SsStackSetup.Port.UI);
        return "https://%s:%s".formatted(mapping.host(), mapping.port());
    }

    /** Logs in as the default admin user, failing the test if the form login does not return 200. */
    static AdminSession login(String baseUrl) {
        var response = RestAssuredFactory.given()
                .formParam("username", ADMIN_USERNAME)
                .formParam("password", ADMIN_PASSWORD)
                .post(baseUrl + "/login");
        assertThat(response.getStatusCode()).as("login to %s", baseUrl).isEqualTo(HTTP_OK);
        return new AdminSession(response.getCookies(), response.getCookie("XSRF-TOKEN"));
    }

    /** A request spec carrying the session's cookies and XSRF header. */
    static RequestSpecification authed(AdminSession session) {
        return RestAssuredFactory.given()
                .cookies(session.cookies())
                .header("X-XSRF-TOKEN", session.xsrfToken());
    }

    /** Finds the id of the service description backing {@code clientId}'s {@code serviceCode} service. */
    static String findServiceDescriptionId(String ss0BaseUrl, AdminSession ss0, String clientId, String serviceCode) {
        var response = authed(ss0).get(ss0BaseUrl + "/api/v1/clients/" + clientId + "/service-descriptions");
        assertThat(response.getStatusCode()).as("list service descriptions for %s", clientId).isEqualTo(HTTP_OK);

        var id = response.jsonPath().getString(
                "find { it.services.find { s -> s.service_code == '" + serviceCode + "' } != null }.id");
        assertThat(id)
                .as("a service description for %s exposing service code %s", clientId, serviceCode)
                .isNotBlank();
        return id;
    }

    /**
     * Enables the service description. {@code ServiceDescriptionService.toggleServices} has no
     * already-enabled check and unconditionally flips the disabled flag, so calling it again on an
     * already-enabled description is a plain, idempotent 200.
     */
    static void enableServiceDescription(String ss0BaseUrl, AdminSession ss0, String serviceDescriptionId) {
        var response = authed(ss0).put(ss0BaseUrl + "/api/v1/service-descriptions/" + serviceDescriptionId + "/enable");
        assertThat(response.getStatusCode())
                .as("enable service description %s", serviceDescriptionId)
                .isEqualTo(HTTP_OK);
    }

    /** Disables the service description, attaching the operator's notice unless it is {@code null}. */
    static void disableServiceDescription(String ss0BaseUrl, AdminSession ss0, String serviceDescriptionId, String notice) {
        var request = authed(ss0);
        if (notice != null) {
            request = request.header("Content-Type", "application/json")
                    .body("""
                            {"disabled_notice": "%s"}
                            """.formatted(notice));
        }
        var response = request.put(ss0BaseUrl + "/api/v1/service-descriptions/" + serviceDescriptionId + "/disable");
        assertThat(response.getStatusCode())
                .as("disable service description %s", serviceDescriptionId)
                .isBetween(HTTP_OK, HTTP_LAST_SUCCESS);
    }

    /**
     * Renames the service code of a REST service description. The description keeps its backend URL and the
     * access rights of the service follow the new code.
     */
    static void renameRestServiceCode(String ss0BaseUrl, AdminSession ss0, String serviceDescriptionId,
                                      String backendUrl, String restServiceCode, String newRestServiceCode) {
        var body = """
                {
                  "url": "%s",
                  "type": "REST",
                  "rest_service_code": "%s",
                  "new_rest_service_code": "%s"
                }
                """.formatted(backendUrl, restServiceCode, newRestServiceCode);
        var response = authed(ss0)
                .header("Content-Type", "application/json")
                .body(body)
                .patch(ss0BaseUrl + "/api/v1/service-descriptions/" + serviceDescriptionId);
        assertThat(response.getStatusCode())
                .as("rename service %s of service description %s to %s: %s", restServiceCode, serviceDescriptionId,
                        newRestServiceCode, response.getBody().asString())
                .isEqualTo(HTTP_OK);
    }

    /**
     * Deletes the service description outright, tolerating a rerun where a prior, interrupted attempt
     * already removed it.
     */
    static void deleteServiceDescription(String ss0BaseUrl, AdminSession ss0, String serviceDescriptionId) {
        var response = authed(ss0).delete(ss0BaseUrl + "/api/v1/service-descriptions/" + serviceDescriptionId);
        assertThat(response.getStatusCode())
                .as("delete service description %s (204 first attempt, 404 if a prior attempt already removed it)",
                        serviceDescriptionId)
                .isIn(HTTP_NO_CONTENT, HTTP_NOT_FOUND);
    }

    /** Deletes the service description and returns the HTTP status of the call without asserting it. */
    static int requestServiceDescriptionDeletion(String ss0BaseUrl, AdminSession ss0, String serviceDescriptionId) {
        return authed(ss0).delete(ss0BaseUrl + "/api/v1/service-descriptions/" + serviceDescriptionId).getStatusCode();
    }

    /** Looks up the backend URL a service is currently configured with. */
    static String discoverBackendUrl(String ss0BaseUrl, AdminSession ss0, String serviceId) {
        var response = authed(ss0).get(ss0BaseUrl + "/api/v1/services/" + serviceId);
        assertThat(response.getStatusCode()).as("look up service %s", serviceId).isEqualTo(HTTP_OK);
        return response.jsonPath().getString("url");
    }

    /**
     * Adds a REST service description, tolerating a warm rerun where one with {@code restServiceCode}
     * already exists on this client: {@code ServiceDescriptionService} rejects the duplicate service code
     * with {@code ServiceCodeAlreadyExistsException} (checked ahead of the URL-duplicate case, so a rerun
     * always hits this one), a {@code ConflictException} mapped to 409. Unlike a deterministic client id,
     * the service description's id is not deterministic, so a 409 falls back to
     * {@link #findExistingServiceDescriptionId} instead of returning one.
     */
    static String addRestServiceDescription(String ss0BaseUrl, AdminSession ss0, String clientId, String backendUrl,
                                            String restServiceCode) {
        var body = """
                {
                  "url": "%s",
                  "type": "REST",
                  "rest_service_code": "%s"
                }
                """.formatted(backendUrl, restServiceCode);
        var response = authed(ss0)
                .header("Content-Type", "application/json")
                .body(body)
                .post(ss0BaseUrl + "/api/v1/clients/" + clientId + "/service-descriptions");
        if (response.getStatusCode() == HTTP_CONFLICT) {
            log.info("REST service description '{}' already exists for {}; looking up its id instead of adding it again",
                    restServiceCode, clientId);
            return findExistingServiceDescriptionId(ss0BaseUrl, ss0, clientId, backendUrl);
        }
        assertThat(response.getStatusCode()).as("add REST service description for %s", clientId).isEqualTo(HTTP_CREATED);
        return response.jsonPath().getString("id");
    }

    /** Adds a WSDL service description from {@code wsdlUrl}, ignoring parser warnings, and returns its id. */
    static String addWsdlServiceDescription(String ss0BaseUrl, AdminSession ss0, String clientId, String wsdlUrl) {
        var body = """
                {
                  "url": "%s",
                  "type": "WSDL",
                  "ignore_warnings": true
                }
                """.formatted(wsdlUrl);
        var response = authed(ss0)
                .header("Content-Type", "application/json")
                .body(body)
                .post(ss0BaseUrl + "/api/v1/clients/" + clientId + "/service-descriptions");
        assertThat(response.getStatusCode())
                .as("add WSDL service description %s for %s: %s", wsdlUrl, clientId, response.getBody().asString())
                .isEqualTo(HTTP_CREATED);
        return response.jsonPath().getString("id");
    }

    /**
     * Adds an OpenAPI 3 service description from {@code specUrl} as REST service {@code restServiceCode},
     * ignoring parser warnings, and returns its id.
     */
    static String addOpenApiServiceDescription(String ss0BaseUrl, AdminSession ss0, String clientId, String specUrl,
                                               String restServiceCode) {
        var response = authed(ss0)
                .header("Content-Type", "application/json")
                .body(openApiDescriptionBody(specUrl, restServiceCode))
                .post(ss0BaseUrl + "/api/v1/clients/" + clientId + "/service-descriptions");
        assertThat(response.getStatusCode())
                .as("add OPENAPI3 service description %s for %s: %s", specUrl, clientId,
                        response.getBody().asString())
                .isEqualTo(HTTP_CREATED);
        return response.jsonPath().getString("id");
    }

    /**
     * Points the OpenAPI 3 service description at {@code specUrl}, ignoring parser warnings. The parser then
     * re-reads the description: generated endpoints are added or removed and manual endpoints that equal a
     * generated one become generated, keeping their access rights.
     */
    static void updateOpenApiServiceDescription(String ss0BaseUrl, AdminSession ss0, String serviceDescriptionId,
                                                String specUrl, String restServiceCode) {
        var response = authed(ss0)
                .header("Content-Type", "application/json")
                .body(openApiDescriptionBody(specUrl, restServiceCode))
                .patch(ss0BaseUrl + "/api/v1/service-descriptions/" + serviceDescriptionId);
        assertThat(response.getStatusCode())
                .as("update OPENAPI3 service description %s to %s: %s", serviceDescriptionId, specUrl,
                        response.getBody().asString())
                .isEqualTo(HTTP_OK);
    }

    /** Re-reads the service description from its URL, ignoring parser warnings. */
    static void refreshServiceDescription(String ss0BaseUrl, AdminSession ss0, String serviceDescriptionId) {
        var response = authed(ss0)
                .header("Content-Type", "application/json")
                .body("{\"ignore_warnings\": true}")
                .put(ss0BaseUrl + "/api/v1/service-descriptions/" + serviceDescriptionId + "/refresh");
        assertThat(response.getStatusCode())
                .as("refresh service description %s: %s", serviceDescriptionId, response.getBody().asString())
                .isEqualTo(HTTP_OK);
    }

    private static String openApiDescriptionBody(String specUrl, String restServiceCode) {
        return """
                {
                  "url": "%s",
                  "type": "OPENAPI3",
                  "rest_service_code": "%s",
                  "ignore_warnings": true
                }
                """.formatted(specUrl, restServiceCode);
    }

    /**
     * Recovers the service description id a 409 from {@link #addRestServiceDescription} could not return:
     * there is no get-by-url endpoint, so the client's service descriptions are listed and matched by the
     * backend URL the caller always uses for that service code.
     */
    static String findExistingServiceDescriptionId(String ss0BaseUrl, AdminSession ss0, String clientId, String backendUrl) {
        var id = findServiceDescriptionIdByUrl(ss0BaseUrl, ss0, clientId, backendUrl);
        assertThat(id)
                .as("an existing service description for %s with backend url %s", clientId, backendUrl)
                .isPresent();
        return id.orElseThrow();
    }

    /** Looks up the id of the client's service description with the given backend URL, if there is one. */
    static Optional<String> findServiceDescriptionIdByUrl(String ss0BaseUrl, AdminSession ss0, String clientId,
                                                         String backendUrl) {
        var response = authed(ss0).get(ss0BaseUrl + "/api/v1/clients/" + clientId + "/service-descriptions");
        assertThat(response.getStatusCode()).as("list service descriptions for %s", clientId).isEqualTo(HTTP_OK);

        return Optional.ofNullable(response.jsonPath().getString("find { it.url == '" + backendUrl + "' }.id"))
                .filter(id -> !id.isBlank());
    }

    /**
     * Grants a consumer access to a provider client's service. Tolerates 409 so this stays safe to run
     * against a substrate where the grant already exists.
     */
    static void grantConsumerAccessRights(String ss0BaseUrl, AdminSession ss0, String providerClientId,
                                          String consumerClientId, String serviceCode) {
        var body = """
                {
                  "items": [
                    { "service_code": "%s" }
                  ]
                }
                """.formatted(serviceCode);
        var response = authed(ss0)
                .header("Content-Type", "application/json")
                .body(body)
                .post(ss0BaseUrl + "/api/v1/clients/" + providerClientId + "/service-clients/" + consumerClientId + "/access-rights");
        assertThat(response.getStatusCode())
                .as("grant %s access to %s's %s service", consumerClientId, providerClientId, serviceCode)
                .isIn(HTTP_CREATED, HTTP_CONFLICT);
    }

    /** Revokes a consumer's access to a provider client's service, with its endpoint grants of that service. */
    static void revokeConsumerAccessRights(String ss0BaseUrl, AdminSession ss0, String providerClientId,
                                           String consumerClientId, String serviceCode) {
        var body = """
                {
                  "items": [
                    { "service_code": "%s" }
                  ]
                }
                """.formatted(serviceCode);
        var response = authed(ss0)
                .header("Content-Type", "application/json")
                .body(body)
                .post(ss0BaseUrl + "/api/v1/clients/" + providerClientId + "/service-clients/" + consumerClientId
                        + "/access-rights/delete");
        assertThat(response.getStatusCode())
                .as("revoke %s access to %s's %s service: %s", consumerClientId, providerClientId, serviceCode,
                        response.getBody().asString())
                .isEqualTo(HTTP_NO_CONTENT);
    }

    /**
     * Creates an endpoint on the REST service {@code serviceCode} of the service description and returns its id.
     * The service id is resolved from the description, because it is the path key of the endpoint call. The
     * creation response carries no endpoint id, so the id is read from the service's endpoint list afterwards.
     */
    static String addEndpoint(String baseUrl, AdminSession session, String serviceDescriptionId, String serviceCode,
                              String method, String path) {
        var serviceId = findServiceId(baseUrl, session, serviceDescriptionId, serviceCode);
        var body = """
                {
                  "service_code": "%s",
                  "method": "%s",
                  "path": "%s"
                }
                """.formatted(serviceCode, method, path);
        var response = authed(session)
                .header("Content-Type", "application/json")
                .body(body)
                .post(baseUrl + "/api/v1/services/" + serviceId + "/endpoints");
        assertThat(response.getStatusCode())
                .as("add endpoint %s %s to %s: %s", method, path, serviceId, response.getBody().asString())
                .isEqualTo(HTTP_CREATED);
        return findEndpointId(baseUrl, session, serviceDescriptionId, serviceCode, method, path);
    }

    /** Finds the id of the endpoint of the description's service {@code serviceCode} with the method and path. */
    static String findEndpointId(String baseUrl, AdminSession session, String serviceDescriptionId,
                                 String serviceCode, String method, String path) {
        var serviceId = findServiceId(baseUrl, session, serviceDescriptionId, serviceCode);
        var service = authed(session).get(baseUrl + "/api/v1/services/" + serviceId);
        assertThat(service.getStatusCode()).as("get service %s", serviceId).isEqualTo(HTTP_OK);
        var id = EndpointIds.find(service.jsonPath().getList("endpoints"), method, path);
        assertThat(id).as("endpoint %s %s in the endpoints of service %s", method, path, serviceId).isPresent();
        return id.orElseThrow();
    }

    private static String findServiceId(String baseUrl, AdminSession session, String serviceDescriptionId,
                                        String serviceCode) {
        var services = authed(session).get(baseUrl + "/api/v1/service-descriptions/" + serviceDescriptionId + "/services");
        assertThat(services.getStatusCode())
                .as("list services of service description %s", serviceDescriptionId)
                .isEqualTo(HTTP_OK);
        var serviceId = services.jsonPath().getString("find { it.service_code == '" + serviceCode + "' }.id");
        assertThat(serviceId)
                .as("a service with code %s in service description %s", serviceCode, serviceDescriptionId)
                .isNotBlank();
        return serviceId;
    }

    /** Changes the method and path of the endpoint. */
    static void updateEndpoint(String baseUrl, AdminSession session, String endpointId, String method, String path) {
        var body = """
                {
                  "method": "%s",
                  "path": "%s"
                }
                """.formatted(method, path);
        var response = authed(session)
                .header("Content-Type", "application/json")
                .body(body)
                .patch(baseUrl + "/api/v1/endpoints/" + endpointId);
        assertThat(response.getStatusCode())
                .as("update endpoint %s to %s %s: %s", endpointId, method, path, response.getBody().asString())
                .isEqualTo(HTTP_OK);
    }

    /** Grants the consumer access to the endpoint. */
    static void grantEndpointAccess(String baseUrl, AdminSession session, String endpointId, String consumerClientId) {
        var body = """
                {
                  "items": [
                    { "id": "%s" }
                  ]
                }
                """.formatted(consumerClientId);
        var response = authed(session)
                .header("Content-Type", "application/json")
                .body(body)
                .post(baseUrl + "/api/v1/endpoints/" + endpointId + "/service-clients");
        assertThat(response.getStatusCode())
                .as("grant %s access to endpoint %s: %s", consumerClientId, endpointId, response.getBody().asString())
                .isEqualTo(HTTP_CREATED);
    }

    /** Deletes the endpoint and returns the HTTP status of the call without asserting it. */
    static int requestEndpointDeletion(String baseUrl, AdminSession session, String endpointId) {
        return authed(session).delete(baseUrl + "/api/v1/endpoints/" + endpointId).getStatusCode();
    }

    /** Creates a local group of the owner client and returns its id. */
    static String createLocalGroup(String baseUrl, AdminSession session, String ownerClientId, String code) {
        var body = """
                {
                  "code": "%s",
                  "description": "acl parity"
                }
                """.formatted(code);
        var response = authed(session)
                .header("Content-Type", "application/json")
                .body(body)
                .post(baseUrl + "/api/v1/clients/" + ownerClientId + "/local-groups");
        assertThat(response.getStatusCode())
                .as("create local group %s of %s: %s", code, ownerClientId, response.getBody().asString())
                .isEqualTo(HTTP_CREATED);
        return response.jsonPath().getString("id");
    }

    /** Adds the member or subsystem to the local group. */
    static void addLocalGroupMember(String baseUrl, AdminSession session, String groupId, String memberId) {
        var response = authed(session)
                .header("Content-Type", "application/json")
                .body(localGroupMemberBody(memberId))
                .post(baseUrl + "/api/v1/local-groups/" + groupId + "/members");
        assertThat(response.getStatusCode())
                .as("add %s to local group %s: %s", memberId, groupId, response.getBody().asString())
                .isEqualTo(HTTP_CREATED);
    }

    /** Removes the member or subsystem from the local group. */
    static void removeLocalGroupMember(String baseUrl, AdminSession session, String groupId, String memberId) {
        var response = authed(session)
                .header("Content-Type", "application/json")
                .body(localGroupMemberBody(memberId))
                .post(baseUrl + "/api/v1/local-groups/" + groupId + "/members/delete");
        assertThat(response.getStatusCode())
                .as("remove %s from local group %s: %s", memberId, groupId, response.getBody().asString())
                .isEqualTo(HTTP_NO_CONTENT);
    }

    /** Deletes the local group and returns the HTTP status of the call without asserting it. */
    static int requestLocalGroupDeletion(String baseUrl, AdminSession session, String groupId) {
        return authed(session).delete(baseUrl + "/api/v1/local-groups/" + groupId).getStatusCode();
    }

    private static String localGroupMemberBody(String memberId) {
        return """
                {
                  "items": [ "%s" ]
                }
                """.formatted(memberId);
    }

    /**
     * Adds the subsystem {@code clientId} ({@code INSTANCE:CLASS:CODE:SUBSYSTEM}) as a local client,
     * tolerating a warm rerun where it already exists: {@code ClientService} rejects a duplicate with
     * {@code ClientAlreadyExistsException}, a {@code ConflictException} mapped to 409. The client id is
     * deterministic, so it is returned on either status.
     */
    static String addLocalClient(String baseUrl, AdminSession session, String clientId) {
        var parts = clientId.split(":", -1);
        assertThat(parts).as("subsystem client id %s", clientId).hasSize(CLIENT_ID_PARTS);
        var body = """
                {
                  "ignore_warnings": true,
                  "client": {
                    "member_class": "%s",
                    "member_code": "%s",
                    "subsystem_code": "%s",
                    "connection_type": "HTTP"
                  }
                }
                """.formatted(parts[1], parts[2], parts[parts.length - 1]);
        var response = authed(session)
                .header("Content-Type", "application/json")
                .body(body)
                .post(baseUrl + "/api/v1/clients");
        assertThat(response.getStatusCode())
                .as("add local client %s (201 first run, 409 if it already exists on a warm rerun)", clientId)
                .isIn(HTTP_CREATED, HTTP_CONFLICT);
        return clientId;
    }

    /** Sets the client's internal connection type ({@code HTTP}, {@code HTTPS} or {@code HTTPS_NO_AUTH}). */
    static void setConnectionType(String baseUrl, AdminSession session, String clientId, String connectionType) {
        var response = authed(session)
                .header("Content-Type", "application/json")
                .body("{\"connection_type\": \"%s\"}".formatted(connectionType))
                .patch(baseUrl + "/api/v1/clients/" + clientId);
        assertThat(response.getStatusCode()).as("set connection type of %s to %s", clientId, connectionType).isEqualTo(HTTP_OK);
    }
    /**
     * Submits the client registration request, tolerating a warm rerun where the client is already past
     * {@code SAVED} status: {@code ClientService.registerClient} rejects that with
     * {@code ActionNotPossibleException}, a {@code ConflictException} mapped to 409. Either status leaves
     * {@link #awaitClientRegistered} to confirm the actual outcome.
     */
    static void registerClient(String baseUrl, AdminSession session, String clientId) {
        var response = authed(session).put(baseUrl + "/api/v1/clients/" + clientId + "/register");
        assertThat(response.getStatusCode())
                .as("submit client registration for %s (204 first run, 409 if it is no longer SAVED on a warm rerun)", clientId)
                .isIn(HTTP_NO_CONTENT, HTTP_CONFLICT);
    }

    /**
     * Unregisters the client. {@code ClientService.unregisterClient} flips the client straight to
     * {@code DELINPROG} in the same call (no Central Server approval to wait for, unlike registration), so
     * {@link #deleteClient} can follow immediately.
     */
    static void unregisterClient(String baseUrl, AdminSession session, String clientId) {
        var response = authed(session).put(baseUrl + "/api/v1/clients/" + clientId + "/unregister");
        assertThat(response.getStatusCode())
                .as("unregister %s (204 first run, 409 if a prior run already unregistered it)", clientId)
                .isIn(HTTP_NO_CONTENT, HTTP_CONFLICT);
    }

    /** Deletes the unregistered client, tolerating a rerun where a prior attempt already removed it. */
    static void deleteClient(String baseUrl, AdminSession session, String clientId) {
        var response = authed(session).delete(baseUrl + "/api/v1/clients/" + clientId);
        assertThat(response.getStatusCode())
                .as("delete %s (204 first run, 404 if a prior run already deleted it)", clientId)
                .isIn(HTTP_NO_CONTENT, HTTP_NOT_FOUND);
    }

    /** Waits until the Security Server reports {@code clientId} as REGISTERED, see {@link #awaitClientsRegistered}. */
    static void awaitClientRegistered(String baseUrl, AdminSession session, String csBaseUrl, AdminSession cs,
                                      String clientId) {
        awaitClientsRegistered(baseUrl, session, csBaseUrl, cs, List.of(clientId), REGISTRATION_POLL_TIMEOUT);
    }

    /**
     * Waits until the Security Server reports every client in {@code clientIds} as REGISTERED, approving the
     * Central Server's pending registration requests within the same loop. Environments differ: k8s
     * auto-approves, so a request never surfaces as WAITING; LXD and compose require an explicit approval,
     * and the request may take a moment to appear as WAITING, then several globalconf-distribution cycles to
     * propagate back as REGISTERED. Approving on each tick, rather than once up front, covers all of them.
     */
    static void awaitClientsRegistered(String baseUrl, AdminSession session, String csBaseUrl, AdminSession cs,
                                       List<String> clientIds, Duration timeout) {
        Awaitility.await()
                .pollInterval(REGISTRATION_POLL_INTERVAL)
                .timeout(timeout)
                .untilAsserted(() -> {
                    CentralServerApi.approvePendingClientRegistrations(csBaseUrl, cs, clientIds);
                    for (var clientId : clientIds) {
                        var response = authed(session).get(baseUrl + "/api/v1/clients/" + clientId);
                        assertThat(response.getStatusCode()).as("GET /clients/%s", clientId).isEqualTo(HTTP_OK);
                        assertThat(response.jsonPath().getString("status"))
                                .as("status of client %s", clientId)
                                .isEqualTo(REGISTERED_STATUS);
                    }
                });
    }

    /** Reads the external RPC CA details. */
    static Response getExternalRpcCa(String baseUrl, AdminSession session) {
        return authed(session).get(baseUrl + "/api/v1/system/external-rpc-ca");
    }

    /** Downloads the external RPC CA as a PEM file. */
    static Response downloadExternalRpcCa(String baseUrl, AdminSession session) {
        return authed(session).get(baseUrl + "/api/v1/system/external-rpc-ca/download");
    }

    /** Reads the op-monitor CA details; the API answers 204 when none is stored. */
    static Response getOpMonitorCa(String baseUrl, AdminSession session) {
        return authed(session).get(baseUrl + "/api/v1/system/op-monitor-ca");
    }

    /** Uploads {@code certificateFile} (PEM or DER bytes) as the op-monitor CA. */
    static Response uploadOpMonitorCa(String baseUrl, AdminSession session, byte[] certificateFile) {
        return authed(session)
                .multiPart("certificate", "op-monitor-ca.pem", certificateFile, "application/octet-stream")
                .post(baseUrl + "/api/v1/system/op-monitor-ca");
    }

    /** Deletes the op-monitor CA; the API answers 204 also when none is stored. */
    static Response deleteOpMonitorCa(String baseUrl, AdminSession session) {
        return authed(session).delete(baseUrl + "/api/v1/system/op-monitor-ca");
    }

    /** One logged-in admin session: the cookies to replay and the XSRF token to echo back. */
    record AdminSession(Map<String, String> cookies, String xsrfToken) {
    }
}
