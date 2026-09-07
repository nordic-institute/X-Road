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
package org.niis.xroad.signer.test.container;

import lombok.Getter;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.properties.NodeProperties;
import org.niis.xroad.common.properties.config.keys.CommonRpcConfigKeys;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.common.rpc.credentials.InsecureRpcCredentialsConfigurer;
import org.niis.xroad.signer.client.SignerRpcChannelProperties;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.niis.xroad.signer.client.impl.SignerSignRpcClient;
import org.niis.xroad.test.apitest.core.config.ApiTestCoreProperties;
import org.niis.xroad.test.apitest.core.container.BaseComposeSetup;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

/**
 * Signer + secondary-signer test stack: two signer nodes sharing the same serverconf database, plus the
 * test CA used to countersign generated CSRs. Boots once per JVM launcher session; the primary and
 * secondary {@link SignerRpcClient}/{@link SignerSignRpcClient} pairs are created once the stack is up,
 * with the default RPC deadline.
 */
@Slf4j
public class SignerIntTestContainerSetup extends BaseComposeSetup {

    public static final String SIGNER = "signer";
    public static final String SIGNER_SECONDARY = "signer-secondary";
    public static final String TESTCA = "testca";
    public static final String DB_SERVERCONF = "db-serverconf";

    private static final int SIGNER_GRPC_PORT = CommonRpcConfigKeys.CHANNEL_SIGNER_PORT.convertedDefaultValue();
    private static final int DEFAULT_DEADLINE_MILLIS = CommonRpcConfigKeys.CHANNEL_SIGNER_DEADLINE_AFTER.convertedDefaultValue();
    private static final Duration SIGNER_STARTUP_TIMEOUT = Duration.ofSeconds(45);
    private static final String COMPOSE_FILE = "compose.intTest.yaml";

    @Getter
    private SignerRpcClient signerClient;
    @Getter
    private SignerSignRpcClient signerSignClient;
    @Getter
    private SignerRpcClient secondarySignerClient;
    @Getter
    private SignerSignRpcClient secondarySignerSignClient;

    public SignerIntTestContainerSetup(ApiTestCoreProperties coreProperties) {
        super(coreProperties);
    }

    @Override
    protected String composeProjectName() {
        return "signer-";
    }

    @Override
    protected ComposeContainer initEnv() {
        return new ComposeContainer(composeProjectName(),
                new File(coreProperties.resourceDir() + COMPOSE_FILE))
                .withExposedService(SIGNER, SIGNER_GRPC_PORT, Wait.forHealthcheck().withStartupTimeout(SIGNER_STARTUP_TIMEOUT))
                .withExposedService(SIGNER_SECONDARY, SIGNER_GRPC_PORT, Wait.forHealthcheck().withStartupTimeout(SIGNER_STARTUP_TIMEOUT))
                .withExposedService(DB_SERVERCONF, Port.DB, Wait.forListeningPort())
                .withExposedService(TESTCA, Port.TEST_CA, Wait.forLogMessage(".*nginx entered RUNNING state.*", 1))
                .withLogConsumer(SIGNER, createLogConsumer(SIGNER))
                .withLogConsumer(SIGNER_SECONDARY, createLogConsumer(SIGNER_SECONDARY));
    }

    @Override
    @SneakyThrows
    protected void onPostStart() {
        signerClient = newSignerClient(SIGNER, DEFAULT_DEADLINE_MILLIS);
        signerClient.init();
        signerSignClient = newSignerSignClient(SIGNER, DEFAULT_DEADLINE_MILLIS);

        secondarySignerClient = newSignerClient(SIGNER_SECONDARY, DEFAULT_DEADLINE_MILLIS);
        secondarySignerClient.init();
        secondarySignerSignClient = newSignerSignClient(SIGNER_SECONDARY, DEFAULT_DEADLINE_MILLIS);
    }

    /**
     * Returns the primary or secondary node's {@link SignerRpcClient}, both created with the default RPC deadline.
     */
    public SignerRpcClient client(NodeProperties.NodeType nodeType) {
        return switch (nodeType) {
            case STANDALONE, PRIMARY -> signerClient;
            case SECONDARY -> secondarySignerClient;
        };
    }

    /**
     * Returns the primary or secondary node's {@link SignerSignRpcClient}.
     */
    public SignerSignRpcClient signClient(NodeProperties.NodeType nodeType) {
        return switch (nodeType) {
            case STANDALONE, PRIMARY -> signerSignClient;
            case SECONDARY -> secondarySignerSignClient;
        };
    }

    /**
     * Builds and initializes a standalone {@link SignerRpcClient} against the primary signer node with a
     * custom RPC deadline, without touching the shared {@link #getSignerClient()} instance other test
     * classes rely on. Used to exercise deadline-exceeded behavior in isolation.
     */
    @SneakyThrows
    public SignerRpcClient newSignerClientWithTimeout(int deadlineMillis) {
        var client = newSignerClient(SIGNER, deadlineMillis);
        client.init();
        return client;
    }

    /**
     * Returns the base URL ({@code http://host:port/testca}) of the test CA's CSR-signing endpoint.
     */
    public String testCaBaseUrl() {
        var mapping = getContainerMapping(TESTCA, Port.TEST_CA);
        return "http://%s:%d/testca".formatted(mapping.host(), mapping.port());
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    public void restartContainer(String service) {
        var containerState = env.getContainerByServiceName(service).orElseThrow();

        var dockerClient = containerState.getDockerClient();

        dockerClient.stopContainerCmd(containerState.getContainerId()).exec();
        dockerClient.startContainerCmd(containerState.getContainerId()).exec();
        await().atMost(20, TimeUnit.SECONDS).until(containerState::isHealthy);
    }

    private SignerRpcClient newSignerClient(String service, int deadlineMillis) {
        var mapping = getContainerMapping(service, SIGNER_GRPC_PORT);
        return new SignerRpcClient(rpcChannelFactory(), channelProperties(mapping.host(), mapping.port(), deadlineMillis));
    }

    private SignerSignRpcClient newSignerSignClient(String service, int deadlineMillis) {
        var mapping = getContainerMapping(service, SIGNER_GRPC_PORT);
        return new SignerSignRpcClient(rpcChannelFactory(), channelProperties(mapping.host(), mapping.port(), deadlineMillis));
    }

    private static RpcChannelFactory rpcChannelFactory() {
        return new RpcChannelFactory(new InsecureRpcCredentialsConfigurer());
    }

    private static SignerRpcChannelProperties channelProperties(String host, int port, int deadlineMillis) {
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
                return deadlineMillis;
            }
        };
    }

    @UtilityClass
    public static final class Port {
        public static final int DB = 5432;
        public static final int TEST_CA = 8888;
    }
}
