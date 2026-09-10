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
package org.niis.xroad.cs.test.api.admin;

import io.restassured.http.ContentType;
import io.restassured.response.ValidatableResponse;

import java.util.List;
import java.util.Map;

/**
 * RestAssured client for the Central Server system admin API.
 */
@SuppressWarnings("checkstyle:magicnumber")
public class SystemAdminClient {

    private final AdminApiSession session;

    public SystemAdminClient(AdminApiSession session) {
        this.session = session;
    }

    public ValidatableResponse getSystemStatus() {
        return session.given()
                .get("/system/status")
                .then();
    }

    public ValidatableResponse getHighAvailabilityClusterStatus() {
        return session.given()
                .get("/system/high-availability-cluster/status")
                .then();
    }

    public ValidatableResponse getSystemVersion() {
        return session.given()
                .get("/system/version")
                .then();
    }

    public ValidatableResponse updateCentralServerAddress(String address) {
        return session.given()
                .contentType(ContentType.JSON)
                .body("{\"central_server_address\":\"" + address + "\"}")
                .put("/system/server-address")
                .then();
    }

    /**
     * Requests the configurable properties list without asserting on the response status.
     */
    public ValidatableResponse getConfigurableProperties() {
        return session.given()
                .get("/system/property")
                .then();
    }

    /**
     * Returns configurable properties as raw maps.
     */
    public List<Map<String, Object>> listConfigurablePropertiesRaw() {
        return getConfigurableProperties()
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
    public ValidatableResponse updateConfigurableProperty(String propertyName, String propertyValue) {
        return session.given()
                .contentType(ContentType.JSON)
                .body(Map.of("property_name", propertyName, "property_value", propertyValue))
                .patch("/system/property")
                .then();
    }
}
