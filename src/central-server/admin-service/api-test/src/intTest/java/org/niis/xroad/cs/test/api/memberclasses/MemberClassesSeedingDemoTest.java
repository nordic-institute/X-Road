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
package org.niis.xroad.cs.test.api.memberclasses;

import org.junit.jupiter.api.Test;
import org.niis.xroad.cs.test.api.CsApiTest;
import org.niis.xroad.cs.test.api.CsBaselineSeeder;
import org.niis.xroad.cs.test.api.admin.MemberClassesAdminClient;
import org.niis.xroad.test.apitest.core.junit.Step;

import static org.hamcrest.Matchers.hasItem;

/**
 * Demonstrates the self-seeding pattern: seeds its own namespaced member class, then GETs and
 * asserts it exists. Passes standalone ({@code --tests MemberClassesSeedingDemoTest}) and in the
 * full suite without relying on execution order or a clean DB.
 */
@SuppressWarnings("checkstyle:magicnumber")
class MemberClassesSeedingDemoTest extends CsApiTest {

    @Test
    void seedAndGetMemberClass(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);
        var client = new MemberClassesAdminClient(session);

        var code = Step.given("member class seeded for this test",
                () -> seeder.seedMemberClass(session, "demo01"));

        Step.when("GET /member-classes is called", () ->
                client.listMemberClasses()
                        .statusCode(200)
                        .body("code", hasItem(code)));

        Step.then("seeded member class is present in the list", () -> {
        });
    }

    @Test
    void seedTwoMemberClassesDoNotCollide(CsBaselineSeeder seeder) {
        var session = Step.given("admin session opened", seeder::newSession);

        var code1 = Step.given("first namespaced member class seeded",
                () -> seeder.seedMemberClass(session, "colA"));

        var code2 = Step.given("second namespaced member class seeded",
                () -> seeder.seedMemberClass(session, "colB"));

        Step.then("both codes are distinct", () -> {
            var client = new MemberClassesAdminClient(session);
            client.listMemberClasses()
                    .statusCode(200)
                    .body("code", hasItem(code1))
                    .body("code", hasItem(code2));
        });
    }
}
