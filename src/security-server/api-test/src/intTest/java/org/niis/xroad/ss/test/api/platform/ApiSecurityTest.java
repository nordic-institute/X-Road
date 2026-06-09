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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;
import static org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory.given;

/**
 * Verifies that all admin API endpoints return 401 when called without authentication.
 */
// MIGRATED-FROM: 4000-api-security-check.feature :: "Verify all endpoints fail when called without authorization"
@DisplayName("All admin API endpoints require authentication")
class ApiSecurityTest extends SsApiTest {

    private static final List<String> REPRESENTATIVE_PATHS = List.of(
            "/api/v1/clients",
            "/api/v1/tokens",
            "/api/v1/backups",
            "/api/v1/diagnostics/globalconf",
            "/api/v1/diagnostics/ocsp-responders",
            "/api/v1/diagnostics/timestamping-services",
            "/api/v1/diagnostics/addon-status",
            "/api/v1/system/anchor",
            "/api/v1/system/timestamping-services",
            "/api/v1/system/property",
            "/api/v1/system/version",
            "/api/v1/token-certificates",
            "/api/v1/member-classes",
            "/api/v1/xroad-instances",
            "/api/v1/certificate-authorities",
            "/api/v1/security-servers"
    );

    @Test
    @DisplayName("unauthenticated requests to admin API endpoints are rejected with 401")
    void allAdminEndpointsRequireAuthentication(SsApiTestContainerSetup stack) {
        var uiMapping = stack.getContainerMapping(SsApiTestContainerSetup.UI, Port.UI);
        var baseUrl = "https://%s:%d".formatted(uiMapping.host(), uiMapping.port());

        when("all representative admin API endpoints are called without authentication", () -> {
        });

        then("every endpoint returns 401", () -> {
            var failures = new java.util.ArrayList<String>();
            for (var path : REPRESENTATIVE_PATHS) {
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
