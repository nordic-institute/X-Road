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

import io.restassured.path.json.JsonPath;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.niis.xroad.common.properties.config.keys.ProxyConfigKeys;
import org.niis.xroad.e2e.container.AuxStackSetup;
import org.niis.xroad.e2e.container.E2eEnvSetup;
import org.niis.xroad.e2e.container.SsStackSetup;
import org.niis.xroad.e2e.parity.acl.AclMode;
import org.niis.xroad.e2e.parity.acl.AclModeProbe;
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
import org.niis.xroad.e2e.parity.acl.AclVerdictReport;
import org.niis.xroad.e2e.parity.acl.Reply;
import org.niis.xroad.e2e.parity.acl.RestRequests;
import org.niis.xroad.e2e.parity.acl.RestSender;
import org.niis.xroad.e2e.parity.acl.ScenarioFixture;
import org.niis.xroad.e2e.parity.acl.SoapRequests;
import org.niis.xroad.e2e.parity.acl.VerdictClassifier;
import org.niis.xroad.e2e.parity.acl.VerdictPoller;
import org.niis.xroad.e2e.parity.acl.VerdictPoller.Observation;
import org.niis.xroad.test.apitest.core.restassured.RestAssuredFactory;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.niis.xroad.e2e.AdminApi.adminBaseUrl;
import static org.niis.xroad.e2e.AdminApi.login;

/**
 * Calls each catalogue scenario from ss1 to ss0 and compares the outcome with the legacy verdict. The legacy
 * verdicts are the expectation in every mode, except for a request whose row carries its own expectation for the
 * data space protocol: with {@code -Pe2e.acl-mode=legacy} ss1 sends calls without data space negotiation, so ss0
 * decides by its access rights alone; with {@code -Pe2e.acl-mode=dsp} ss1 negotiates through the data space
 * protocol, and every difference from the expected verdict shows as a failure; with
 * {@code -Pe2e.acl-mode=dsp-refusal} ss1 negotiates as well, and ss0 refuses X-Road 7 style calls from the moment
 * the subsystem pool is registered. Every scenario creates namespaced fixtures on its provider's Security Server
 * (ss0 unless the fixture names ss1), polls until the expected verdict or the cache deadline, and removes its
 * fixtures. A scenario that expects an admin API status instead issues one grant and checks the status, without
 * polling. When {@code test-framework.acl-verdicts-file} is set, one line per scenario names its expected and its
 * observed verdicts.
 *
 * <p>Before the first scenario the shared {@link SubsystemPoolSetup subsystem pool} is registered once, and the
 * service clients holding the management client registration service are listed; after the last scenario the list
 * must be the same.
 *
 * <p>Runs only when {@code -Pe2e.acl-mode} is set and the environment is the Compose stack, the only one that
 * applies the mode; otherwise the pool is not registered and every scenario is skipped. Before the pool is
 * registered, the stored {@code xroad.proxy.dsp-enabled} of ss1 must name the configured mode, else the run
 * fails. Single-threaded and ordered after every other class, and a failing scenario does not stop
 * the next one: the Gradle task sets the fail-fast threshold to {@code 0} in that mode.
 */
@Slf4j
@Order(500)
@Isolated
@Execution(ExecutionMode.SAME_THREAD)
@DisplayName("ACL parity - catalogue scenarios called from ss1 to ss0")
class AclParityTest extends E2eTest {

    private static final String SS0_ENV = "ss0";
    private static final String SS1_ENV = "ss1";
    private static final String RUN_TOKEN = Long.toString(Instant.now().getEpochSecond(), Character.MAX_RADIX);

    private static List<String> managementGrantsBefore;
    private static AclMode aclMode;

    @BeforeAll
    static void registerSubsystemPool(E2eEnvironment env) {
        var mode = AclParityProperties.configuredMode();
        assumeTrue(mode.isPresent(), "ACL parity runs only with -Pe2e.acl-mode");
        assumeTrue(env instanceof E2eEnvSetup,
                "ACL parity runs only with -Pe2e.env-mode=compose: only the Compose stack applies -Pe2e.acl-mode");
        aclMode = mode.get();
        log.info(AclModeProbe.verify(aclMode, storedDspEnabled((E2eEnvSetup) env)));
        AclParityProperties.verdictsFile().ifPresent(file -> AclVerdictReport.start(Path.of(file)));
        var ss1BaseUrl = adminBaseUrl(env, SS1_ENV);
        AdminApi.setConnectionType(ss1BaseUrl, login(ss1BaseUrl), AclParticipants.MEMBER_OF_CONSUMER, "HTTP");
        SubsystemPoolSetup.ensure(env);
        if (aclMode.refusesLegacyCalls()) {
            ((E2eEnvSetup) env).refuseLegacyCallsOnSs0();
        }
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

    private static String storedDspEnabled(E2eEnvSetup env) {
        var query = "SELECT property_value FROM configuration_properties WHERE property_key = '%s'"
                .formatted(ProxyConfigKeys.DSP_ENABLED.key());
        var result = env.execInEnvContainer(SS1_ENV, SsStackSetup.DB_SERVERCONF,
                "psql", "-U", "postgres", "-d", "serverconf", "-tAX", "-c", query);
        if (result.getExitCode() != 0) {
            throw new IllegalStateException("reading dsp-enabled from the serverconf of %s failed: %s"
                    .formatted(SS1_ENV, result.getStderr()));
        }
        return result.getStdout().trim();
    }

    private static List<String> managementGrants(E2eEnvironment env) {
        var ss0BaseUrl = adminBaseUrl(env, SS0_ENV);
        return AdminApi.serviceClientsWithAccess(ss0BaseUrl, login(ss0BaseUrl), AclParticipants.MANAGEMENT,
                AclParticipants.CLIENT_REG);
    }

    static Stream<Named<AclScenario>> scenarios() {
        return AclScenarioCatalogue.all().stream()
                .map(scenario -> Named.of(scenario.id() + " " + scenario.description(), scenario));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("scenarios")
    void scenarioGetsItsExpectedVerdict(AclScenario catalogued, E2eEnvironment env) {
        var scenario = catalogued.forMode(aclMode);
        var providerBaseUrl = adminBaseUrl(env, providerEnv(scenario));
        var csBaseUrl = CentralServerApi.baseUrl(env);
        var gateway = new AdminApiGateway(providerBaseUrl, login(providerBaseUrl), backendUrl(env), csBaseUrl,
                login(csBaseUrl));

        try (var management = new ManagementProbe(adminBaseUrl(env, SS1_ENV), RUN_TOKEN, scenario.id());
                var fixture = ScenarioFixture.open(gateway, RUN_TOKEN, scenario)) {
            var serviceCodes = fixture.serviceCodes();
            if (scenario.adminGrant() != null) {
                assertAdminGrantStatus(scenario, serviceCodes, gateway);
                return;
            }
            var outcomes = AclScenarioVerdicts.pollPhased(new VerdictPoller(), scenario, fixture::applyPhase,
                    request -> call(env, management, scenario, serviceCodes, request),
                    scenario.waitClass().firstCheckTimeout(), scenario.waitClass().timeout(),
                    DisabledServiceFault.SERVERCONF_CACHE_EXPIRY_POLL_INTERVAL);
            recordVerdicts(AclVerdictReport.line(scenario.id(), outcomes));
            outcomes.forEach(outcome -> log.info("{}: {} -> verdict {} ({}) after {} attempts in {} s",
                    scenario.id(), outcome.check().request().describe(), outcome.result().last(),
                    outcome.result().detail(), outcome.result().attempts(),
                    outcome.result().elapsed().toSeconds()));
            AclScenarioVerdicts.failureMessage(scenario.id(), outcomes).ifPresent(Assertions::fail);
        }
    }

    private static void assertAdminGrantStatus(AclScenario scenario, List<String> serviceCodes, AdminApiGateway gateway) {
        var grant = scenario.adminGrant();
        var reply = gateway.requestAccessGrant(scenario.fixture().providerClientId(), grant.subjectId(),
                serviceCodes.get(grant.serviceIndex()));
        var errorCode = JsonPath.from(reply.body().isBlank() ? "{}" : reply.body()).getString("error.code");
        log.info("{}: grant to {} -> admin API status {}, error code {}", scenario.id(), grant.subjectId(),
                reply.status(), errorCode);
        recordVerdicts(AclVerdictReport.adminGrantLine(scenario.id(),
                "%d %s".formatted(grant.expectedStatus(), grant.expectedErrorCode()),
                "%d %s".formatted(reply.status(), errorCode)));
        Assertions.assertEquals(grant.expectedStatus(), reply.status(),
                "admin API status of the grant to " + grant.subjectId());
        Assertions.assertEquals(grant.expectedErrorCode(), errorCode,
                "admin API error code of the grant to " + grant.subjectId());
    }

    private static void recordVerdicts(String line) {
        AclParityProperties.verdictsFile().ifPresent(file -> AclVerdictReport.append(Path.of(file), line));
    }

    private static String providerEnv(AclScenario scenario) {
        return scenario.fixture().providerHost() == ProviderHost.SS1 ? SS1_ENV : SS0_ENV;
    }

    private static String backendUrl(E2eEnvironment env) {
        var mapping = env.getContainerMapping(CentralServerApi.CS_ENV, AuxStackSetup.ISREST,
                AuxStackSetup.Port.ISREST);
        return "http://%s:%s".formatted(mapping.host(), mapping.port());
    }

    private static Observation call(E2eEnvironment env, ManagementProbe management, AclScenario scenario,
                                    List<String> serviceCodes, RequestSpec request) {
        var proxy = env.getContainerMapping(SS1_ENV, SsStackSetup.PROXY, SsStackSetup.Port.PROXY);
        var proxyOrigin = "http://%s:%s".formatted(proxy.host(), proxy.port());
        var providerClientId = scenario.fixture().providerClientId();
        return switch (request) {
            case RestRequest rest -> {
                var serviceCode = serviceCodes.get(rest.serviceIndex());
                var reply = RestSender.send(rest.method(),
                        RestRequests.url(proxyOrigin, providerClientId, serviceCode, rest.path()),
                        RestRequests.clientHeader(rest.consumerClientId()));
                yield new Observation(VerdictClassifier.classify(reply), VerdictClassifier.describe(reply));
            }
            case SoapRequest soap -> {
                var serviceCode = serviceCodes.get(soap.serviceIndex());
                var reply = postSoap(proxyOrigin, messageId -> SoapRequests.envelope(soap.consumerClientId(),
                        providerClientId, serviceCode, soap.serviceVersion(), soap.securityServerHint(), messageId));
                yield new Observation(VerdictClassifier.classifySoap(reply), VerdictClassifier.describeSoap(reply));
            }
            case MetaserviceRequest meta -> callMetaservice(proxyOrigin, providerClientId, serviceCodes, meta);
            case MonitoringRequest monitoring -> {
                var reply = postSoap(proxyOrigin, messageId -> SoapRequests.monitoringEnvelope(
                        monitoring.consumerClientId(), providerClientId, monitoring.code(),
                        AclParticipants.OWNER_SECURITY_SERVER, monitoring.filterClientId(), messageId));
                yield observe(reply, serviceCodes, monitoring.expectation());
            }
            case ListClientsRequest listClients -> observe(
                    RestSender.send("GET", RestRequests.listClientsUrl(proxyOrigin), null), serviceCodes,
                    listClients.expectation());
            case ManagementRequest registration -> {
                var reply = management.register(registration.consumerClientId());
                yield new Observation(VerdictClassifier.classifyManagement(reply),
                        VerdictClassifier.describeManagement(reply));
            }
        };
    }

    private static Observation callMetaservice(String proxyOrigin, String providerClientId, List<String> serviceCodes,
                                               MetaserviceRequest meta) {
        var target = meta.serviceIndex() == MetaserviceRequest.NO_SERVICE
                ? null : serviceCodes.get(meta.serviceIndex());
        var reply = meta.transport() == Transport.REST
                ? RestSender.send(meta.method(),
                        RestRequests.metaserviceUrl(proxyOrigin, providerClientId, meta.code(), target),
                        RestRequests.clientHeader(meta.consumerClientId()), "application/json")
                : postSoap(proxyOrigin, messageId -> SoapRequests.metaserviceEnvelope(meta.consumerClientId(),
                        providerClientId, meta.code(), target, meta.targetVersion(), messageId));
        return observe(reply, serviceCodes, meta.expectation());
    }

    private static Reply postSoap(String proxyOrigin, Function<String, String> envelopeOf) {
        return Replies.toReply(RestAssuredFactory.given()
                .header("Content-Type", "text/xml")
                .body(envelopeOf.apply("acl-parity-" + UUID.randomUUID()))
                .post(proxyOrigin));
    }

    private static Observation observe(Reply reply, List<String> serviceCodes, Expectation expectation) {
        var required = expectation.required(serviceCodes);
        var forbidden = expectation.forbidden(serviceCodes);
        return new Observation(VerdictClassifier.classifyMetaservice(reply, required, forbidden),
                VerdictClassifier.describeMetaservice(reply, required, forbidden));
    }
}
