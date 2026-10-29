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

import org.niis.xroad.test.apitest.core.config.ApiTestCoreProperties;
import org.niis.xroad.test.apitest.core.container.BaseComposeSetup;
import org.testcontainers.containers.output.FrameConsumerResultCallback;
import org.testcontainers.containers.output.OutputFrame;
import org.testcontainers.containers.output.Slf4jLogConsumer;

import java.io.File;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Common base for the two stack shapes a single ss0/ss1 slot in {@link E2eEnvSetup} can boot: the
 * per-service multi-container stack ({@link SsStackSetup}) and the single sidecar container
 * ({@link SidecarSsStackSetup}). Both answer to the same set of network aliases and ports, so tests
 * declare {@code E2eEnvironment}/the ops interfaces and never see which shape is running.
 */
public abstract class AbstractSsStack extends BaseComposeSetup {

    private final Map<String, Slf4jLogConsumer> logConsumers = new HashMap<>();

    protected AbstractSsStack(ApiTestCoreProperties coreProperties) {
        super(coreProperties);
    }

    /**
     * Blocks until this stack's proxy reports readiness, including OCSP status for the auth key.
     */
    public abstract void awaitProxyReadiness();

    protected Slf4jLogConsumer createLogConsumer(String envName, String containerName) {
        var consumer = createLogConsumer("%s-%s".formatted(envName, containerName));
        logConsumers.put(containerName, consumer);
        return consumer;
    }

    /** Restarts the container and streams its new output to the log consumer it had before, which a restart ends. */
    @Override
    public void restartService(String containerName) {
        super.restartService(containerName);
        var consumer = logConsumers.get(containerName);
        if (consumer != null) {
            followLogs(containerName, consumer);
        }
    }

    private void followLogs(String containerName, Slf4jLogConsumer consumer) {
        var container = env.getContainerByServiceName(containerName).orElseThrow(
                () -> new IllegalStateException("Container not found: " + containerName));
        var dockerClient = container.getDockerClient();
        var startedAt = Instant.parse(dockerClient.inspectContainerCmd(container.getContainerId()).exec()
                .getState().getStartedAt());
        var callback = new FrameConsumerResultCallback();
        callback.addConsumer(OutputFrame.OutputType.STDOUT, consumer);
        callback.addConsumer(OutputFrame.OutputType.STDERR, consumer);
        dockerClient.logContainerCmd(container.getContainerId())
                .withFollowStream(true)
                .withStdOut(true)
                .withStdErr(true)
                .withSince((int) startedAt.getEpochSecond())
                .exec(callback);
    }

    protected File composeFile(String fileName) {
        return new File(coreProperties.resourceDir() + fileName);
    }
}
