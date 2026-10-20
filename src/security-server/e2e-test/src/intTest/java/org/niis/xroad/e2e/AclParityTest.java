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

import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.niis.xroad.e2e.container.AuxStackSetup;
import org.niis.xroad.e2e.container.SsStackSetup;
import org.niis.xroad.e2e.parity.FixtureExecutor;
import org.niis.xroad.e2e.parity.FixturePlan;
import org.niis.xroad.e2e.parity.FixturePlanner;
import org.niis.xroad.e2e.parity.KnownDefectMarker;
import org.niis.xroad.e2e.parity.KnownDefectMarker.Judgement;
import org.niis.xroad.e2e.parity.RestRequests;
import org.niis.xroad.e2e.parity.RestSender;
import org.niis.xroad.e2e.parity.Scenario;
import org.niis.xroad.e2e.parity.Scenario.RequestSpec;
import org.niis.xroad.e2e.parity.Scenario.RestRequest;
import org.niis.xroad.e2e.parity.Scenario.SoapRequest;
import org.niis.xroad.e2e.parity.ScenarioCatalogue;
import org.niis.xroad.e2e.parity.ScenarioVerdicts;
import org.niis.xroad.e2e.parity.ScenarioVerdicts.Outcome;
import org.niis.xroad.e2e.parity.SoapRequests;
import org.niis.xroad.e2e.parity.VerdictClassifier;
import org.niis.xroad.e2e.parity.VerdictPoller;
import org.niis.xroad.e2e.parity.VerdictPoller.Observation;
import org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.niis.xroad.e2e.AdminApi.adminBaseUrl;
import static org.niis.xroad.e2e.AdminApi.login;

/**
 * Records the verdict legacy access rights give for each catalogue scenario: ss1 calls ss0 without data space
 * negotiation, so ss0 decides by its access rights alone. Every scenario creates namespaced fixtures on ss0,
 * polls until the expected verdict or the cache deadline, and removes its fixtures.
 *
 * <p>A scenario with a known-defect marker asserts the safe verdict and is probed once. While it misses that
 * verdict it is aborted with the reason "expected failure", which Gradle reports as skipped; once it meets the
 * verdict it fails with "defect fixed, remove marker".
 *
 * <p>Before the first scenario the shared {@link SubsystemPoolSetup subsystem pool} is registered once.
 *
 * <p>Runs only when {@code -Pe2e.acl-mode} is set; otherwise the pool is not registered and every scenario
 * is skipped. Single-threaded and ordered after every other class, and a failing scenario does not stop
 * the next one: the Gradle task sets the fail-fast threshold to {@code 0} in that mode.
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

    @BeforeAll
    static void registerSubsystemPool(E2eEnvironment env) {
        assumeTrue(AclParityProperties.configuredMode().isPresent(), "ACL parity runs only with -Pe2e.acl-mode");
        var ss1BaseUrl = adminBaseUrl(env, SS1_ENV);
        AdminApi.setConnectionType(ss1BaseUrl, login(ss1BaseUrl), ScenarioCatalogue.MEMBER_OF_CONSUMER, "HTTP");
        SubsystemPoolSetup.ensure(env);
    }

    static Stream<Named<Scenario>> scenarios() {
        return ScenarioCatalogue.all().stream()
                .map(scenario -> Named.of(displayName(scenario), scenario));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void scenarioGetsItsExpectedVerdict(Scenario scenario, E2eEnvironment env) {
        assumeTrue(AclParityProperties.configuredMode().isPresent(), "ACL parity runs only with -Pe2e.acl-mode");

        var ss0BaseUrl = adminBaseUrl(env, SS0_ENV);
        var gateway = new AdminApiGateway(ss0BaseUrl, login(ss0BaseUrl), backendAdminUrl(env));
        var plan = new FixturePlanner(RUN_TOKEN).plan(scenario);

        try (var fixture = new FixtureExecutor(gateway).open(plan)) {
            var outcomes = ScenarioVerdicts.pollPhased(new VerdictPoller(), scenario, plan, fixture::apply,
                    request -> call(env, scenario, plan, request),
                    scenario.knownDefect() == null ? DisabledServiceFault.SERVERCONF_CACHE_EXPIRY_TIMEOUT
                            : Duration.ZERO,
                    DisabledServiceFault.SERVERCONF_CACHE_EXPIRY_POLL_INTERVAL);
            if (scenario.knownDefect() == null) {
                outcomes.forEach(outcome -> log.info("{}: {} -> verdict {} ({}) after {} attempts in {} s",
                        scenario.id(), outcome.check().request().describe(), outcome.result().last(),
                        outcome.result().detail(), outcome.result().attempts(),
                        outcome.result().elapsed().toSeconds()));
            }
            report(scenario, outcomes, KnownDefectMarker.judge(scenario, outcomes));
        }
    }

    private static String backendAdminUrl(E2eEnvironment env) {
        var mapping = env.getContainerMapping(CentralServerApi.CS_ENV, AuxStackSetup.ISREST,
                AuxStackSetup.Port.ISREST);
        return "http://%s:%s".formatted(mapping.host(), mapping.port());
    }

    private static String displayName(Scenario scenario) {
        var name = scenario.id() + " " + scenario.description();
        return scenario.knownDefect() == null ? name : name + " [known defect " + scenario.knownDefect() + "]";
    }

    private static void report(Scenario scenario, List<Outcome> outcomes, Judgement judgement) {
        if (scenario.knownDefect() != null) {
            log.info("{}: known defect {}: {}, observed {}", scenario.id(), scenario.knownDefect(), judgement.status(),
                    observedVerdicts(outcomes));
        }
        if (judgement.status() == KnownDefectMarker.Status.FAILED) {
            Assertions.fail(judgement.message());
        }
        if (judgement.status() == KnownDefectMarker.Status.EXPECTED_FAILURE) {
            Assumptions.abort(judgement.message());
        }
    }

    private static String observedVerdicts(List<Outcome> outcomes) {
        return outcomes.stream().map(outcome -> outcome.result().last().name()).collect(Collectors.joining(", "));
    }

    private static Observation call(E2eEnvironment env, Scenario scenario, FixturePlan plan, RequestSpec request) {
        var proxy = env.getContainerMapping(SS1_ENV, SsStackSetup.PROXY, SsStackSetup.Port.PROXY);
        var proxyOrigin = "http://%s:%s".formatted(proxy.host(), proxy.port());
        var providerClientId = scenario.fixture().providerClientId();
        var serviceCode = plan.serviceCodes().get(request.serviceIndex());
        return switch (request) {
            case RestRequest rest -> {
                var reply = RestSender.send(rest.method(),
                        RestRequests.url(proxyOrigin, providerClientId, serviceCode, rest.path()),
                        RestRequests.clientHeader(rest.consumerClientId()));
                yield new Observation(VerdictClassifier.classify(reply), VerdictClassifier.describe(reply));
            }
            case SoapRequest soap -> {
                var reply = Replies.toReply(RestAssuredFactory.given()
                        .header("Content-Type", "text/xml")
                        .body(SoapRequests.envelope(soap.consumerClientId(), providerClientId, serviceCode,
                                soap.serviceVersion(), soap.securityServerHint(), "acl-parity-" + UUID.randomUUID()))
                        .post(proxyOrigin));
                yield new Observation(VerdictClassifier.classifySoap(reply), VerdictClassifier.describeSoap(reply));
            }
        };
    }
}
