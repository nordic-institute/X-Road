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
package org.niis.xroad.cs.test.api.system;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceAccessMode;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.niis.xroad.cs.test.api.CsApiTest;
import org.niis.xroad.cs.test.api.CsBaselineSeeder;
import org.niis.xroad.cs.test.api.admin.SystemAdminClient;

import static org.hamcrest.Matchers.emptyOrNullString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * API tests for Central Server system status, cluster status, version, and server address.
 */
@SuppressWarnings("checkstyle:magicnumber")
class SystemApiTest extends CsApiTest {

    @Test
    void systemStatusIsOk(CsBaselineSeeder seeder) {
        var system = new SystemAdminClient(seeder.newSession());

        when("system status is requested", () ->
                system.getSystemStatus()
                        .statusCode(200)
                        .body("initialization_status", notNullValue())
                        .body("high_availability_status", notNullValue())
                        .body("high_availability_status.node_name", equalTo("test_node"))
                        .body("high_availability_status.is_ha_configured", equalTo(true)));
    }

    @Test
    @ResourceLock(value = "server-address", mode = ResourceAccessMode.READ)
    void systemClusterStatusEndpointWorks(CsBaselineSeeder seeder) {
        var system = new SystemAdminClient(seeder.newSecurityOfficerSession());

        when("cluster status is requested", () ->
                system.getHighAvailabilityClusterStatus()
                        .statusCode(200)
                        .body("is_ha_configured", equalTo(true))
                        .body("node_name", equalTo("test_node"))
                        .body("nodes", hasSize(1))
                        .body("nodes[0].node_name", equalTo("test_node"))
                        .body("nodes[0].node_address", not(emptyOrNullString()))
                        .body("nodes[0].status", equalTo("ERROR"))
                        .body("nodes[0].configuration_generated", notNullValue())
                        .body("all_nodes_ok", equalTo(false)));
    }

    @Test
    void systemVersionEndpointReturnsVersion(CsBaselineSeeder seeder) {
        var system = new SystemAdminClient(seeder.newSession());

        then("version endpoint returns non-empty info without placeholder tokens", () ->
                system.getSystemVersion()
                        .statusCode(200)
                        .body("info", not(emptyOrNullString()))
                        .body("info", not(equalTo("@version@")))
                        .body("info", not(equalTo("@buildType@")))
                        .body("info", not(equalTo("@gitCommitDate@")))
                        .body("info", not(equalTo("@gitCommitHash@"))));
    }

    @Test
    @ResourceLock("server-address")
    void updateCentralServerAddress(CsBaselineSeeder seeder) {
        var system = new SystemAdminClient(seeder.newSecurityOfficerSession());

        given("valid.url is accepted and returns 200 with status", () ->
                system.updateCentralServerAddress("valid.url")
                        .statusCode(200)
                        .body("initialization_status", notNullValue()));

        when("invalid...address.c is rejected with 400", () ->
                system.updateCentralServerAddress("invalid...address.c")
                        .statusCode(400));

        when("invalid_address.c is rejected with 400", () ->
                system.updateCentralServerAddress("invalid_address.c")
                        .statusCode(400));

        then("server address is restored to cs", () ->
                system.updateCentralServerAddress("cs")
                        .statusCode(200));
    }
}
