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
package org.niis.xroad.cs.test.api.security;

import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.Test;
import org.niis.xroad.cs.test.api.CsApiTest;
import org.niis.xroad.cs.test.api.CsBaselineSeeder;
import org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * Verifies that every endpoint in the CS admin OpenAPI spec returns 401 without an auth header.
 * Parsed directly from the spec at test time, so any newly added endpoint is covered automatically.
 */
@SuppressWarnings("checkstyle:magicnumber")
class ApiSecurityTest extends CsApiTest {

    private static final String OPENAPI_SPEC_RESOURCE = "openapi-definition.yaml";
    private static final String API_BASE_PATH = "/api/v1";
    private static final String PARAM_PLACEHOLDER = "1";

    /**
     * Endpoints intentionally accessible without authentication.
     * Keyed by operationId; value is the HTTP status expected without auth.
     * Source: ApiWebSecurityConfig.apiWebSecurityCustomizer ignores /api/v1/openapi.yaml.
     */
    private static final Map<String, Integer> PUBLIC_ENDPOINTS = Map.of(
            "downloadOpenApi", 200
    );

    @Test
    void allEndpointsFailWithoutAuthorization(CsBaselineSeeder seeder) {
        var baseUrl = seeder.getAdminBaseUrl();

        var endpoints = when("OpenAPI spec parsed for all path+verb combinations",
                () -> loadEndpointsFromSpec());

        then("each protected endpoint returns 401 and each public endpoint returns its expected status", () -> {
            var failures = new ArrayList<String>();
            for (var endpoint : endpoints) {
                int actual = callWithoutAuth(baseUrl, endpoint);
                int expected = endpoint.expectedStatus();
                if (actual != expected) {
                    failures.add("%s %s → expected %d, got %d".formatted(
                            endpoint.verb().toUpperCase(), endpoint.resolvedPath(), expected, actual));
                }
            }
            if (!failures.isEmpty()) {
                throw new AssertionError("Security check failed for " + failures.size() + " endpoint(s):\n"
                        + String.join("\n", failures));
            }
        });
    }

    private List<Endpoint> loadEndpointsFromSpec() {
        Map<String, Object> spec = parseYaml();
        @SuppressWarnings("unchecked")
        Map<String, Map<String, Object>> paths = (Map<String, Map<String, Object>>) spec.get("paths");

        var result = new ArrayList<Endpoint>();
        for (var pathEntry : paths.entrySet()) {
            String rawPath = pathEntry.getKey();
            Map<String, Object> methods = pathEntry.getValue();
            for (var methodEntry : methods.entrySet()) {
                String verb = methodEntry.getKey();
                if (!isHttpVerb(verb)) {
                    continue;
                }
                @SuppressWarnings("unchecked")
                Map<String, Object> operation = (Map<String, Object>) methodEntry.getValue();
                String operationId = (String) operation.get("operationId");
                int expectedStatus = PUBLIC_ENDPOINTS.getOrDefault(operationId, 401);
                result.add(new Endpoint(verb, rawPath, substituteParams(rawPath), expectedStatus));
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseYaml() {
        InputStream resource = getClass().getClassLoader().getResourceAsStream(OPENAPI_SPEC_RESOURCE);
        if (resource == null) {
            throw new IllegalStateException(OPENAPI_SPEC_RESOURCE + " not found on classpath");
        }
        return (Map<String, Object>) new Yaml().load(resource);
    }

    private boolean isHttpVerb(String key) {
        return Set.of("get", "post", "put", "patch", "delete").contains(key);
    }

    private String substituteParams(String path) {
        return path.replaceAll("\\{[^}]+}", PARAM_PLACEHOLDER);
    }

    private int callWithoutAuth(String baseUrl, Endpoint endpoint) {
        RequestSpecification spec = RestAssuredFactory.givenSilent()
                .baseUri(baseUrl)
                .basePath(API_BASE_PATH);
        Response response = switch (endpoint.verb()) {
            case "get" -> spec.get(endpoint.resolvedPath());
            case "post" -> spec.post(endpoint.resolvedPath());
            case "put" -> spec.put(endpoint.resolvedPath());
            case "patch" -> spec.patch(endpoint.resolvedPath());
            case "delete" -> spec.delete(endpoint.resolvedPath());
            default -> throw new IllegalStateException("Unexpected verb: " + endpoint.verb());
        };
        return response.statusCode();
    }

    private record Endpoint(String verb, String rawPath, String resolvedPath, int expectedStatus) {
    }
}
