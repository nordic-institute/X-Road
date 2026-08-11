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
package org.niis.xroad.softtoken.signer.test.container;

import lombok.Getter;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.properties.config.keys.CommonRpcConfigKeys;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.common.rpc.credentials.InsecureRpcCredentialsConfigurer;
import org.niis.xroad.signer.client.SignerRpcChannelProperties;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.niis.xroad.signer.client.impl.SignerSignRpcClient;
import org.niis.xroad.test.apitest.core.config.ApiTestCoreProperties;
import org.niis.xroad.test.apitest.core.container.BaseComposeSetup;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.ContainerState;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

/**
 * Browserless signer + softtoken-signer test stack: the signer service, the softtoken-signer service under
 * test, and the shared serverconf PostgreSQL database. Boots once per JVM launcher session; the
 * {@link SignerRpcClient} (to signer) and {@link SignerSignRpcClient} (to softtoken-signer) are created once
 * the stack is up.
 */
@Slf4j
public class SoftTokenSignerContainerSetup extends BaseComposeSetup {
    private static final int SIGNER_GRPC_PORT = CommonRpcConfigKeys.CHANNEL_SIGNER_PORT.convertedDefaultValue();
    private static final Duration SIGNER_STARTUP_TIMEOUT = Duration.ofSeconds(45);
    private static final Duration SOFTTOKEN_SIGNER_STARTUP_TIMEOUT = Duration.ofSeconds(45);

    public static final String SIGNER = "signer";
    public static final String SOFTTOKEN_SIGNER = "softtoken-signer";
    public static final String DB_SERVERCONF = "db-serverconf";

    /**
     * Resource-lock name guarding signer container availability. {@code SoftTokenHealthChecksIntTest} stops
     * and restarts the signer container mid-scenario; {@code SoftTokenKeySyncIntTest} assumes it is always
     * reachable. Both classes declare a lock on this name (write for the stopper, read for the assumer) so
     * JUnit's parallel executor never interleaves them.
     */
    public static final String SIGNER_AVAILABILITY_LOCK = "softtoken-signer-signer-availability";

    private static final String COMPOSE_FILE = "compose.intTest.yaml";

    @Getter
    private SignerRpcClient signerClient;

    @Getter
    private SignerSignRpcClient softTokenSignerSignClient;

    public SoftTokenSignerContainerSetup(ApiTestCoreProperties coreProperties) {
        super(coreProperties);
    }

    @Override
    protected String composeProjectName() {
        return "softtoken-signer-";
    }

    @Override
    protected ComposeContainer initEnv() {
        return new ComposeContainer(composeProjectName(),
                new File(coreProperties.resourceDir() + COMPOSE_FILE))
                .withExposedService(SIGNER, SIGNER_GRPC_PORT, Wait.forHealthcheck().withStartupTimeout(SIGNER_STARTUP_TIMEOUT))
                .withExposedService(SOFTTOKEN_SIGNER, Port.SOFTTOKEN_SIGNER_GRPC,
                        Wait.forHealthcheck().withStartupTimeout(SOFTTOKEN_SIGNER_STARTUP_TIMEOUT))
                .withExposedService(SOFTTOKEN_SIGNER, Port.HEALTH_PORT)
                .withExposedService(DB_SERVERCONF, Port.DB, Wait.forListeningPort())
                .withLogConsumer(SOFTTOKEN_SIGNER, createLogConsumer(SOFTTOKEN_SIGNER));
    }

    @Override
    @SneakyThrows
    protected void onPostStart() {
        var factory = new RpcChannelFactory(new InsecureRpcCredentialsConfigurer());

        var signerMapping = getContainerMapping(SIGNER, SIGNER_GRPC_PORT);
        signerClient = new SignerRpcClient(factory, channelProperties(signerMapping.host(), signerMapping.port()));
        signerClient.init();

        var softtokenMapping = getContainerMapping(SOFTTOKEN_SIGNER, Port.SOFTTOKEN_SIGNER_GRPC);
        softTokenSignerSignClient = new SignerSignRpcClient(factory, channelProperties(softtokenMapping.host(), softtokenMapping.port()));
    }

    private SignerRpcChannelProperties channelProperties(String host, int port) {
        return new SignerRpcChannelProperties() {
            @Override
            public String host() {
                return host;
            }

            @Override
            public int port() {
                return port;
            }

            @Override
            public int deadlineAfter() {
                return CommonRpcConfigKeys.CHANNEL_SIGNER_DEADLINE_AFTER.convertedDefaultValue();
            }
        };
    }

    /**
     * Returns the base URL of the softtoken-signer health endpoint.
     */
    public String healthBaseUrl() {
        var mapping = getContainerMapping(SOFTTOKEN_SIGNER, Port.HEALTH_PORT);
        return "http://%s:%d".formatted(mapping.host(), mapping.port());
    }

    /**
     * Stop a container by service name.
     */
    @SuppressWarnings("checkstyle:MagicNumber")
    public void stopContainer(String service) {
        var containerState = env.getContainerByServiceName(service).orElseThrow();
        var dockerClient = containerState.getDockerClient();
        dockerClient.stopContainerCmd(containerState.getContainerId()).exec();
        await().atMost(30, TimeUnit.SECONDS).until(() -> !containerState.isRunning());
    }

    /**
     * Check whether a container is currently running.
     */
    public boolean isRunning(String service) {
        return env.getContainerByServiceName(service)
                .map(ContainerState::isRunning)
                .orElse(false);
    }

    /**
     * Start a container by service name.
     */
    @SuppressWarnings("checkstyle:MagicNumber")
    public void startContainer(String service, boolean waitForHealthy) {
        var containerState = env.getContainerByServiceName(service).orElseThrow();
        var dockerClient = containerState.getDockerClient();
        dockerClient.startContainerCmd(containerState.getContainerId()).exec();
        if (waitForHealthy) {
            await().atMost(45, TimeUnit.SECONDS).until(containerState::isHealthy);
        }
    }

    /**
     * Port constants for containerized services.
     */
    @UtilityClass
    public static final class Port {
        public static final int DB = 5432;
        public static final int SOFTTOKEN_SIGNER_GRPC = 5561;
        public static final int HEALTH_PORT = 4099;
    }
}
