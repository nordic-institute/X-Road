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
package org.niis.xroad.cs.test.api.issuerservice;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.niis.xroad.cs.test.api.CsApiTest;
import org.niis.xroad.cs.test.api.CsBaselineSeeder;
import org.niis.xroad.cs.test.api.admin.TrustedIssuersAdminClient;
import org.niis.xroad.test.apitest.core.junit.Step;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * API tests for the {@code /trusted-issuers} admin resource: listing and removing a trusted issuer.
 */
@SuppressWarnings("checkstyle:magicnumber")
class TrustedIssuersApiTest extends CsApiTest {

    @Test
    @Tag("destructive")
    void trustedIssuerIsListedRemovedAndKeptAsRemoved(CsBaselineSeeder seeder) {
        var client = new TrustedIssuersAdminClient(seeder.newSecurityOfficerSession());

        var id = Step.given("a trusted issuer is seeded", () ->
                seeder.seedTrustedIssuer("trustedissuer01"));

        Step.then("the trusted issuer is listed", () ->
                client.list()
                        .statusCode(200)
                        .body("id", hasItem(id)));

        Step.when("the trusted issuer is removed", () ->
                client.delete(id).statusCode(204));

        Step.and("the trusted issuer is no longer listed", () ->
                client.list()
                        .statusCode(200)
                        .body("id", not(hasItem(id))));

        var removedAt = Step.and("the row is kept with removed_at set", () -> {
            var value = seeder.queryIssuerDidRemovedAt(id);
            assertNotNull(value, "removed_at should be set after removal");
            return value;
        });

        Step.when("the trusted issuer is removed again", () ->
                client.delete(id)
                        .statusCode(404)
                        .body("error.code", equalTo("trusted_issuer_not_found")));

        Step.then("the removed_at value is unchanged", () ->
                assertEquals(removedAt, seeder.queryIssuerDidRemovedAt(id)));
    }
}
