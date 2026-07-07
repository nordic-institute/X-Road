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
package org.niis.xroad.cs.test.api.openapi;

import org.junit.jupiter.api.Test;
import org.niis.xroad.cs.test.api.CsApiTest;
import org.niis.xroad.cs.test.api.CsBaselineSeeder;

import static org.hamcrest.Matchers.containsString;
import static org.niis.xroad.test.apitest.core.junit.Step.then;

/**
 * API tests verifying the OpenAPI definition endpoint availability.
 */
@SuppressWarnings("checkstyle:magicnumber")
class OpenApiDefinitionTest extends CsApiTest {

    private static final int MIN_YAML_BODY_LENGTH = 100_000;

    @Test
    void openapiDefinitionYamlIsAvailable(CsBaselineSeeder seeder) {
        var session = seeder.newSession();

        then("GET /openapi.yaml returns 200 with yaml content and proper content-disposition", () -> {
            var response = session.given()
                    .get("/openapi.yaml")
                    .then()
                    .statusCode(200)
                    .header("Content-Disposition", containsString("openapi.yaml"))
                    .header("Content-Type", containsString("yaml"))
                    .extract();

            var bodyLength = response.body().asByteArray().length;
            if (bodyLength < MIN_YAML_BODY_LENGTH) {
                throw new AssertionError("Expected openapi.yaml body length >= " + MIN_YAML_BODY_LENGTH
                        + " but was " + bodyLength);
            }
        });
    }
}
