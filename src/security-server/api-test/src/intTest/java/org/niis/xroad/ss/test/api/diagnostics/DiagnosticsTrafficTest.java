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

import io.restassured.response.ValidatableResponse;
import lombok.extern.slf4j.Slf4j;
import org.awaitility.core.ConditionTimeoutException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.niis.xroad.securityserver.restapi.openapi.model.ClientAddDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ClientDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ClientStatusDto;
import org.niis.xroad.securityserver.restapi.openapi.model.ConnectionTypeDto;
import org.niis.xroad.ss.test.api.Port;
import org.niis.xroad.ss.test.api.SsApiTest;
import org.niis.xroad.ss.test.api.SsApiTestContainerSetup;
import org.niis.xroad.ss.test.api.admin.ClientsAdminClient;
import org.niis.xroad.ss.test.api.admin.DiagnosticsAdminClient;
import org.niis.xroad.ss.test.api.admin.SystemAdminClient;
import org.niis.xroad.ss.test.api.seeding.SsBaselineSeeder;
import org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.parallel.ResourceAccessMode.READ;
import static org.niis.xroad.test.apitest.core.junit.Step.given;
import static org.niis.xroad.test.apitest.core.junit.Step.then;
import static org.niis.xroad.test.apitest.core.junit.Step.when;

/**
 * API tests for Security Server diagnostics traffic tab: service-list population from configured service descriptions
 * and request counts read from the local op-monitor.
 */
@Slf4j
@DisplayName("Diagnostics traffic — service list population")
@ResourceLock(value = "op-monitor-ca", mode = READ)
@SuppressWarnings("checkstyle:magicnumber")
class DiagnosticsTrafficTest extends SsApiTest {

    private static final String JSON_SPEC_URL = "http://mock-server:1080/test-services/testopenapi2.json";
    private static final Duration RECORD_TIMEOUT = Duration.ofSeconds(20);
    private static final Duration RECORD_POLL_INTERVAL = Duration.ofSeconds(1);
    private static final Duration CLOCK_SKEW_MARGIN = Duration.ofMinutes(1);
    private static final String REGISTERED_SUBSYSTEM_CODE = "TestService";

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

    @Test
    @DisplayName("Traffic intervals count a request recorded by the proxy, read from the local op-monitor")
    void intervalsCountARecordedRequest(SsApiTestContainerSetup stack, SsBaselineSeeder seeder) {
        var clientId = given("a registered subsystem client with plain HTTP connection exists", () ->
                ensureRegisteredSubsystem(new ClientsAdminClient(seeder.newSession())));
        var clientPath = clientId.replace(':', '/');
        var serviceCode = "noSuchService" + System.nanoTime();
        var serviceId = "%s:%s".formatted(clientId, serviceCode);

        var proxyCall = when("a request for a nonexistent service is sent through the proxy as that client", () -> {
            var mapping = stack.getContainerMapping(SsApiTestContainerSetup.PROXY, Port.PROXY_HTTP);
            var sentAt = Instant.now();
            var response = RestAssuredFactory.given()
                    .header("X-Road-Client", clientPath)
                    .get("http://%s:%d/r1/%s/%s".formatted(mapping.host(), mapping.port(), clientPath, serviceCode));
            return new ProxyCall(sentAt, response.statusCode(), response.header("X-Road-Error"));
        });

        then("the intervals call for that service reports at least one request in some interval", () -> {
            var diagnostics = new DiagnosticsAdminClient(seeder.newSession());
            try {
                await("op-monitor record for " + serviceId)
                        .pollDelay(Duration.ZERO)
                        .pollInterval(RECORD_POLL_INTERVAL)
                        .atMost(RECORD_TIMEOUT)
                        .logging(log::info)
                        .untilAsserted(() -> assertThat(recordedRequestCount(diagnostics, proxyCall.sentAt(), serviceId)).isPositive());
            } catch (ConditionTimeoutException e) {
                throw new AssertionError(timeoutReport(seeder, clientId, serviceId, proxyCall), e);
            }
        });
    }

    private static String ensureRegisteredSubsystem(ClientsAdminClient clients) {
        var clientId = "DEV:%s:%s:%s".formatted(SsBaselineSeeder.SS_OWNER_CLASS, SsBaselineSeeder.SS_OWNER_CODE,
                REGISTERED_SUBSYSTEM_CODE);
        if (clients.findClientByIdentifier(clientId) == null) {
            var clientDto = new ClientDto(SsBaselineSeeder.SS_OWNER_CLASS, SsBaselineSeeder.SS_OWNER_CODE)
                    .subsystemCode(REGISTERED_SUBSYSTEM_CODE)
                    .connectionType(ConnectionTypeDto.HTTP);
            clients.addClient(new ClientAddDto(clientDto).ignoreWarnings(true)).statusCode(201);
        }
        assertThat(clients.findClientByIdentifier(clientId).getStatus()).isEqualTo(ClientStatusDto.REGISTERED);
        return clientId;
    }

    private static int recordedRequestCount(DiagnosticsAdminClient diagnostics, Instant sentAt, String serviceId) {
        return DiagnosticsAdminClient.totalRequestCount(intervalsAround(diagnostics, sentAt, serviceId)
                .statusCode(200)
                .extract()
                .jsonPath());
    }

    private static ValidatableResponse intervalsAround(DiagnosticsAdminClient diagnostics, Instant sentAt, String serviceId) {
        return diagnostics.getOperationalMonitoringIntervals(sentAt.minus(CLOCK_SKEW_MARGIN), Instant.now().plus(CLOCK_SKEW_MARGIN), 1,
                serviceId);
    }

    private static String timeoutReport(SsBaselineSeeder seeder, String clientId, String serviceId, ProxyCall proxyCall) {
        var session = seeder.newSession();
        var diagnostics = new DiagnosticsAdminClient(session);
        return """
                op-monitor reported no request for %s within %s
                  client: %s, sent at: %s, proxy response: HTTP %d, X-Road-Error: %s
                  query window: sent at - %s .. now + %s
                  requests for this service: %s, all requests in the window: %s
                  GET /system/op-monitor-ca: HTTP %d (204 = no imported CA)
                  all requests 0 means nothing reached op-monitor: check the proxy log for
                  'Trusting only the imported op-monitor CA' and 'Sending operational monitoring data failed'
                """.formatted(
                        serviceId, RECORD_TIMEOUT,
                        clientId, proxyCall.sentAt(), proxyCall.status(), proxyCall.xroadError(),
                        CLOCK_SKEW_MARGIN, CLOCK_SKEW_MARGIN,
                        describeCount(intervalsAround(diagnostics, proxyCall.sentAt(), serviceId)),
                        describeCount(intervalsAround(diagnostics, proxyCall.sentAt(), null)),
                        new SystemAdminClient(session).getOpMonitorCa().extract().statusCode());
    }

    private static String describeCount(ValidatableResponse intervals) {
        var response = intervals.extract();
        return response.statusCode() == 200
                ? String.valueOf(DiagnosticsAdminClient.totalRequestCount(response.jsonPath()))
                : "HTTP " + response.statusCode();
    }

    private record ProxyCall(Instant sentAt, int status, String xroadError) {
    }
}
