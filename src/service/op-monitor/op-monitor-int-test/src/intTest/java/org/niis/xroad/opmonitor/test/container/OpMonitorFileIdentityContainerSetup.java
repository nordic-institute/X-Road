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
package org.niis.xroad.opmonitor.test.container;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Container;
import com.github.dockerjava.api.model.Frame;
import lombok.SneakyThrows;
import org.niis.xroad.common.properties.config.keys.CommonRpcConfigKeys;
import org.niis.xroad.test.apitest.core.config.ApiTestCoreProperties;
import org.niis.xroad.test.apitest.core.container.BaseComposeSetup;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;

/**
 * Op-monitor test stack for the file-based TLS identity: op-monitor with its databases, and no OpenBao service. A
 * trap listens at the OpenBao address and logs every connection. Three more op-monitor services start with broken
 * identity files and are expected to stop.
 */
public class OpMonitorFileIdentityContainerSetup extends BaseComposeSetup {

    public static final String MISMATCHED_KEY = "op-monitor-mismatched-key";
    public static final String MISSING_CERTIFICATE = "op-monitor-missing-certificate";
    public static final String TWO_TRUSTED_CAS = "op-monitor-two-trusted-cas";

    private static final String COMPOSE_PROJECT = "op-monitor-file-identity-";
    private static final String COMPOSE_FILE = "compose.fileIdentity.intTest.yaml";
    private static final String COMPOSE_PROJECT_LABEL = "com.docker.compose.project";
    private static final String COMPOSE_SERVICE_LABEL = "com.docker.compose.service";
    private static final String OP_MONITOR = "op-monitor";
    private static final String OPENBAO_TRAP = "openbao-trap";
    private static final String OPENBAO_URL = "http://openbao:8200/";
    private static final String DB_OP_MONITOR = "db-opmonitor";
    private static final int DB_OP_MONITOR_PORT = 5432;
    private static final int QUARKUS_HEALTH_PORT = 4099;
    private static final Duration OP_MONITOR_STARTUP_TIMEOUT = Duration.ofSeconds(90);
    private static final Duration EXIT_TIMEOUT = Duration.ofSeconds(120);
    private static final Duration POLL_INTERVAL = Duration.ofSeconds(2);
    private static final long LOG_TIMEOUT_SECONDS = 15;

    private final Map<String, String> composeEnvironment;

    public OpMonitorFileIdentityContainerSetup(ApiTestCoreProperties coreProperties,
                                               Map<String, String> composeEnvironment) {
        super(coreProperties);
        this.composeEnvironment = composeEnvironment;
    }

    @Override
    protected String composeProjectName() {
        return COMPOSE_PROJECT;
    }

    @Override
    protected ComposeContainer initEnv() {
        return new ComposeContainer(composeProjectName(), new File(coreProperties.resourceDir() + COMPOSE_FILE))
                .withEnv(composeEnvironment)
                .withExposedService(OP_MONITOR, CommonRpcConfigKeys.CHANNEL_OP_MONITOR_PORT.convertedDefaultValue(),
                        Wait.forHealthcheck().withStartupTimeout(OP_MONITOR_STARTUP_TIMEOUT))
                .withExposedService(DB_OP_MONITOR, DB_OP_MONITOR_PORT, Wait.forListeningPort())
                .withLogConsumer(OP_MONITOR, createLogConsumer(OP_MONITOR));
    }

    /**
     * Returns where the op-monitor gRPC port is reachable from the test JVM.
     */
    public ContainerMapping opMonitorMapping() {
        return getContainerMapping(OP_MONITOR, CommonRpcConfigKeys.CHANNEL_OP_MONITOR_PORT.convertedDefaultValue());
    }

    /**
     * Returns the liveness endpoint response body of op-monitor, read from inside its container.
     */
    public String livenessResponse() {
        return execInContainer(OP_MONITOR, "curl", "-s", "--max-time", "2",
                "http://127.0.0.1:" + QUARKUS_HEALTH_PORT + "/q/health/live").getStdout();
    }

    /**
     * Makes an HTTP request to the OpenBao address from inside the op-monitor container, so that the trap has a
     * connection to record.
     */
    public void connectToOpenBaoAddress() {
        execInContainer(OP_MONITOR, "curl", "-s", "-o", "/dev/null", "--max-time", "2", OPENBAO_URL);
    }

    /**
     * Returns the log of the OpenBao trap, which has a line for every connection it accepted.
     */
    public String openBaoTrapLog() {
        return containerLog(containerId(OPENBAO_TRAP));
    }

    /**
     * Waits until the service has exited and returns its exit code and log.
     */
    public Outcome awaitExit(String service) {
        var id = containerId(service);
        var client = DockerClientFactory.instance().client();
        await().pollInterval(POLL_INTERVAL).atMost(EXIT_TIMEOUT)
                .until(() -> Boolean.FALSE.equals(client.inspectContainerCmd(id).exec().getState().getRunning()));
        var exitCode = client.inspectContainerCmd(id).exec().getState().getExitCodeLong();
        return new Outcome(exitCode == null ? -1 : exitCode, containerLog(id));
    }

    private String containerId(String service) {
        var containers = DockerClientFactory.instance().client().listContainersCmd().withShowAll(true).exec();
        return containers.stream()
                .filter(container -> container.getLabels() != null
                        && container.getLabels().getOrDefault(COMPOSE_PROJECT_LABEL, "").startsWith(COMPOSE_PROJECT)
                        && service.equals(container.getLabels().get(COMPOSE_SERVICE_LABEL)))
                .map(Container::getId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Container not found: " + service));
    }

    @SneakyThrows
    private static String containerLog(String id) {
        DockerClient client = DockerClientFactory.instance().client();
        var content = new StringBuilder();
        client.logContainerCmd(id).withStdOut(true).withStdErr(true)
                .exec(new ResultCallback.Adapter<Frame>() {
                    @Override
                    public void onNext(Frame frame) {
                        content.append(new String(frame.getPayload(), StandardCharsets.UTF_8));
                    }
                })
                .awaitCompletion(LOG_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return content.toString();
    }

    /**
     * Exit code and log of a stopped service.
     *
     * @param exitCode the process exit code
     * @param log the stdout and stderr of the container
     */
    public record Outcome(long exitCode, String log) {
    }
}
