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
package org.niis.xroad.e2e.container;

import com.github.dockerjava.api.model.ContainerNetwork;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.test.apitest.core.config.ApiTestCoreProperties;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;

import java.io.File;
import java.time.Duration;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.awaitility.Awaitility.await;
import static org.testcontainers.containers.wait.strategy.Wait.forListeningPort;

/**
 * A single full-sidecar container standing in for one of the per-instance {@link SsStackSetup}
 * stacks (embedded PostgreSQL, embedded OpenBao, every service under supervisord). Selected for ss0
 * by {@code test-framework.ss0-stack=sidecar}; joins the shared {@code xroad-network} carrying every
 * alias the multi-container stack exports for that instance (UI, proxy, {@code xrd-<name>}), so hurl
 * and the test suite run unmodified against either shape.
 */
@Slf4j
@SuppressWarnings("checkstyle:magicnumber")
public class SidecarSsStackSetup extends AbstractSsStack {

    private static final String SIDECAR = "sidecar";

    private static final String COMPOSE_SIDECAR_FILE = "compose.ss0-sidecar.e2e.yaml";
    private static final String XROAD_NETWORK = "xroad-network";

    /**
     * The sidecar's measured cold start is around three minutes (embedded PostgreSQL and OpenBao
     * bootstrap plus every packaged service starting under supervisord, the dataspace control plane
     * alone taking over two), and a single early control-plane restart under load has been observed;
     * this leaves generous headroom above that instead of reusing the multi-container stack's
     * five-minute budget.
     */
    private static final Duration READINESS_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration READINESS_POLL_INTERVAL = Duration.ofSeconds(5);
    private static final Duration PORT_WAIT_TIMEOUT = Duration.ofMinutes(8);

    private final String name;

    public SidecarSsStackSetup(ApiTestCoreProperties coreProperties, String name) {
        super(coreProperties);
        this.name = name;
    }

    @Override
    protected String composeProjectName() {
        return name + "-";
    }

    @Override
    protected ComposeContainer initEnv() {
        return new ComposeContainer(composeProjectName(), composeFile(COMPOSE_SIDECAR_FILE))
                .withEnv("XROAD_DSP_PARTICIPANT_CONTEXT_ID", "xrd-" + name)
                .withExposedService(SIDECAR, SsStackSetup.Port.PROXY,
                        forListeningPort().withStartupTimeout(PORT_WAIT_TIMEOUT))
                .withExposedService(SIDECAR, SsStackSetup.Port.PROXY_HEALTHCHECK,
                        forListeningPort().withStartupTimeout(PORT_WAIT_TIMEOUT))
                .withExposedService(SIDECAR, SsStackSetup.Port.UI,
                        forListeningPort().withStartupTimeout(PORT_WAIT_TIMEOUT))
                .withLogConsumer(SIDECAR, createLogConsumer(name, SIDECAR));
    }

    @Override
    protected void onPostStart() {
        connectToExternalNetwork();
    }

    /**
     * One sidecar container answers to every alias the multi-container stack spreads across its ui,
     * proxy, signer, configuration-client, ds-control-plane and ds-identity-hub services, plus the
     * {@code xrd-<name>} messaging alias the proxy alone carries there. The ds-control-plane and
     * ds-identity-hub aliases matter even though compose-mode DSP assertions self-skip: dataspace
     * asset-access acquisition is the actual wire mechanism message exchange and management requests
     * negotiate over, so a counterparty resolving those hostnames is required for any cross-SS call to
     * reach the sidecar, not only for DSP-specific test scenarios.
     */
    private void connectToExternalNetwork() {
        var containerState = env.getContainerByServiceName(SIDECAR).orElseThrow();
        var dockerClient = containerState.getDockerClient();

        String networkId = dockerClient.listNetworksCmd().exec().stream()
                .filter(n -> XROAD_NETWORK.equals(n.getName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Could not find external network '%s'".formatted(XROAD_NETWORK)))
                .getId();

        var aliases = List.of(
                "%s-%s".formatted(name, SsStackSetup.UI),
                "%s-%s".formatted(name, SsStackSetup.PROXY),
                "%s-%s".formatted(name, SsStackSetup.SIGNER),
                "%s-%s".formatted(name, SsStackSetup.CONFIGURATION_CLIENT),
                "%s-%s".formatted(name, SsStackSetup.DS_CONTROL_PLANE),
                "%s-%s".formatted(name, SsStackSetup.DS_IDENTITY_HUB),
                "xrd-" + name);

        dockerClient.connectToNetworkCmd()
                .withContainerId(containerState.getContainerId())
                .withNetworkId(networkId)
                .withContainerNetwork(new ContainerNetwork().withAliases(aliases.toArray(String[]::new)))
                .exec();
    }

    /**
     * Blocks until the sidecar's proxy reports readiness, including OCSP status for the auth key.
     * Sized for the sidecar's slower cold start (see {@link #READINESS_TIMEOUT}) rather than the
     * multi-container stack's per-service boot time.
     */
    @Override
    public void awaitProxyReadiness() {
        var mapping = getContainerMapping(SsStackSetup.PROXY, SsStackSetup.Port.PROXY_HEALTHCHECK);
        var readinessUrl = "http://%s:%d/q/health/ready".formatted(mapping.host(), mapping.port());
        log.info("Waiting for {} sidecar proxy readiness at {}", name, readinessUrl);

        await()
                .atMost(READINESS_TIMEOUT)
                .pollInterval(READINESS_POLL_INTERVAL)
                .ignoreExceptions()
                .until(() -> {
                    var json = given().get(readinessUrl).jsonPath();
                    var overall = json.getString("status");
                    var authKeyOcsp = json.getString(
                            "checks.find { it.name == 'PROXY_AUTH_KEY_OCSP_READINESS_CHECK' }.data.status");
                    log.info("{} sidecar proxy readiness: status={}, authKeyOcsp={}", name, overall, authKeyOcsp);
                    return "UP".equals(overall) && "OK".equals(authKeyOcsp);
                });
    }

    /**
     * The one sidecar container answers to every service name the multi-container stack spreads
     * across ui/proxy/signer/configuration-client, so callers may pass any of those names; only the
     * port distinguishes which of the container's listeners they mean.
     */
    @Override
    public ContainerMapping getContainerMapping(String service, int originalPort) {
        return super.getContainerMapping(SIDECAR, originalPort);
    }

    private Slf4jLogConsumer createLogConsumer(String envName, String containerName) {
        return createLogConsumer("%s-%s".formatted(envName, containerName));
    }

    private File composeFile(String fileName) {
        return new File(coreProperties.resourceDir() + fileName);
    }
}
