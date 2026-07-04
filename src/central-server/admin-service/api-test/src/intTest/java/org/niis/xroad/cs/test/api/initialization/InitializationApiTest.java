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
package org.niis.xroad.cs.test.api.initialization;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.niis.xroad.cs.test.api.CsApiTest;
import org.niis.xroad.cs.test.api.CsBaselineSeeder;
import org.niis.xroad.cs.test.api.admin.InitializationAdminClient;

import static org.hamcrest.Matchers.equalTo;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * API tests for Central Server initialization API: status, rejection, RBAC, and validation errors.
 */
@SuppressWarnings("checkstyle:magicnumber")
class InitializationApiTest extends CsApiTest {

    private static final String GET_TOKEN_INITIALIZED_MOCK = """
            {
              "httpRequest": {"method": "GET", "path": "/getToken/0"},
              "httpResponse": {
                "statusCode": 200,
                "headers": {"Content-Type": ["application/json"]},
                "body": "{\\"id\\":\\"0\\",\\"active\\":true,\\"type\\":\\"type\\",\\"friendlyName\\":\\"friendlyName\\",\\"readOnly\\":false,\\"available\\":true,\\"serialNumber\\":\\"serialNumber\\",\\"label\\":\\"label\\",\\"slotIndex\\":13,\\"status\\":\\"OK\\",\\"keyInfo\\":[],\\"tokenInfo\\":{}}"
              }
            }
            """;

    @Test
    @Disabled("warm stack: CS already initialized; NOT_INITIALIZED->INITIALIZED happy path cannot run on shared warm substrate")
    void serverInitializationHappyPath(CsBaselineSeeder seeder) {
        throw new UnsupportedOperationException("Implement when a dedicated fresh-stack fixture is available");
    }

    @Test
    void reInitializationRejectedAlreadyInitialized(CsBaselineSeeder seeder) {
        var init = new InitializationAdminClient(seeder.newSession());

        given("getToken/0 mocked to return an initialized token", () ->
                seeder.mockExpectation(GET_TOKEN_INITIALIZED_MOCK));

        try {
            when("POST /initialization on already-initialized CS is rejected", () ->
                    init.postInitialization("e2e-cs", "E2E-CS", "1234-VALID")
                            .statusCode(409)
                            .body("error.code", equalTo("init_already_initialized")));
        } finally {
            seeder.clearMockExpectations("/getToken/0");
        }
    }

    @Test
    void serverInitializationForbiddenForNonPrivilegedUser(CsBaselineSeeder seeder) {
        var init = new InitializationAdminClient(seeder.newRegistrationOfficerSession());

        when("REGISTRATION_OFFICER attempts POST /initialization returns 403", () ->
                init.postInitialization("e2e-cs", "E2E-CS", "1234-VALID")
                        .statusCode(403));
    }

    @ParameterizedTest
    @CsvSource({
        "12,          E2E-CS,               e2e-cs,                  token_weak_pin",
        "'',          E2E-CS,               e2e-cs,                  validation_failure",
        "1234-VALID,  INSTANCE::::%INVALID, e2e-cs,                  validation_failure",
        "1234-VALID,  '',                   e2e-cs,                  validation_failure",
        "1234-VALID,  E2E-CS,               123.123..invalid..123.x, validation_failure",
        "1234-VALID,  E2E-CS,               123.123_invalid_123.x,   validation_failure",
        "1234-VALID,  E2E-CS,               '',                      validation_failure"
    })
    void serverInitializationFailsWithError(
            String tokenPin, String instanceIdentifier, String address, String errorCode,
            CsBaselineSeeder seeder) {
        var init = new InitializationAdminClient(seeder.newSession());

        when("POST /initialization with invalid params returns 400 and error code " + errorCode, () ->
                init.postInitialization(address, instanceIdentifier, tokenPin)
                        .statusCode(400)
                        .body("error.code", equalTo(errorCode)));
    }
}
