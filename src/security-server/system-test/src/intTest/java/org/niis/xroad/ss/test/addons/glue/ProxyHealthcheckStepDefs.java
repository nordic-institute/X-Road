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

package org.niis.xroad.ss.test.addons.glue;

import com.codeborne.selenide.Selenide;
import feign.FeignException;
import io.cucumber.java.en.Step;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.ss.test.SsSystemTestContainerSetup;
import org.niis.xroad.ss.test.addons.api.FeignHealthcheckApi;
import org.niis.xroad.ss.test.addons.api.HealthResponse;
import org.niis.xroad.ss.test.ui.glue.BaseUiStepDefs;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.testcontainers.shaded.org.awaitility.core.ThrowingRunnable;
import tools.jackson.databind.json.JsonMapper;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.testcontainers.shaded.org.awaitility.Awaitility.given;

@Slf4j
@SuppressWarnings({"checkstyle:MagicNumber", "checkstyle:SneakyThrowsCheck"})
public class ProxyHealthcheckStepDefs extends BaseUiStepDefs {

    @Autowired
    private SsSystemTestContainerSetup systemTestContainerSetup;

    @Autowired
    private FeignHealthcheckApi healthcheckApi;

    private final JsonMapper jsonMapper = JsonMapper.builder().build();

    @SneakyThrows
    @Step("^service \"(.*)\" is \"(stopped|started|restarted)\"$")
    public void stopService(String service, String state) {
        switch (state) {
            case "restarted":
                systemTestContainerSetup.restartContainer(service);
                break;
            case "stopped":
                systemTestContainerSetup.stop(service);
                break;
            case "started":
                systemTestContainerSetup.start(service, true);
                break;
            default:
                throw new IllegalStateException("unexpected state: " + state);
        }
        log.info("Grace period after service {} {}", service, state);
        Selenide.sleep(5000);
    }

    @Step("healthcheck has no errors")
    public void validateHealthcheckNoErrors() {
        assertWithWait(() -> {
            log.info("Polling for HealthCheck update..");
            try {
                var result = healthcheckApi.getHealthcheck();
                assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
                assertThat(result.getBody().isUp()).isTrue();
            } catch (FeignException e) {
                throw new AssertionError("Healthcheck is in error state: " + e.contentUTF8());
            }
        });
    }

    @Step("Proxy healthcheck check {string} is {string}")
    public void validateHealthcheck(String checkName, String expectedStatus) {
        assertWithWait(() -> {
            HealthResponse response = getHealthcheckResponse();

            var match = response.checks().stream()
                    .filter(check -> checkName.equals(check.name()))
                    .findFirst();
            assertThat(match).as("check '%s' not found in healthcheck response", checkName).isPresent();
            assertThat(match.get().status()).isEqualTo(expectedStatus);
        });
    }

    @Step("Proxy healthcheck check {string} is {string} with status {string}")
    public void validateHealthcheckWithStatus(String checkName, String expectedStatus, String dataStatus) {
        assertWithWait(() -> {
            HealthResponse response = getHealthcheckResponse();

            var match = response.checks().stream()
                    .filter(check -> checkName.equals(check.name()))
                    .findFirst();
            assertThat(match).as("check '%s' not found in healthcheck response", checkName).isPresent();
            assertThat(match.get().status()).isEqualTo(expectedStatus);
            assertThat(match.get().data().get("status"))
                    .as("data.status of check '%s'", checkName)
                    .isEqualTo(dataStatus);
        });
    }

    @Step("Proxy healthcheck check {string} data {string} is {int}")
    public void validateHealthcheckCheckDataInt(String checkName, String dataKey, int expected) {
        assertWithWait(() -> {
            HealthResponse response = getHealthcheckResponse();

            var match = response.checks().stream()
                    .filter(check -> checkName.equals(check.name()))
                    .findFirst();
            assertThat(match).as("check '%s' not found in healthcheck response", checkName).isPresent();
            assertThat(match.get().data())
                    .as("data of check '%s'", checkName)
                    .containsEntry(dataKey, expected);
        });
    }

    private HealthResponse getHealthcheckResponse() {
        try {
            return healthcheckApi.getHealthcheck().getBody();
        } catch (FeignException feignException) {
            return jsonMapper.readerFor(HealthResponse.class).readValue(feignException.contentUTF8());
        }
    }

    private void assertWithWait(ThrowingRunnable assertion) {
        final int pollInterval = 5;
        final int pollDelay = 5;
        final int maxWaitTime = 80;
        given()
                .pollDelay(pollDelay, TimeUnit.SECONDS)
                .pollInterval(pollInterval, TimeUnit.SECONDS)
                .pollInSameThread()
                .atMost(maxWaitTime, TimeUnit.SECONDS)
                .await().untilAsserted(assertion);
    }

    @Step("HSM health check is enabled on proxy")
    public void hsmHealthCheckIsEnabled() {
        testDatabasePropertyService.putProperty("xroad.proxy.hsm-health-check-enabled", "true", "proxy");
    }

    @Step("HSM health check is disabled on proxy")
    public void hsmHealthCheckIsDisabled() {
        testDatabasePropertyService.putProperty("xroad.proxy.hsm-health-check-enabled", "false", "proxy");
    }

}
