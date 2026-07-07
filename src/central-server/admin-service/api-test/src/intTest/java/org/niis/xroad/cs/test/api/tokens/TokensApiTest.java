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
package org.niis.xroad.cs.test.api.tokens;

import org.junit.jupiter.api.Test;
import org.niis.xroad.cs.test.api.CsApiTest;
import org.niis.xroad.cs.test.api.CsBaselineSeeder;
import org.niis.xroad.cs.test.api.admin.TokensAdminClient;

import static org.hamcrest.Matchers.equalTo;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * API tests for Central Server token login/logout and RBAC enforcement.
 */
@SuppressWarnings("checkstyle:magicnumber")
class TokensApiTest extends CsApiTest {

    private static final String TOKEN_INACTIVE_JSON = """
            {
              "httpRequest": {"method": "GET", "path": "/getToken/token-id-1"},
              "httpResponse": {
                "statusCode": 200,
                "headers": {"Content-Type": ["application/json"]},
                "body": {
                  "type": "JSON",
                  "json": {"id":"token-id-1","active":false,"type":"type","friendlyName":"friendlyName",
                    "readOnly":false,"available":true,"serialNumber":"serialNumber","label":"label",
                    "slotIndex":13,"status":"OK","keyInfo":[],"tokenInfo":{},"savedToConfiguration":false}
                }
              }
            }
            """;

    private static final String TOKEN_ACTIVATE_JSON = """
            {
              "httpRequest": {"method": "PUT", "path": "/activateToken/token-id-1"},
              "httpResponse": {"statusCode": 204}
            }
            """;

    private static final String TOKEN_ACTIVE_JSON = """
            {
              "httpRequest": {"method": "GET", "path": "/getToken/token-id-2"},
              "httpResponse": {
                "statusCode": 200,
                "headers": {"Content-Type": ["application/json"]},
                "body": {
                  "type": "JSON",
                  "json": {"id":"token-id-2","active":true,"type":"type","friendlyName":"friendlyName",
                    "readOnly":false,"available":true,"serialNumber":"serialNumber","label":"label",
                    "slotIndex":13,"status":"OK","keyInfo":[],"tokenInfo":{},"savedToConfiguration":false}
                }
              }
            }
            """;

    private static final String TOKEN_DEACTIVATE_JSON = """
            {
              "httpRequest": {"method": "PUT", "path": "/deactivateToken/token-id-2"},
              "httpResponse": {"statusCode": 204}
            }
            """;

    @Test
    void loginToken(CsBaselineSeeder seeder) {
        var tokens = new TokensAdminClient(seeder.newSecurityOfficerSession());

        given("signer mocks registered for inactive token-id-1", () -> {
            seeder.mockExpectation(TOKEN_INACTIVE_JSON);
            seeder.mockExpectation(TOKEN_ACTIVATE_JSON);
        });

        try {
            then("token-id-1 login returns 200 with matching id", () ->
                    tokens.loginToken("token-id-1", "1234")
                            .statusCode(200)
                            .body("id", equalTo("token-id-1")));
        } finally {
            seeder.clearMockExpectations("/getToken/token-id-1");
            seeder.clearMockExpectations("/activateToken/token-id-1");
        }
    }

    @Test
    void loginTokenForbiddenForNonPrivilegedUser(CsBaselineSeeder seeder) {
        var tokens = new TokensAdminClient(seeder.newRegistrationOfficerSession());

        when("REGISTRATION_OFFICER attempts to login token", () ->
                tokens.loginToken("token-id-1", "1234")
                        .statusCode(403));
    }

    @Test
    void logoutToken(CsBaselineSeeder seeder) {
        var tokens = new TokensAdminClient(seeder.newSecurityOfficerSession());

        given("signer mocks registered for active token-id-2", () -> {
            seeder.mockExpectation(TOKEN_ACTIVE_JSON);
            seeder.mockExpectation(TOKEN_DEACTIVATE_JSON);
        });

        try {
            then("token-id-2 logout returns 200 with matching id", () ->
                    tokens.logoutToken("token-id-2")
                            .statusCode(200)
                            .body("id", equalTo("token-id-2")));
        } finally {
            seeder.clearMockExpectations("/getToken/token-id-2");
            seeder.clearMockExpectations("/deactivateToken/token-id-2");
        }
    }

    @Test
    void logoutTokenForbiddenForNonPrivilegedUser(CsBaselineSeeder seeder) {
        var tokens = new TokensAdminClient(seeder.newRegistrationOfficerSession());

        when("REGISTRATION_OFFICER attempts to logout token", () ->
                tokens.logoutToken("token-id-2")
                        .statusCode(403));
    }
}
