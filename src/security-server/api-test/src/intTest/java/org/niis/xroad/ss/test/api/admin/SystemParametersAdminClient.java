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
package org.niis.xroad.ss.test.api.admin;

import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;
import org.niis.xroad.securityserver.restapi.openapi.model.SecurityServerAddressDto;
import org.niis.xroad.securityserver.restapi.openapi.model.SecurityServerPropertyUpdateDto;
import org.niis.xroad.securityserver.restapi.openapi.model.TimestampingServiceDto;

import java.util.List;
import java.util.Map;

/**
 * RestAssured client for system-parameter admin API resources: server address, timestamping services,
 * approved certificate authorities, and configurable properties.
 */
@SuppressWarnings("checkstyle:magicnumber")
public class SystemParametersAdminClient {

    private final AdminApiSession session;

    public SystemParametersAdminClient(AdminApiSession session) {
        this.session = session;
    }

    /**
     * Requests a Security Server address change. Returns 202 on submission or 400/500 when
     * the management request to the Central Server fails.
     */
    public ValidatableResponse changeServerAddress(String address) {
        return session.given()
                .contentType(ContentType.JSON)
                .body(new SecurityServerAddressDto(address))
                .put("/system/server-address")
                .then();
    }

    /**
     * Returns configured timestamping services as raw maps (no OffsetDateTime fields — safe without JSR-310 mapper).
     */
    public List<Map<String, Object>> listTimestampingServicesRaw() {
        return session.given()
                .get("/system/timestamping-services")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("$");
    }

    /**
     * Adds a timestamping service.
     */
    public ValidatableResponse addTimestampingService(TimestampingServiceDto service) {
        return session.given()
                .contentType(ContentType.JSON)
                .body(service)
                .post("/system/timestamping-services")
                .then();
    }

    /**
     * Deletes a timestamping service by posting it to the delete endpoint.
     */
    public ValidatableResponse deleteTimestampingService(TimestampingServiceDto service) {
        return session.given()
                .contentType(ContentType.JSON)
                .body(service)
                .post("/system/timestamping-services/delete")
                .then();
    }

    /**
     * Returns the configured timestamping prioritization strategy as a raw string enum value.
     */
    public String getTimestampingPrioritizationStrategy() {
        return session.given()
                .get("/system/timestamping-services/prioritization-strategy")
                .then()
                .statusCode(200)
                .extract()
                .asString()
                .replaceAll("\"", "");
    }

    /**
     * Returns approved certificate authorities as raw maps for assertion without OffsetDateTime issues.
     */
    public List<Map<String, Object>> listApprovedCertificateAuthoritiesRaw() {
        return session.given()
                .get("/certificate-authorities")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("$");
    }

    /**
     * Returns the OCSP responder prioritization strategy as a raw string enum value.
     */
    public String getOcspPrioritizationStrategy() {
        return session.given()
                .get("/certificate-authorities/ocsp-prioritization-strategy")
                .then()
                .statusCode(200)
                .extract()
                .asString()
                .replaceAll("\"", "");
    }

    /**
     * Returns configurable properties as raw maps.
     */
    public List<Map<String, Object>> listConfigurablePropertiesRaw() {
        return session.given()
                .get("/system/property")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getList("$");
    }

    /**
     * Returns the current value of the named configurable property, or {@code null} if not found.
     */
    public String getConfigurablePropertyValue(String propertyName) {
        return listConfigurablePropertiesRaw().stream()
                .filter(p -> propertyName.equals(p.get("property_name")))
                .findFirst()
                .map(p -> (String) p.get("current_value"))
                .orElse(null);
    }

    /**
     * Returns the effective value of the named configurable property — its current value when set,
     * otherwise its default value. Returns {@code null} if the property is not found.
     */
    public String getConfigurablePropertyEffectiveValue(String propertyName) {
        return listConfigurablePropertiesRaw().stream()
                .filter(p -> propertyName.equals(p.get("property_name")))
                .findFirst()
                .map(p -> {
                    var current = (String) p.get("current_value");
                    return current != null ? current : (String) p.get("default_value");
                })
                .orElse(null);
    }

    /**
     * Updates a configurable property.
     */
    public ValidatableResponse updateConfigurableProperty(String propertyName, String propertyValue, String scope) {
        var body = new SecurityServerPropertyUpdateDto(propertyName, propertyValue).scope(scope);
        return session.given()
                .contentType(ContentType.JSON)
                .body(body)
                .patch("/system/property")
                .then();
    }
}
