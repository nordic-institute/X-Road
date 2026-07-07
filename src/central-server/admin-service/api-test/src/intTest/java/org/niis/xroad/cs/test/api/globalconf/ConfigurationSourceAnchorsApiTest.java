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
package org.niis.xroad.cs.test.api.globalconf;

import org.junit.jupiter.api.Test;
import org.niis.xroad.cs.test.api.CsApiTest;
import org.niis.xroad.cs.test.api.CsBaselineSeeder;
import org.niis.xroad.cs.test.api.admin.ConfigurationSourceAnchorsAdminClient;
import org.niis.xroad.test.apitest.core.junit.Step;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@SuppressWarnings("checkstyle:magicnumber")
class ConfigurationSourceAnchorsApiTest extends CsApiTest {

    @Test
    void reCreateInternalConfigurationSourceAnchor(CsBaselineSeeder seeder) {
        var session = Step.given("security officer session opened", seeder::newSecurityOfficerSession);
        var client = new ConfigurationSourceAnchorsAdminClient(session);

        var oldHash = Step.given("existing internal anchor hash is noted", () ->
                client.getAnchor("INTERNAL")
                        .statusCode(200)
                        .body("anchor.hash", notNullValue())
                        .extract().jsonPath().getString("anchor.hash"));

        var oldCreatedAt = Step.and("existing internal anchor createdAt is noted", () ->
                client.getAnchor("INTERNAL")
                        .extract().jsonPath().getString("anchor.created_at"));

        var reCreated = Step.when("internal anchor is re-created", () ->
                client.reCreateAnchor("INTERNAL")
                        .statusCode(200)
                        .body("hash", notNullValue())
                        .body("created_at", notNullValue())
                        .extract().jsonPath());

        Step.then("re-created anchor has different hash and createdAt", () -> {
            assertNotEquals(oldHash, reCreated.getString("hash"), "hash must differ after recreation");
            assertNotEquals(oldCreatedAt, reCreated.getString("created_at"), "created_at must differ after recreation");
        });

        Step.and("GET returns values matching re-create response", () ->
                client.getAnchor("INTERNAL")
                        .statusCode(200)
                        .body("anchor.hash", not(oldHash))
                        .body("anchor.hash", notNullValue()));
    }

    @Test
    void reCreateExternalConfigurationSourceAnchor(CsBaselineSeeder seeder) {
        var session = Step.given("security officer session opened", seeder::newSecurityOfficerSession);
        var client = new ConfigurationSourceAnchorsAdminClient(session);

        var oldHash = Step.given("existing external anchor hash is noted", () ->
                client.getAnchor("EXTERNAL")
                        .statusCode(200)
                        .body("anchor.hash", notNullValue())
                        .extract().jsonPath().getString("anchor.hash"));

        var oldCreatedAt = Step.and("existing external anchor createdAt is noted", () ->
                client.getAnchor("EXTERNAL")
                        .extract().jsonPath().getString("anchor.created_at"));

        var reCreated = Step.when("external anchor is re-created", () ->
                client.reCreateAnchor("EXTERNAL")
                        .statusCode(200)
                        .body("hash", notNullValue())
                        .body("created_at", notNullValue())
                        .extract().jsonPath());

        Step.then("re-created anchor has different hash and createdAt", () -> {
            assertNotEquals(oldHash, reCreated.getString("hash"), "hash must differ after recreation");
            assertNotEquals(oldCreatedAt, reCreated.getString("created_at"), "created_at must differ after recreation");
        });

        Step.and("GET returns values matching re-create response", () ->
                client.getAnchor("EXTERNAL")
                        .statusCode(200)
                        .body("anchor.hash", not(oldHash))
                        .body("anchor.hash", notNullValue()));
    }
}
