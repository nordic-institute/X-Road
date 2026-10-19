/*
 * The MIT License
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
package org.niis.xroad.e2e;

import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.niis.xroad.e2e.container.SsStackSetup;
import org.niis.xroad.e2e.parity.FixtureExecutor;
import org.niis.xroad.e2e.parity.FixturePlanner;
import org.niis.xroad.e2e.parity.Reply;
import org.niis.xroad.e2e.parity.RestRequests;
import org.niis.xroad.e2e.parity.Scenario;
import org.niis.xroad.e2e.parity.ScenarioCatalogue;
import org.niis.xroad.e2e.parity.Verdict;
import org.niis.xroad.e2e.parity.VerdictClassifier;
import org.niis.xroad.e2e.parity.VerdictPoller;
import org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.niis.xroad.e2e.AdminApi.adminBaseUrl;
import static org.niis.xroad.e2e.AdminApi.login;

/**
 * Records the verdict legacy access rights give for each catalogue scenario: ss1 calls ss0 without data space
 * negotiation, so ss0 decides by its access rights alone. Every scenario creates namespaced fixtures on ss0,
 * polls until the expected verdict or the cache deadline, and removes its fixtures.
 *
 * <p>Runs only when {@code -Pe2e.acl-mode} is set; otherwise every scenario is skipped. Single-threaded and
 * ordered after every other class, and a failing scenario does not stop the next one: the Gradle task sets
 * the fail-fast threshold to {@code 0} in that mode.
 */
@Slf4j
@Tag("acl-parity")
@Order(500)
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
@DisplayName("ACL parity - legacy access rights decide service calls from ss1 to ss0")
class AclParityTest extends E2eTest {

    private static final String SS0_ENV = "ss0";
    private static final String SS1_ENV = "ss1";
    private static final String RUN_TOKEN = Long.toString(Instant.now().getEpochSecond(), Character.MAX_RADIX);

    static Stream<Named<Scenario>> scenarios() {
        return ScenarioCatalogue.all().stream()
                .map(scenario -> Named.of(scenario.id() + " " + scenario.description(), scenario));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void scenarioGetsItsExpectedVerdict(Scenario scenario, E2eEnvironment env) {
        assumeTrue(AclParityProperties.configuredMode().isPresent(), "ACL parity runs only with -Pe2e.acl-mode");

        var ss0BaseUrl = adminBaseUrl(env, SS0_ENV);
        var gateway = new AdminApiGateway(ss0BaseUrl, login(ss0BaseUrl));
        var plan = new FixturePlanner(RUN_TOKEN).plan(scenario.id(), scenario.fixture());

        try (var fixture = new FixtureExecutor(gateway).open(plan)) {
            var serviceCode = plan.serviceCodes().get(0);
            var result = new VerdictPoller().await(() -> call(env, scenario, serviceCode), scenario.expected(),
                    DisabledServiceFault.SERVERCONF_CACHE_EXPIRY_TIMEOUT,
                    DisabledServiceFault.SERVERCONF_CACHE_EXPIRY_POLL_INTERVAL);
            log.info("{}: verdict {} after {} attempts in {} s", scenario.id(), result.last(), result.attempts(),
                    result.elapsed().toSeconds());
            assertTrue(result.matched(), () -> result.failureMessage(scenario.id(), scenario.expected()));
        }
    }

    private static Verdict call(E2eEnvironment env, Scenario scenario, String serviceCode) {
        var proxy = env.getContainerMapping(SS1_ENV, SsStackSetup.PROXY, SsStackSetup.Port.PROXY);
        var request = scenario.request();
        var url = RestRequests.url("http://%s:%s".formatted(proxy.host(), proxy.port()),
                scenario.fixture().providerClientId(), serviceCode, request.path());
        var response = RestAssuredFactory.given()
                .header(RestRequests.CLIENT_HEADER, RestRequests.clientHeader(request.consumerClientId()))
                .request(request.method(), url);
        return VerdictClassifier.classify(toReply(response));
    }

    private static Reply toReply(Response response) {
        var headers = new LinkedHashMap<String, String>();
        response.getHeaders().asList().forEach(header -> headers.putIfAbsent(header.getName(), header.getValue()));
        return new Reply(response.getStatusCode(), headers, response.getBody().asString());
    }
}
