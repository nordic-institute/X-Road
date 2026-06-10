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
package org.niis.xroad.ss.test.api.diagnostics;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.niis.xroad.ss.test.api.SsApiTest;
import org.niis.xroad.ss.test.api.admin.ClientsAdminClient;
import org.niis.xroad.ss.test.api.seeding.SsBaselineSeeder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * API tests for Security Server diagnostics traffic tab: service-list population from configured service descriptions.
 */
@DisplayName("Diagnostics traffic — service list population")
@SuppressWarnings("checkstyle:magicnumber")
class DiagnosticsTrafficTest extends SsApiTest {

    private static final String JSON_SPEC_URL = "http://mock-server:1080/test-services/testopenapi2.json";

    // MIGRATED-FROM: 0910-ss-diagnostics-traffic.feature :: "Services are loaded"
    @Test
    @DisplayName("Service selector for a client lists a seeded service code after adding a service description")
    void servicesAreLoaded(SsBaselineSeeder seeder) {
        var seed = given("a subsystem with an OPENAPI3 service description for code 's4c2' is seeded", () ->
                seeder.seedClientWithOpenApiService("diagTraffic", JSON_SPEC_URL, "s4c2"));

        var serviceDescriptions = when("the client's service descriptions are retrieved via /clients/{id}/service-descriptions", () -> {
            var session = seeder.newSession();
            return new ClientsAdminClient(session).listServiceDescriptions(seed.clientId());
        });

        then("the service descriptions list is non-empty", () ->
                assertThat(serviceDescriptions).isNotEmpty());

        then("at least one service description contains the service code 's4c2'", () -> {
            var serviceCodes = serviceDescriptions.stream()
                    .flatMap(sd -> sd.services().stream())
                    .map(ClientsAdminClient.ServiceView::serviceCode)
                    .toList();
            assertThat(serviceCodes).contains("s4c2");
        });
    }
}
