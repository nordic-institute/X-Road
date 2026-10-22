/*
 * The MIT License
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

import io.restassured.http.Method;
import org.niis.xroad.e2e.AdminApi.AdminSession;
import org.niis.xroad.e2e.parity.acl.AclScenario.WaitClass;
import org.niis.xroad.e2e.parity.acl.AdminGateway;
import org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static java.net.HttpURLConnection.HTTP_CREATED;
import static java.net.HttpURLConnection.HTTP_NO_CONTENT;
import static java.net.HttpURLConnection.HTTP_OK;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.niis.xroad.e2e.AdminApi.authed;
import static org.niis.xroad.e2e.AdminApi.sendJson;

/** The provider Security Server's admin API and the Central Server's global groups. */
final class AdminApiGateway implements AdminGateway {

    private static final Duration GLOBALCONF_POLL_INTERVAL = Duration.ofSeconds(3);

    private final String baseUrl;
    private final AdminSession session;
    private final String backendOrigin;
    private final String csBaseUrl;
    private final AdminSession csSession;

    /**
     * @param backendOrigin the origin the backend answers on, as the test reaches it
     * @param csBaseUrl      the Central Server admin UI, where global groups are kept
     * @param csSession      the logged-in Central Server admin session
     */
    AdminApiGateway(String baseUrl, AdminSession session, String backendOrigin, String csBaseUrl,
                    AdminSession csSession) {
        this.baseUrl = baseUrl;
        this.session = session;
        this.backendOrigin = backendOrigin;
        this.csBaseUrl = csBaseUrl;
        this.csSession = csSession;
    }

    @Override
    public String addRestDescription(String providerClientId, String backendUrl, String serviceCode) {
        return AdminApi.addRestServiceDescription(baseUrl, session, providerClientId, backendUrl, serviceCode);
    }

    @Override
    public String addWsdlDescription(String providerClientId, String wsdlUrl) {
        var body = """
                {
                  "url": "%s",
                  "type": "WSDL",
                  "ignore_warnings": true
                }
                """.formatted(wsdlUrl);
        var response = sendJson(session, Method.POST,
                baseUrl + "/api/v1/clients/" + providerClientId + "/service-descriptions", body, HTTP_CREATED,
                "add WSDL service description %s for %s".formatted(wsdlUrl, providerClientId));
        return response.jsonPath().getString("id");
    }

    @Override
    public String addOpenApiDescription(String providerClientId, String specUrl, String serviceCode) {
        var response = sendJson(session, Method.POST,
                baseUrl + "/api/v1/clients/" + providerClientId + "/service-descriptions",
                openApiDescriptionBody(specUrl, serviceCode), HTTP_CREATED,
                "add OPENAPI3 service description %s for %s".formatted(specUrl, providerClientId));
        return response.jsonPath().getString("id");
    }

    @Override
    public void updateOpenApiDescription(String descriptionId, String specUrl, String serviceCode) {
        sendJson(session, Method.PATCH, baseUrl + "/api/v1/service-descriptions/" + descriptionId,
                openApiDescriptionBody(specUrl, serviceCode), HTTP_OK,
                "update OPENAPI3 service description %s to %s".formatted(descriptionId, specUrl));
    }

    @Override
    public void enableDescription(String descriptionId) {
        AdminApi.enableServiceDescription(baseUrl, session, descriptionId);
    }

    @Override
    public void disableDescription(String descriptionId, String notice) {
        AdminApi.disableServiceDescription(baseUrl, session, descriptionId, notice);
    }

    @Override
    public void renameRestService(String descriptionId, String backendUrl, String serviceCode,
                                  String newServiceCode) {
        var body = """
                {
                  "url": "%s",
                  "type": "REST",
                  "rest_service_code": "%s",
                  "new_rest_service_code": "%s"
                }
                """.formatted(backendUrl, serviceCode, newServiceCode);
        sendJson(session, Method.PATCH, baseUrl + "/api/v1/service-descriptions/" + descriptionId, body, HTTP_OK,
                "rename service %s of service description %s to %s".formatted(serviceCode, descriptionId,
                        newServiceCode));
    }

    @Override
    public boolean grantAccess(String providerClientId, String consumerClientId, String serviceCode) {
        return AdminApi.grantConsumerAccessRights(baseUrl, session, providerClientId, consumerClientId, serviceCode);
    }

    @Override
    public int requestAccessGrant(String providerClientId, String subjectId, String serviceCode) {
        return AdminApi.requestConsumerAccessGrant(baseUrl, session, providerClientId, subjectId, serviceCode);
    }

    @Override
    public void revokeAccess(String providerClientId, String consumerClientId, String serviceCode) {
        var body = """
                {
                  "items": [
                    { "service_code": "%s" }
                  ]
                }
                """.formatted(serviceCode);
        sendJson(session, Method.POST, baseUrl + "/api/v1/clients/" + providerClientId + "/service-clients/"
                + consumerClientId + "/access-rights/delete", body, HTTP_NO_CONTENT,
                "revoke %s access to %s's %s service".formatted(consumerClientId, providerClientId, serviceCode));
    }

    @Override
    public void refreshDescription(String descriptionId) {
        sendJson(session, Method.PUT, baseUrl + "/api/v1/service-descriptions/" + descriptionId + "/refresh",
                "{\"ignore_warnings\": true}", HTTP_OK, "refresh service description " + descriptionId);
    }

    @Override
    public int swapBackendSpec(String urlPath) {
        return requestBackend(urlPath);
    }

    @Override
    public String addEndpoint(String descriptionId, String serviceCode, String method, String path) {
        var serviceId = findServiceId(descriptionId, serviceCode);
        var body = """
                {
                  "service_code": "%s",
                  "method": "%s",
                  "path": "%s"
                }
                """.formatted(serviceCode, method, path);
        sendJson(session, Method.POST, baseUrl + "/api/v1/services/" + serviceId + "/endpoints", body, HTTP_CREATED,
                "add endpoint %s %s to %s".formatted(method, path, serviceId));
        return findEndpointId(descriptionId, serviceCode, method, path);
    }

    @Override
    public String findEndpointId(String descriptionId, String serviceCode, String method, String path) {
        var serviceId = findServiceId(descriptionId, serviceCode);
        var service = authed(session).get(baseUrl + "/api/v1/services/" + serviceId);
        assertThat(service.getStatusCode()).as("get service %s", serviceId).isEqualTo(HTTP_OK);
        List<Map<String, Object>> endpoints = service.jsonPath().getList("endpoints");
        var id = endpoints.stream()
                .filter(endpoint -> method.equals(endpoint.get("method")) && path.equals(endpoint.get("path")))
                .map(endpoint -> String.valueOf(endpoint.get("id")))
                .findFirst();
        assertThat(id).as("endpoint %s %s in the endpoints of service %s", method, path, serviceId).isPresent();
        return id.orElseThrow();
    }

    @Override
    public void updateEndpoint(String endpointId, String method, String path) {
        var body = """
                {
                  "method": "%s",
                  "path": "%s"
                }
                """.formatted(method, path);
        sendJson(session, Method.PATCH, baseUrl + "/api/v1/endpoints/" + endpointId, body, HTTP_OK,
                "update endpoint %s to %s %s".formatted(endpointId, method, path));
    }

    @Override
    public void grantEndpointAccess(String endpointId, String consumerClientId) {
        var body = """
                {
                  "items": [
                    { "id": "%s" }
                  ]
                }
                """.formatted(consumerClientId);
        sendJson(session, Method.POST, baseUrl + "/api/v1/endpoints/" + endpointId + "/service-clients", body,
                HTTP_CREATED, "grant %s access to endpoint %s".formatted(consumerClientId, endpointId));
    }

    @Override
    public int deleteEndpoint(String endpointId) {
        return authed(session).delete(baseUrl + "/api/v1/endpoints/" + endpointId).getStatusCode();
    }

    @Override
    public int deleteDescription(String descriptionId) {
        return authed(session).delete(baseUrl + "/api/v1/service-descriptions/" + descriptionId).getStatusCode();
    }

    @Override
    public String createLocalGroup(String ownerClientId, String code) {
        var body = """
                {
                  "code": "%s",
                  "description": "acl parity"
                }
                """.formatted(code);
        var response = sendJson(session, Method.POST, baseUrl + "/api/v1/clients/" + ownerClientId + "/local-groups",
                body, HTTP_CREATED, "create local group %s of %s".formatted(code, ownerClientId));
        return response.jsonPath().getString("id");
    }

    @Override
    public void addLocalGroupMember(String groupId, String memberId) {
        sendJson(session, Method.POST, baseUrl + "/api/v1/local-groups/" + groupId + "/members",
                localGroupMemberBody(memberId), HTTP_CREATED,
                "add %s to local group %s".formatted(memberId, groupId));
    }

    @Override
    public void removeLocalGroupMember(String groupId, String memberId) {
        sendJson(session, Method.POST, baseUrl + "/api/v1/local-groups/" + groupId + "/members/delete",
                localGroupMemberBody(memberId), HTTP_NO_CONTENT,
                "remove %s from local group %s".formatted(memberId, groupId));
    }

    @Override
    public int deleteLocalGroup(String groupId) {
        return authed(session).delete(baseUrl + "/api/v1/local-groups/" + groupId).getStatusCode();
    }

    @Override
    public void createGlobalGroup(String code) {
        CentralServerApi.createGlobalGroup(csBaseUrl, csSession, code);
    }

    @Override
    public void addGlobalGroupMember(String code, String memberId) {
        CentralServerApi.addGlobalGroupMember(csBaseUrl, csSession, code, memberId);
    }

    @Override
    public void removeGlobalGroupMember(String code, String memberId) {
        CentralServerApi.removeGlobalGroupMember(csBaseUrl, csSession, code, memberId);
    }

    @Override
    public int deleteGlobalGroup(String code) {
        return CentralServerApi.requestGlobalGroupDeletion(csBaseUrl, csSession, code);
    }

    @Override
    public void grantGlobalGroupAccess(String providerClientId, String groupId, String serviceCode) {
        awaitGroupKnown(() -> AdminApi.grantConsumerAccessRights(baseUrl, session, providerClientId, groupId,
                serviceCode));
    }

    @Override
    public void grantGlobalGroupEndpointAccess(String endpointId, String groupId) {
        awaitGroupKnown(() -> grantEndpointAccess(endpointId, groupId));
    }

    private static void awaitGroupKnown(Runnable grant) {
        await("the Security Server learns the global group through global configuration")
                .atMost(WaitClass.GLOBALCONF.timeout())
                .pollInterval(GLOBALCONF_POLL_INTERVAL)
                .untilAsserted(grant::run);
    }

    private String findServiceId(String descriptionId, String serviceCode) {
        var services = authed(session).get(baseUrl + "/api/v1/service-descriptions/" + descriptionId + "/services");
        assertThat(services.getStatusCode())
                .as("list services of service description %s", descriptionId)
                .isEqualTo(HTTP_OK);
        var serviceId = services.jsonPath().getString("find { it.service_code == '" + serviceCode + "' }.id");
        assertThat(serviceId)
                .as("a service with code %s in service description %s", serviceCode, descriptionId)
                .isNotBlank();
        return serviceId;
    }

    private static String openApiDescriptionBody(String specUrl, String serviceCode) {
        return """
                {
                  "url": "%s",
                  "type": "OPENAPI3",
                  "rest_service_code": "%s",
                  "ignore_warnings": true
                }
                """.formatted(specUrl, serviceCode);
    }

    private static String localGroupMemberBody(String memberId) {
        return """
                {
                  "items": [ "%s" ]
                }
                """.formatted(memberId);
    }

    private int requestBackend(String urlPath) {
        return RestAssuredFactory.given().get(backendOrigin + urlPath).getStatusCode();
    }
}
