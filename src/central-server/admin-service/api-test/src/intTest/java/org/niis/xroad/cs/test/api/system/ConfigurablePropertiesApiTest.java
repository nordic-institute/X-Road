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
import org.junit.jupiter.api.parallel.ResourceLock;
import org.niis.xroad.cs.test.api.CsApiTest;
import org.niis.xroad.cs.test.api.CsBaselineSeeder;
import org.niis.xroad.cs.test.api.admin.SystemAdminClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * API tests for the Central Server's configurable system parameters ({@code /system/property}),
 * served by the shared {@code ConfigurablePropertiesApi} contract (common-admin-api).
 */
@SuppressWarnings("checkstyle:magicnumber")
class ConfigurablePropertiesApiTest extends CsApiTest {

    private static final String RATE_LIMIT_PROP = "xroad.admin-service.rate-limit-cache-size";
    private static final String NOT_EXPOSED_PROP = "xroad.admin-service.allowed-hostnames";
    private static final String UNKNOWN_PROP = "xroad.admin-service.does-not-exist";

    @Test
    void listReturnsDeclaredExposedPropertiesWithDefaultValue(CsBaselineSeeder seeder) {
        var system = new SystemAdminClient(seeder.newSession());

        var properties = when("configurable properties are listed", () ->
                system.listConfigurablePropertiesRaw());

        then("the list is non-empty and includes an exposed key from each of the three processes", () ->
                assertThat(properties)
                        .isNotEmpty()
                        .extracting(p -> p.get("property_name"))
                        .contains(
                                "xroad.admin-service.rate-limit-cache-size",
                                "xroad.management-service.rate-limit-cache-size",
                                "xroad.registration-service.rate-limit-cache-size")
                        .doesNotContain(NOT_EXPOSED_PROP));
    }

    @Test
    @ResourceLock("configurable-property")
    void updateConfigurablePropertyPersistsAndIsReflectedOnNextRead(CsBaselineSeeder seeder) {
        var system = new SystemAdminClient(seeder.newSession());

        var priorValue = given("the effective value of the rate-limit property is captured for restore", () ->
                system.getConfigurablePropertyEffectiveValue(RATE_LIMIT_PROP));

        try {
            when("the rate-limit property is updated to '5000'", () ->
                    system.updateConfigurableProperty(RATE_LIMIT_PROP, "5000")
                            .statusCode(204));

            then("the current value of the property is now '5000'", () ->
                    assertThat(system.getConfigurablePropertyValue(RATE_LIMIT_PROP)).isEqualTo("5000"));
        } finally {
            if (priorValue != null) {
                system.updateConfigurableProperty(RATE_LIMIT_PROP, priorValue).statusCode(204);
            }
        }
    }

    @Test
    void updateRejectsValueTheKeyConverterCannotParse(CsBaselineSeeder seeder) {
        var system = new SystemAdminClient(seeder.newSession());

        then("updating with a non-numeric value returns 400 invalid_property_value", () ->
                system.updateConfigurableProperty(RATE_LIMIT_PROP, "not-a-number")
                        .statusCode(400)
                        .body("error.code", equalTo("invalid_property_value")));
    }

    @Test
    void updateRejectsUndeclaredOrUnexposedKeys(CsBaselineSeeder seeder) {
        var system = new SystemAdminClient(seeder.newSession());

        then("updating a key the catalogue does not declare returns 404", () ->
                system.updateConfigurableProperty(UNKNOWN_PROP, "value")
                        .statusCode(404));

        then("updating a declared but non-exposed key returns 404", () ->
                system.updateConfigurableProperty(NOT_EXPOSED_PROP, "example.org")
                        .statusCode(404));
    }

    @Test
    void managementServiceRoleCannotViewOrUpdateConfigurableProperties(CsBaselineSeeder seeder) {
        var system = new SystemAdminClient(seeder.newManagementServiceOnlySession());

        then("listing configurable properties is forbidden", () ->
                system.getConfigurableProperties().statusCode(403));

        then("updating a configurable property is forbidden", () ->
                system.updateConfigurableProperty(RATE_LIMIT_PROP, "5000").statusCode(403));
    }
}
