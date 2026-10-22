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
import org.junit.jupiter.api.AfterAll;
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
import org.niis.xroad.e2e.parity.acl.AclParticipants;
import org.niis.xroad.e2e.parity.acl.AclScenario;
import org.niis.xroad.e2e.parity.acl.AclScenario.Expectation;
import org.niis.xroad.e2e.parity.acl.AclScenario.ListClientsRequest;
import org.niis.xroad.e2e.parity.acl.AclScenario.ManagementRequest;
import org.niis.xroad.e2e.parity.acl.AclScenario.MetaserviceRequest;
import org.niis.xroad.e2e.parity.acl.AclScenario.MonitoringRequest;
import org.niis.xroad.e2e.parity.acl.AclScenario.ProviderHost;
import org.niis.xroad.e2e.parity.acl.AclScenario.RequestSpec;
import org.niis.xroad.e2e.parity.acl.AclScenario.RestRequest;
import org.niis.xroad.e2e.parity.acl.AclScenario.SoapRequest;
import org.niis.xroad.e2e.parity.acl.AclScenario.Transport;
import org.niis.xroad.e2e.parity.acl.AclScenarioVerdicts;
import org.niis.xroad.e2e.parity.acl.AclScenarioVerdicts.Outcome;
import org.niis.xroad.e2e.parity.acl.FixtureExecutor;
import org.niis.xroad.e2e.parity.acl.FixturePlan;
import org.niis.xroad.e2e.parity.acl.FixturePlanner;
import org.niis.xroad.e2e.parity.acl.KnownDefectMarker;
import org.niis.xroad.e2e.parity.acl.KnownDefectMarker.Judgement;
import org.niis.xroad.e2e.parity.acl.Reply;
import org.niis.xroad.e2e.parity.acl.RestRequests;
import org.niis.xroad.e2e.parity.acl.RestSender;
import org.niis.xroad.e2e.parity.acl.SoapRequests;
import org.niis.xroad.e2e.parity.acl.VerdictClassifier;
import org.niis.xroad.e2e.parity.acl.VerdictPoller;
import org.niis.xroad.e2e.parity.acl.VerdictPoller.Observation;
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
 * negotiation, so ss0 decides by its access rights alone. Every scenario creates namespaced fixtures on its
 * provider's Security Server (ss0 unless the fixture names ss1), polls until the expected verdict or the cache
 * deadline, and removes its fixtures. A scenario that expects an admin API status instead issues one grant and
 * checks the status, without polling.
 *
 * <p>A scenario with a known-defect marker asserts the safe verdict and is probed once. While it misses that
 * verdict it is aborted with the reason "expected failure", which Gradle reports as skipped; once it meets the
 * verdict it fails with "defect fixed, remove marker".
 *
 * <p>Before the first scenario the shared {@link SubsystemPoolSetup subsystem pool} is registered once, and the
 * service clients holding the management client registration service are listed; after the last scenario the list
 * must be the same.
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

    private static List<String> managementGrantsBefore;

    @BeforeAll
    static void registerSubsystemPool(E2eEnvironment env) {
        assumeTrue(AclParityProperties.configuredMode().isPresent(), "ACL parity runs only with -Pe2e.acl-mode");
        var ss1BaseUrl = adminBaseUrl(env, SS1_ENV);
        AdminApi.setConnectionType(ss1BaseUrl, login(ss1BaseUrl), AclParticipants.MEMBER_OF_CONSUMER, "HTTP");
        SubsystemPoolSetup.ensure(env);
        managementGrantsBefore = managementGrants(env);
    }

    @AfterAll
    static void managementGrantsAreRestored(E2eEnvironment env) {
        if (managementGrantsBefore != null) {
            Assertions.assertEquals(managementGrantsBefore, managementGrants(env),
                    "service clients holding %s of %s on ss0".formatted(
                            AclParticipants.CLIENT_REG, AclParticipants.MANAGEMENT));
        }
    }

    private static List<String> managementGrants(E2eEnvironment env) {
        var ss0BaseUrl = adminBaseUrl(env, SS0_ENV);
        return AdminApi.serviceClientsWithAccess(ss0BaseUrl, login(ss0BaseUrl), AclParticipants.MANAGEMENT,
                AclParticipants.CLIENT_REG);
    }

    static Stream<Named<AclScenario>> scenarios() {
        return AclScenarioCatalogue.all().stream()
                .map(scenario -> Named.of(displayName(scenario), scenario));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void scenarioGetsItsExpectedVerdict(AclScenario scenario, E2eEnvironment env) {
        assumeTrue(AclParityProperties.configuredMode().isPresent(), "ACL parity runs only with -Pe2e.acl-mode");

        var providerBaseUrl = adminBaseUrl(env, providerEnv(scenario));
        var csBaseUrl = CentralServerApi.baseUrl(env);
        var gateway = new AdminApiGateway(providerBaseUrl, login(providerBaseUrl), backendAdminUrl(env), csBaseUrl,
                login(csBaseUrl));
        var plan = new FixturePlanner(RUN_TOKEN).plan(scenario);

        try (var management = new ManagementProbe(adminBaseUrl(env, SS1_ENV), RUN_TOKEN, scenario.id());
                var fixture = new FixtureExecutor(gateway).open(plan)) {
            if (scenario.adminGrant() != null) {
                assertAdminGrantStatus(scenario, plan, gateway);
                return;
            }
            var outcomes = AclScenarioVerdicts.pollPhased(new VerdictPoller(), scenario, plan, fixture::apply,
                    request -> call(env, management, scenario, plan, request),
                    scenario.knownDefect() == null ? scenario.waitClass().timeout() : Duration.ZERO,
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

    private static void assertAdminGrantStatus(AclScenario scenario, FixturePlan plan, AdminApiGateway gateway) {
        var grant = scenario.adminGrant();
        var status = gateway.requestAccessGrant(scenario.fixture().providerClientId(), grant.subjectId(),
                plan.serviceCodes().get(grant.serviceIndex()));
        log.info("{}: grant to {} -> admin API status {}", scenario.id(), grant.subjectId(), status);
        Assertions.assertEquals(grant.expectedStatus(), status,
                "admin API status of the grant to " + grant.subjectId());
    }

    private static String providerEnv(AclScenario scenario) {
        return scenario.fixture().providerHost() == ProviderHost.SS1 ? SS1_ENV : SS0_ENV;
    }

    private static String backendAdminUrl(E2eEnvironment env) {
        var mapping = env.getContainerMapping(CentralServerApi.CS_ENV, AuxStackSetup.ISREST,
                AuxStackSetup.Port.ISREST);
        return "http://%s:%s".formatted(mapping.host(), mapping.port());
    }

    private static String displayName(AclScenario scenario) {
        var name = scenario.id() + " " + scenario.description();
        return scenario.knownDefect() == null ? name : name + " [known defect " + scenario.knownDefect() + "]";
    }

    private static void report(AclScenario scenario, List<Outcome> outcomes, Judgement judgement) {
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

    private static Observation call(E2eEnvironment env, ManagementProbe management, AclScenario scenario,
                                    FixturePlan plan, RequestSpec request) {
        var proxy = env.getContainerMapping(SS1_ENV, SsStackSetup.PROXY, SsStackSetup.Port.PROXY);
        var proxyOrigin = "http://%s:%s".formatted(proxy.host(), proxy.port());
        var providerClientId = scenario.fixture().providerClientId();
        return switch (request) {
            case RestRequest rest -> {
                var serviceCode = plan.serviceCodes().get(rest.serviceIndex());
                var reply = RestSender.send(rest.method(),
                        RestRequests.url(proxyOrigin, providerClientId, serviceCode, rest.path()),
                        RestRequests.clientHeader(rest.consumerClientId()));
                yield new Observation(VerdictClassifier.classify(reply), VerdictClassifier.describe(reply));
            }
            case SoapRequest soap -> {
                var serviceCode = plan.serviceCodes().get(soap.serviceIndex());
                var reply = Replies.toReply(RestAssuredFactory.given()
                        .header("Content-Type", "text/xml")
                        .body(SoapRequests.envelope(soap.consumerClientId(), providerClientId, serviceCode,
                                soap.serviceVersion(), soap.securityServerHint(), "acl-parity-" + UUID.randomUUID()))
                        .post(proxyOrigin));
                yield new Observation(VerdictClassifier.classifySoap(reply), VerdictClassifier.describeSoap(reply));
            }
            case MetaserviceRequest meta -> callMetaservice(proxyOrigin, providerClientId, plan, meta);
            case MonitoringRequest monitoring -> {
                var reply = Replies.toReply(RestAssuredFactory.given()
                        .header("Content-Type", "text/xml")
                        .body(SoapRequests.monitoringEnvelope(monitoring.consumerClientId(), providerClientId,
                                monitoring.code(), AclParticipants.OWNER_SECURITY_SERVER,
                                monitoring.filterClientId(), "acl-parity-" + UUID.randomUUID()))
                        .post(proxyOrigin));
                yield observe(reply, plan, monitoring.expectation());
            }
            case ListClientsRequest listClients -> observe(
                    RestSender.send("GET", RestRequests.listClientsUrl(proxyOrigin), null), plan,
                    listClients.expectation());
            case ManagementRequest registration -> {
                var reply = management.register(registration.consumerClientId());
                yield new Observation(VerdictClassifier.classifyManagement(reply),
                        VerdictClassifier.describeManagement(reply));
            }
        };
    }

    private static Observation callMetaservice(String proxyOrigin, String providerClientId, FixturePlan plan,
                                               MetaserviceRequest meta) {
        var target = meta.serviceIndex() == MetaserviceRequest.NO_SERVICE
                ? null : plan.serviceCodes().get(meta.serviceIndex());
        var reply = meta.transport() == Transport.REST
                ? RestSender.send(meta.method(),
                        RestRequests.metaserviceUrl(proxyOrigin, providerClientId, meta.code(), target),
                        RestRequests.clientHeader(meta.consumerClientId()), "application/json")
                : Replies.toReply(RestAssuredFactory.given()
                        .header("Content-Type", "text/xml")
                        .body(SoapRequests.metaserviceEnvelope(meta.consumerClientId(), providerClientId, meta.code(),
                                target, meta.targetVersion(), "acl-parity-" + UUID.randomUUID()))
                        .post(proxyOrigin));
        return observe(reply, plan, meta.expectation());
    }

    private static Observation observe(Reply reply, FixturePlan plan, Expectation expectation) {
        var required = expectation.required(plan.serviceCodes());
        var forbidden = expectation.forbidden(plan.serviceCodes());
        return new Observation(VerdictClassifier.classifyMetaservice(reply, required, forbidden),
                VerdictClassifier.describeMetaservice(reply, required, forbidden));
    }
}
