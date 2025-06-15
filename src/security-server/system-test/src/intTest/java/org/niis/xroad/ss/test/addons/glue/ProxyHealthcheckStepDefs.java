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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import feign.FeignException;
import io.cucumber.java.en.Step;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.core.ThrowingRunnable;
import org.niis.xroad.common.test.glue.BaseStepDefs;
import org.niis.xroad.ss.test.addons.api.FeignHealthcheckApi;
import org.niis.xroad.ss.test.ui.container.EnvSetup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.given;

@Slf4j
@SuppressWarnings("checkstyle:MagicNumber")
public class ProxyHealthcheckStepDefs extends BaseStepDefs {

    @Autowired
    private EnvSetup envSetup;

    @Autowired
    private FeignHealthcheckApi healthcheckApi;

    @Step("^service \"(.*)\" is \"(stopped|started|restarted)\"$")
    public void stopService(String service, String state) {
        switch (state) {
            case "restarted":
                envSetup.restartContainer(service);
                break;
            case "stopped":
                envSetup.stop(service);
                break;
            case "started":
                envSetup.start(service, true);
                break;
            default:
                throw new IllegalStateException("unexpected state: " + state);
        }
    }

    @Step("healthcheck has no errors")
    public void validateHealthcheckNoErrors() {
        assertWithWait(() -> {
            log.info("Polling for HealthCheck update..");
            try {
                ResponseEntity<String> result = healthcheckApi.getHealthcheck();
                assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
                log.info("HS had no error. {}. Body {}", result.getStatusCode(), result.getBody());
            } catch (FeignException e) {
                throw new AssertionError("Healthcheck is in error state: " + e.contentUTF8());
            }
        });
    }

    @Step("healthcheck has errors and error message is {string}")
    public void validateHealthcheckErrors(String errorMessage) {
        assertWithWait(() -> {
            try {
                var response = healthcheckApi.getHealthcheck();
                throw new AssertionError("Healthcheck is not in error state: " + response);
            } catch (FeignException feignException) {
                log.info("Polling for HealthCheck update..");
                assertThat(feignException.status()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
                assertThat(feignException.contentUTF8()).contains(errorMessage);
                testReportService.attachText("successful error message", errorMessage);
            }
        });
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
    public void hsmHealthCheckIsEnabled() throws IOException {
        setConfigValue("build/resources/intTest/container-files/etc/xroad/conf.d/local.yaml",
                "xroad.proxy.hsm-health-check-enabled", true);
    }

    @Step("HSM health check is disabled on proxy")
    public void hsmHealthCheckIsDisabled() throws IOException {
        setConfigValue("build/resources/intTest/container-files/etc/xroad/conf.d/local.yaml",
                "xroad.proxy.hsm-health-check-enabled", false);
    }

    public static void setConfigValue(String fileName, String key, Object value) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

        Map<String, Object> data = mapper.readValue(new File(fileName), Map.class);

        String[] keys = key.split("\\.");
        Map<String, Object> current = data;
        for (int i = 0; i < keys.length - 1; i++) {
            current = (Map<String, Object>) current.computeIfAbsent(keys[i], k -> new HashMap<>());
        }
        current.put(keys[keys.length - 1], value);
        mapper.writeValue(new File(fileName), data);
    }
}
