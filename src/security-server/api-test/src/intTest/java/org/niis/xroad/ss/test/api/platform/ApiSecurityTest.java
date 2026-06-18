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
package org.niis.xroad.ss.test.api.platform;

import io.restassured.http.Method;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.niis.xroad.ss.test.api.Port;
import org.niis.xroad.ss.test.api.SsApiTest;
import org.niis.xroad.ss.test.api.SsApiTestContainerSetup;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;
import static org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory.given;

/**
 * Verifies that all admin API endpoints return 401 when called without authentication.
 *
 * <p>The path and verb set is derived at test runtime from the OpenAPI spec bundled in the
 * {@code openapi-model} jar ({@code META-INF/openapi-definition.yaml}). Every operation listed
 * in the spec is exercised across all HTTP verbs; adding a new endpoint to the spec automatically
 * extends this sweep with no test edit required.
 *
 * <p>Path parameters (e.g. {@code {id}}) are replaced with {@code _} before the request is sent.
 * Spring Security's filter chain evaluates authentication before routing, so the 401 is returned
 * regardless of whether the resolved path maps to a real resource.
 *
 * <p>Intentionally-public paths (served without authentication by Spring Security's {@code ignoring()}
 * rule in {@code ApiWebSecurityConfig}) are excluded from the 401-sweep: {@code /api/v1/openapi.yaml},
 * {@code /api/v1/initialization/admin-user}, and {@code /api/v1/initialization/admin-user/status}.
 */
// MIGRATED-FROM: 4000-api-security-check.feature :: "Verify all endpoints fail when called without authorization"
@DisplayName("All admin API endpoints require authentication")
@SuppressWarnings("checkstyle:magicnumber")
class ApiSecurityTest extends SsApiTest {

    private static final String API_BASE = "/api/v1";
    private static final String OPENAPI_SPEC_RESOURCE = "META-INF/openapi-definition.yaml";
    private static final List<String> HTTP_VERBS = List.of("get", "post", "put", "delete", "patch");

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/v1/openapi.yaml",
            "/api/v1/initialization/admin-user",
            "/api/v1/initialization/admin-user/status"
    );

    @Test
    @DisplayName("unauthenticated requests to admin API endpoints are rejected with 401")
    void allAdminEndpointsRequireAuthentication(SsApiTestContainerSetup stack) {
        var uiMapping = stack.getContainerMapping(SsApiTestContainerSetup.UI, Port.UI);
        var baseUrl = "https://%s:%d".formatted(uiMapping.host(), uiMapping.port());

        var operations = when("admin API operations are loaded from the OpenAPI spec", () ->
                loadOperationsFromSpec());

        then("every non-public operation returns 401 when called without authentication", () -> {
            var failures = new ArrayList<String>();
            for (var op : operations) {
                var fullPath = API_BASE + op.specPath();
                if (PUBLIC_PATHS.contains(fullPath)) {
                    continue;
                }
                var requestPath = resolvePathParams(fullPath);
                int status = given()
                        .baseUri(baseUrl)
                        .request(op.method(), requestPath)
                        .statusCode();
                if (status != 401) {
                    failures.add("%s %s → %d (expected 401)".formatted(op.method(), fullPath, status));
                }
            }
            if (!failures.isEmpty()) {
                fail("Endpoints did not return 401:\n" + String.join("\n", failures));
            }
            assertThat(failures).isEmpty();
        });
    }

    private List<Operation> loadOperationsFromSpec() {
        var specStream = getClass().getClassLoader().getResourceAsStream(OPENAPI_SPEC_RESOURCE);
        if (specStream == null) {
            throw new IllegalStateException("OpenAPI spec not found on classpath: " + OPENAPI_SPEC_RESOURCE);
        }
        try (InputStream stream = specStream) {
            return parseOperations(stream);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read OpenAPI spec", e);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Operation> parseOperations(InputStream stream) {
        var yaml = new Yaml();
        Map<String, Object> spec = yaml.load(stream);
        Map<String, Object> paths = (Map<String, Object>) spec.get("paths");

        var operations = new ArrayList<Operation>();
        for (var pathEntry : paths.entrySet()) {
            var specPath = pathEntry.getKey();
            Map<String, Object> pathItem = (Map<String, Object>) pathEntry.getValue();
            for (var verb : HTTP_VERBS) {
                if (pathItem.containsKey(verb)) {
                    operations.add(new Operation(Method.valueOf(verb.toUpperCase()), specPath));
                }
            }
        }
        return operations;
    }

    private String resolvePathParams(String path) {
        return path.replaceAll("\\{[^}]+}", "_");
    }

    private record Operation(Method method, String specPath) {
    }
}
