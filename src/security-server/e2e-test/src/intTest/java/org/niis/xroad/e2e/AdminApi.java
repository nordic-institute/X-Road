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

import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import lombok.experimental.UtilityClass;
import org.niis.xroad.e2e.container.SsStackSetup;
import org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Form login against a security server's admin API, and the session it hands back. Every admin-API
 * scenario needs the same three steps — resolve the UI container's mapped address, POST the login
 * form, then replay the session cookies plus the XSRF header on each request — so they live here
 * rather than once per test class.
 */
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

    /** ss0's own {@code TestService} subsystem and its self-accessible {@code mock1} REST service. */
    static final String MOCK1_CLIENT_ID = "DEV:COM:1234:TestService";
    static final String MOCK1_SERVICE_CODE = "mock1";
    static final String MOCK1_SERVICE_ID = MOCK1_CLIENT_ID + ":" + MOCK1_SERVICE_CODE;
    static final String MOCK1_X_ROAD_CLIENT = "DEV/COM/1234/TestService";
    static final String MOCK1_SERVICE_PATH = "/r1/DEV/COM/1234/TestService/mock1";
    static final String MOCK1_REQUEST_BODY = """
            {"data": 1.0, "service": "random"}
            """;

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
                .isBetween(HTTP_OK, HTTP_LAST_SUCCESS);
    }

    /** Disables the service description, attaching the operator's notice. */
    static void disableServiceDescription(String ss0BaseUrl, AdminSession ss0, String serviceDescriptionId, String notice) {
        var body = """
                {"disabled_notice": "%s"}
                """.formatted(notice);
        var response = authed(ss0)
                .header("Content-Type", "application/json")
                .body(body)
                .put(ss0BaseUrl + "/api/v1/service-descriptions/" + serviceDescriptionId + "/disable");
        assertThat(response.getStatusCode())
                .as("disable service description %s", serviceDescriptionId)
                .isBetween(HTTP_OK, HTTP_LAST_SUCCESS);
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
            return findExistingServiceDescriptionId(ss0BaseUrl, ss0, clientId, backendUrl);
        }
        assertThat(response.getStatusCode()).as("add REST service description for %s", clientId).isEqualTo(HTTP_CREATED);
        return response.jsonPath().getString("id");
    }

    /**
     * Recovers the service description id a 409 from {@link #addRestServiceDescription} could not return:
     * there is no get-by-url endpoint, so the client's service descriptions are listed and matched by the
     * backend URL the caller always uses for that service code.
     */
    static String findExistingServiceDescriptionId(String ss0BaseUrl, AdminSession ss0, String clientId, String backendUrl) {
        var response = authed(ss0).get(ss0BaseUrl + "/api/v1/clients/" + clientId + "/service-descriptions");
        assertThat(response.getStatusCode()).as("list service descriptions for %s", clientId).isEqualTo(HTTP_OK);

        var id = response.jsonPath().getString("find { it.url == '" + backendUrl + "' }.id");
        assertThat(id)
                .as("an existing service description for %s with backend url %s", clientId, backendUrl)
                .isNotBlank();
        return id;
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

    /** Sends a REST POST as {@code xRoadClient} to {@code servicePath} on {@code envName}'s mapped proxy. */
    static ValidatableResponse callService(E2eEnvironment env, String envName, String xRoadClient, String servicePath) {
        var mapping = env.getContainerMapping(envName, SsStackSetup.PROXY, SsStackSetup.Port.PROXY);
        return RestAssuredFactory.given()
                .body(MOCK1_REQUEST_BODY)
                .header("Content-Type", "application/json")
                .header("x-road-client", xRoadClient)
                .post("http://%s:%s%s".formatted(mapping.host(), mapping.port(), servicePath))
                .then();
    }

    /** Sends the {@code mock1} REST POST as {@link #MOCK1_X_ROAD_CLIENT} to {@code envName}'s mapped proxy. */
    static ValidatableResponse callMock1(E2eEnvironment env, String envName) {
        return callService(env, envName, MOCK1_X_ROAD_CLIENT, MOCK1_SERVICE_PATH);
    }

    /** One logged-in admin session: the cookies to replay and the XSRF token to echo back. */
    record AdminSession(Map<String, String> cookies, String xsrfToken) {
    }
}
