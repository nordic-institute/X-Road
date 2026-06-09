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
import org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * API tests for initial admin user state after baseline setup.
 */
@DisplayName("Initial admin user — backend state")
@SuppressWarnings("checkstyle:magicnumber")
class InitAdminUserTest extends SsApiTest {

    // MIGRATED-FROM: 0090-ss-initial-admin-user.feature :: "Reopening admin user URL after creation redirects to login"
    @Test
    @DisplayName("After admin user is created the creation endpoint reports admin_user_creation_required=false")
    void reopeningAdminUrlAfterCreationReturnsNotRequired(SsApiTestContainerSetup stack) {
        var uiMapping = stack.getContainerMapping(SsApiTestContainerSetup.UI, Port.UI);
        var baseUrl = "https://%s:%d".formatted(uiMapping.host(), uiMapping.port());

        var required = when("the initial admin user status endpoint is queried after baseline setup", () ->
                RestAssuredFactory.given()
                        .get(baseUrl + "/api/v1/initialization/admin-user/status")
                        .then()
                        .statusCode(200)
                        .extract()
                        .jsonPath()
                        .getBoolean("admin_user_creation_required"));

        then("admin_user_creation_required is false because the admin user already exists", () ->
                assertThat(required).isFalse());
    }
}
