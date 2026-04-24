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

import feign.Feign;
import feign.Logger;
import feign.codec.Decoder;
import feign.codec.Encoder;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.ss.test.SsSystemTestContainerSetup;
import org.niis.xroad.ss.test.addons.api.FeignHealthApi;
import org.niis.xroad.ss.test.ui.container.Port;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.openfeign.support.SpringMvcContract;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.StreamSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.ss.test.SsSystemTestContainerSetup.PROXY;
import static org.testcontainers.shaded.org.awaitility.Awaitility.await;

/**
 * Shared helper for MicroProfile Health check assertions (readiness and liveness).
 */
@Slf4j
@Component
@SuppressWarnings({"checkstyle:MagicNumber", "SpringJavaAutowiredMembersInspection"})
public class HealthCheckAssertions {

    @Autowired
    private SsSystemTestContainerSetup systemTestContainerSetup;
    @Autowired
    private Decoder decoder;
    @Autowired
    private Encoder encoder;

    private final Map<String, FeignHealthApi> clientCache = new HashMap<>();

    public void assertOverallStatusIsUp(String serviceName,
                                        Function<FeignHealthApi, ResponseEntity<JsonNode>> endpoint) {
        var client = getHealthCheckClient(serviceName);
        await()
                .pollDelay(Duration.ZERO)
                .pollInterval(5, TimeUnit.SECONDS)
                .atMost(60, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    log.info("Checking health for service: {}", serviceName);
                    var response = endpoint.apply(client);
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    JsonNode root = response.getBody();
                    assertThat(root.get("status").asString())
                            .as("overall status for service '%s'", serviceName)
                            .isEqualTo("UP");
                    log.info("Service {} health check passed. Response: {}", serviceName, response.getBody());
                });
    }

    public void assertCheckHasStatus(String serviceName, String checkName, String expectedStatus,
                                     Function<FeignHealthApi, ResponseEntity<JsonNode>> endpoint) {
        var client = getHealthCheckClient(serviceName);
        await()
                .pollDelay(Duration.ZERO)
                .pollInterval(2, TimeUnit.SECONDS)
                .atMost(15, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    log.info("Checking health for service: {}", serviceName);
                    var response = endpoint.apply(client);
                    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
                    JsonNode root = response.getBody();
                    var match = StreamSupport.stream(root.get("checks").spliterator(), false)
                            .filter(c -> checkName.equals(c.get("name").asString()))
                            .findFirst();
                    assertThat(match).as("check '%s' not found in response", checkName).isPresent();
                    assertThat(match.get().get("status").asString())
                            .as("status of check '%s'", checkName)
                            .isEqualTo(expectedStatus);
                    log.info("Service {} check '{}' status is {}", serviceName, checkName, expectedStatus);
                });
    }

    private FeignHealthApi getHealthCheckClient(String serviceName) {
        return clientCache.computeIfAbsent(serviceName, name -> {
            var port = PROXY.equalsIgnoreCase(name) ? Port.PROXY_HEALTHCHECK : Port.QUARKUS_HEALTH;
            var container = systemTestContainerSetup.getContainerMapping(name, port);
            return Feign.builder()
                    .logLevel(Logger.Level.FULL)
                    .encoder(encoder)
                    .decoder(decoder)
                    .contract(new SpringMvcContract())
                    .target(FeignHealthApi.class, "http://%s:%d".formatted(container.host(), container.port()));
        });
    }
}
