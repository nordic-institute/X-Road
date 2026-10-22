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

import org.niis.xroad.e2e.parity.acl.AdminCall.OverrideBackendStub;
import org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.UncheckedIOException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The WireMock backend's admin API. An override is a mapping built from the stub file of the response it serves,
 * with a priority above the stub files' priority and a request matching one path and query.
 */
final class WireMockBackend {

    private static final int HTTP_CREATED = 201;
    private static final int OVERRIDE_PRIORITY = 0;
    private static final String MAPPINGS = "/__admin/mappings";
    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    private final String adminBaseUrl;

    WireMockBackend(String adminBaseUrl) {
        this.adminBaseUrl = adminBaseUrl;
    }

    /** Starts serving the override and returns the id of its mapping. */
    String override(OverrideBackendStub override) {
        var response = RestAssuredFactory.given()
                .header("Content-Type", "application/json")
                .body(mapping(override))
                .post(adminBaseUrl + MAPPINGS);
        assertThat(response.getStatusCode())
                .as("add backend mapping for %s: %s", override.urlPath(), response.getBody().asString())
                .isEqualTo(HTTP_CREATED);
        return response.jsonPath().getString("id");
    }

    /** Removes the mapping and returns the HTTP status of the call. */
    int remove(String mappingId) {
        return RestAssuredFactory.given().delete(adminBaseUrl + MAPPINGS + "/" + mappingId).getStatusCode();
    }

    private static String mapping(OverrideBackendStub override) {
        var root = (ObjectNode) readStub(override.stubName());
        root.put("priority", OVERRIDE_PRIORITY);
        var request = (ObjectNode) root.get("request");
        request.put("urlPath", override.urlPath());
        var query = request.putObject("queryParameters");
        override.query().forEach((name, value) -> query.putObject(name).put("equalTo", value));
        var response = (ObjectNode) root.get("response");
        var body = response.get("body").asString();
        for (var replacement : override.replacements().entrySet()) {
            body = body.replace(replacement.getKey(), replacement.getValue());
        }
        response.put("body", body);
        return MAPPER.writeValueAsString(root);
    }

    private static JsonNode readStub(String stubName) {
        var resource = "/wiremock_mappings/" + stubName + ".json";
        try (var in = WireMockBackend.class.getResourceAsStream(resource)) {
            assertThat(in).as("backend stub file %s", resource).isNotNull();
            return MAPPER.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
