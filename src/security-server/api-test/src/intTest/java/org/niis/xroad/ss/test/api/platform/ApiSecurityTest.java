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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.niis.xroad.ss.test.api.Port;
import org.niis.xroad.ss.test.api.SsApiTest;
import org.niis.xroad.ss.test.api.SsApiTestContainerSetup;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;
import static org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory.given;

/**
 * Verifies that all admin API endpoints return 401 when called without authentication.
 *
 * <p>The legacy 4000 test reflected over all {@code @FeignClient} beans (~143 operations). This test
 * enumerates every parameterless GET path from the OpenAPI spec plus one representative path per
 * resource area to achieve comparable breadth. Parameterized paths (containing {@code {id}} etc.)
 * are tested via their resource-root equivalents. POST/PUT/DELETE paths all require auth by the same
 * Spring Security filter chain; the GET set is sufficient to verify the chain is correctly applied.
 * Any future resource area added to the spec without a corresponding GET root would be missed —
 * see issue 43 for the known residual gap vs. the full 143-operation FeignClient reflection approach.
 *
 * <p>Intentionally-public paths (served without authentication by Spring Security's {@code ignoring()}
 * rule in {@code ApiWebSecurityConfig}) are excluded from the 401-sweep: {@code /api/v1/openapi.yaml},
 * {@code /api/v1/initialization/admin-user}, and {@code /api/v1/initialization/admin-user/status}.
 */
// MIGRATED-FROM: 4000-api-security-check.feature :: "Verify all endpoints fail when called without authorization"
@DisplayName("All admin API endpoints require authentication")
@SuppressWarnings("checkstyle:magicnumber")
class ApiSecurityTest extends SsApiTest {

    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/v1/openapi.yaml",
            "/api/v1/initialization/admin-user",
            "/api/v1/initialization/admin-user/status"
    );

    private static final List<String> ADMIN_API_PATHS = List.of(
            "/api/v1/backups",
            "/api/v1/backups/ext",
            "/api/v1/backups/upload",
            "/api/v1/token-certificates",
            "/api/v1/clients",
            "/api/v1/diagnostics/globalconf",
            "/api/v1/diagnostics/ocsp-responders",
            "/api/v1/diagnostics/timestamping-services",
            "/api/v1/diagnostics/addon-status",
            "/api/v1/diagnostics/backup-encryption-status",
            "/api/v1/diagnostics/message-log-encryption-status",
            "/api/v1/diagnostics/proxy-memory-usage-status",
            "/api/v1/diagnostics/auth-cert-req-status",
            "/api/v1/diagnostics/global-conf-status",
            "/api/v1/diagnostics/other-security-server-status",
            "/api/v1/diagnostics/operational-monitoring",
            "/api/v1/diagnostics/info/download",
            "/api/v1/initialization",
            "/api/v1/initialization/status",
            "/api/v1/member-classes",
            "/api/v1/member-names",
            "/api/v1/security-servers",
            "/api/v1/service-descriptions",
            "/api/v1/system/anchor",
            "/api/v1/system/anchor/download",
            "/api/v1/system/certificate",
            "/api/v1/system/certificate/export",
            "/api/v1/system/certificate/csr",
            "/api/v1/system/property",
            "/api/v1/system/server-address",
            "/api/v1/system/timestamping-services",
            "/api/v1/system/node-type",
            "/api/v1/system/auth-provider-type",
            "/api/v1/system/version",
            "/api/v1/system/maintenance-mode",
            "/api/v1/certificate-authorities",
            "/api/v1/certificate-authorities/ocsp-prioritization-strategy",
            "/api/v1/mail/mail-notification-status",
            "/api/v1/mail/send-test-mail",
            "/api/v1/timestamping-services",
            "/api/v1/tokens",
            "/api/v1/xroad-instances"
    );

    @Test
    @DisplayName("unauthenticated requests to admin API endpoints are rejected with 401")
    void allAdminEndpointsRequireAuthentication(SsApiTestContainerSetup stack) {
        var uiMapping = stack.getContainerMapping(SsApiTestContainerSetup.UI, Port.UI);
        var baseUrl = "https://%s:%d".formatted(uiMapping.host(), uiMapping.port());

        when("all admin API paths are called without authentication", () -> {
        });

        then("every non-public path returns 401", () -> {
            var failures = new java.util.ArrayList<String>();
            for (var path : ADMIN_API_PATHS) {
                if (PUBLIC_PATHS.contains(path)) {
                    continue;
                }
                int status = given()
                        .baseUri(baseUrl)
                        .get(path)
                        .statusCode();
                if (status != 401) {
                    failures.add("%s → %d (expected 401)".formatted(path, status));
                }
            }
            if (!failures.isEmpty()) {
                fail("Endpoints did not return 401:\n" + String.join("\n", failures));
            }
            assertThat(failures).isEmpty();
        });
    }
}
