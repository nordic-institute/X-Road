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
package org.niis.xroad.ss.test.api.settings;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.niis.xroad.securityserver.restapi.openapi.model.CostTypeDto;
import org.niis.xroad.securityserver.restapi.openapi.model.TimestampingServiceDto;
import org.niis.xroad.ss.test.api.SsApiTest;
import org.niis.xroad.ss.test.api.admin.SystemParametersAdminClient;
import org.niis.xroad.ss.test.api.seeding.SsBaselineSeeder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.test.apitest.core.junit.Step.and;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * API tests for Security Server system parameters: address, timestamping services, approved CAs,
 * and configurable properties.
 */
@DisplayName("System parameters — address, timestamping, approved CAs, configurable properties")
@SuppressWarnings("checkstyle:magicnumber")
class SystemParametersTest extends SsApiTest {

    private static final String TEST_TSA_NAME = "Test TSA";
    private static final String TEST_TSA_URL = "http://testca:8899";
    private static final String RATE_LIMIT_PROP = "xroad.proxy-ui-api.rate-limit-requests-per-second";
    private static final String RATE_LIMIT_SCOPE = "proxy-ui-api";

    // MIGRATED-FROM: 0400-ss-system-parameters.feature :: "Security server address is update fails"
    @Test
    @DisplayName("Attempting to change SS address to 'new.address' fails with management request error")
    void securityServerAddressUpdateFails(SsBaselineSeeder seeder) {
        var systemParams = new SystemParametersAdminClient(seeder.newSession());

        given("the Security Server is initialized", () ->
                assertThat(systemParams.listTimestampingServicesRaw()).isNotNull());

        when("the SS address is changed to 'new.address'", () -> {
            var response = systemParams.changeServerAddress("new.address");
            then("the response is 400 or 500 indicating a management request failure", () ->
                    assertThat(response.extract().statusCode())
                            .as("Expected management request failure (400 or 500)")
                            .isIn(400, 500));
        });
    }

    // MIGRATED-FROM: 0400-ss-system-parameters.feature :: "Timestamping service is selected and deleted"
    @Test
    @ResourceLock("timestamping")
    @DisplayName("Timestamping service added then deleted leaves the configured list empty")
    void timestampingServiceAddedThenDeleted(SsBaselineSeeder seeder) {
        var systemParams = new SystemParametersAdminClient(seeder.newSession());
        var tsa = new TimestampingServiceDto(TEST_TSA_NAME, TEST_TSA_URL, CostTypeDto.FREE);

        List<Map<String, Object>> priorServices = given("current timestamping services captured for restore", () ->
                systemParams.listTimestampingServicesRaw());

        try {
            given("any pre-existing Test TSA is removed", () ->
                    priorServices.stream()
                            .filter(s -> TEST_TSA_URL.equals(s.get("url")))
                            .forEach(s -> systemParams.deleteTimestampingService(tsa).statusCode(204)));

            when("the Test TSA is added", () ->
                    systemParams.addTimestampingService(tsa)
                            .statusCode(201));

            then("the configured list has exactly one entry (the Test TSA)", () -> {
                var services = systemParams.listTimestampingServicesRaw();
                assertThat(services).hasSize(1);
            });

            when("the Test TSA is deleted", () ->
                    systemParams.deleteTimestampingService(tsa)
                            .statusCode(204));

            then("the configured list is empty", () -> {
                var services = systemParams.listTimestampingServicesRaw();
                assertThat(services).isEmpty();
            });
        } finally {
            restoreTimestampingServices(systemParams, priorServices);
        }
    }

    // MIGRATED-FROM: 0400-ss-system-parameters.feature :: "Timestamping service is selected"
    @Test
    @ResourceLock("timestamping")
    @DisplayName("Timestamping service added persists expected name, url, cost type, and PAID_FIRST strategy")
    void timestampingServiceSelectedPersistsValues(SsBaselineSeeder seeder) {
        var systemParams = new SystemParametersAdminClient(seeder.newSession());
        var tsa = new TimestampingServiceDto(TEST_TSA_NAME, TEST_TSA_URL, CostTypeDto.FREE);

        List<Map<String, Object>> priorServices = given("current timestamping services captured for restore", () ->
                systemParams.listTimestampingServicesRaw());

        try {
            given("any pre-existing Test TSA is removed", () ->
                    priorServices.stream()
                            .filter(s -> TEST_TSA_URL.equals(s.get("url")))
                            .forEach(s -> systemParams.deleteTimestampingService(tsa).statusCode(204)));

            when("the Test TSA is added", () ->
                    systemParams.addTimestampingService(tsa)
                            .statusCode(201));

            then("the configured list has one entry with name 'Test TSA', url and cost type 'Free'", () -> {
                var services = systemParams.listTimestampingServicesRaw();
                assertThat(services).hasSize(1);
                var entry = services.get(0);
                assertThat(entry.get("name")).isEqualTo(TEST_TSA_NAME);
                assertThat(entry.get("url")).isEqualTo(TEST_TSA_URL);
                assertThat(entry.get("cost_type")).isEqualTo("FREE");
            });

            and("the timestamping prioritization strategy is PAID_FIRST (globalconf-derived)", () -> {
                var strategy = systemParams.getTimestampingPrioritizationStrategy();
                assertThat(strategy).isEqualTo("PAID_FIRST");
            });
        } finally {
            restoreTimestampingServices(systemParams, priorServices);
        }
    }

    // MIGRATED-FROM: 0400-ss-system-parameters.feature :: "Approved CA component has correct values"
    @Test
    @DisplayName("Approved CAs list row 0 has expected distinguished name, OCSP url, cost type, and ONLY_FREE strategy")
    void approvedCaHasCorrectValues(SsBaselineSeeder seeder) {
        var systemParams = new SystemParametersAdminClient(seeder.newSession());

        var cas = given("the approved CAs list is retrieved", () ->
                systemParams.listApprovedCertificateAuthoritiesRaw());

        then("the list is non-empty", () ->
                assertThat(cas).isNotEmpty());

        and("row 0 has distinguished name 'CN=Test CA, O=Test'", () -> {
            var firstCa = cas.get(0);
            var subjectDn = (String) firstCa.get("subject_distinguished_name");
            assertThat(subjectDn).contains("CN=Test CA").contains("O=Test");
        });

        and("row 0 has an OCSP responder at 'http://testca:8888' with cost type FREE", () -> {
            var firstCa = cas.get(0);
            @SuppressWarnings("unchecked")
            var ocspResponders = (List<Map<String, Object>>) firstCa.get("ocsp_responders");
            assertThat(ocspResponders).isNotEmpty();
            var firstOcsp = ocspResponders.get(0);
            assertThat(firstOcsp.get("url")).isEqualTo("http://testca:8888");
            assertThat(firstOcsp.get("cost_type")).isEqualTo("FREE");
        });

        and("the OCSP prioritization strategy is ONLY_FREE (globalconf-distributed)", () -> {
            var strategy = systemParams.getOcspPrioritizationStrategy();
            assertThat(strategy).isEqualTo("ONLY_FREE");
        });
    }

    // MIGRATED-FROM: 0400-ss-system-parameters.feature :: "Configurable property can be edited and restart warning is shown"
    @Test
    @ResourceLock("configurable-property")
    @DisplayName("Configurable property updated via API persists the new value (API slice; restart-warning render is UI integration)")
    void configurablePropertyUpdatedPersists(SsBaselineSeeder seeder) {
        var systemParams = new SystemParametersAdminClient(seeder.newSession());

        var priorValue = given("the effective value of the rate-limit property is captured for restore", () ->
                systemParams.getConfigurablePropertyEffectiveValue(RATE_LIMIT_PROP));

        try {
            when("the rate-limit property is updated to '25'", () ->
                    systemParams.updateConfigurableProperty(RATE_LIMIT_PROP, "25", RATE_LIMIT_SCOPE)
                            .statusCode(204));

            then("the current value of the property is now '25'", () -> {
                var currentValue = systemParams.getConfigurablePropertyValue(RATE_LIMIT_PROP);
                assertThat(currentValue).isEqualTo("25");
            });
        } finally {
            if (priorValue != null) {
                systemParams.updateConfigurableProperty(RATE_LIMIT_PROP, priorValue, RATE_LIMIT_SCOPE)
                        .statusCode(204);
            }
        }
    }

    private void restoreTimestampingServices(SystemParametersAdminClient systemParams,
                                             List<Map<String, Object>> priorServices) {
        var tsa = new TimestampingServiceDto(TEST_TSA_NAME, TEST_TSA_URL, CostTypeDto.FREE);
        var currentServices = systemParams.listTimestampingServicesRaw();
        boolean tsaCurrentlyPresent = currentServices.stream()
                .anyMatch(s -> TEST_TSA_URL.equals(s.get("url")));
        boolean tsaWasPresentBefore = priorServices.stream()
                .anyMatch(s -> TEST_TSA_URL.equals(s.get("url")));
        if (tsaCurrentlyPresent && !tsaWasPresentBefore) {
            systemParams.deleteTimestampingService(tsa).statusCode(204);
        } else if (!tsaCurrentlyPresent && tsaWasPresentBefore) {
            systemParams.addTimestampingService(tsa).statusCode(201);
        }
    }
}
