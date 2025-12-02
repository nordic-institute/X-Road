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

package org.niis.xroad.test.framework.core.container;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.test.framework.core.config.TestFrameworkCoreProperties;
import org.niis.xroad.test.framework.core.logging.ComposeLoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.output.Slf4jLogConsumer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@RequiredArgsConstructor
public abstract class BaseComposeSetup implements InitializingBean, DisposableBean {
    private static final int EXEC_TIMEOUT_SECONDS = 20;
    protected final TestFrameworkCoreProperties coreProperties;
    protected ComposeContainer env;

    @Override
    public void afterPropertiesSet() {
        init();
    }

    protected void init() {
        env = initEnv();
        env.start();
        onPostStart();
    }

    protected ComposeContainer initEnv() {
        throw new UnsupportedOperationException("initEnv() not implemented");
    }

    protected void onPostStart() {
        // can be overridden
    }

    @Override
    public void destroy() {
        if (env != null) {
            env.stop();
        }
    }

    protected Slf4jLogConsumer createLogConsumer(String containerName) {
        return new Slf4jLogConsumer(
                new ComposeLoggerFactory().create("%s-".formatted(containerName), coreProperties.workingDir()));
    }

    protected Slf4jLogConsumer createLogConsumer(String envStr, String containerName) {
        return new Slf4jLogConsumer(
                new ComposeLoggerFactory().create("%s-%s".formatted(envStr, containerName), coreProperties.workingDir()));
    }

    public ContainerMapping getContainerMapping(String service, int originalPort) {
        return new ContainerMapping(
                env.getServiceHost(service, originalPort),
                env.getServicePort(service, originalPort));
    }

    public Container.ExecResult execInContainer(String container, String... command) {
        final int maxAttempts = 2; // Initial attempt + 1 retry

        for (int attempt = 1; true; attempt++) {
            try {
                log.debug("Executing command in container {} (attempt {}/{}): {}",
                        container, attempt, maxAttempts, String.join(" ", command));

                return CompletableFuture.supplyAsync(() -> {
                    try {
                        return env.getContainerByServiceName(container).orElseThrow()
                                .execInContainer(command);
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to execute command in container", e);
                    }
                }).orTimeout(EXEC_TIMEOUT_SECONDS, TimeUnit.SECONDS).get();

            } catch (Exception e) {
                boolean isTimeout = e.getCause() instanceof TimeoutException;
                if (isTimeout && attempt == maxAttempts) {
                    log.error("Command execution timed out after {} attempts in container {}: {}",
                            maxAttempts, container, String.join(" ", command));
                    throw new RuntimeException(
                            String.format("Command execution timed out after %d attempts (%ds each) in container %s",
                                    maxAttempts, EXEC_TIMEOUT_SECONDS, container),
                            e);
                } else if (isTimeout) {
                    log.warn("Command execution timed out (attempt {}/{}), retrying...", attempt, maxAttempts);
                } else {
                    // Non-timeout exception, rethrow immediately
                    throw new RuntimeException(e);
                }
            }
        }
    }

    public record ContainerMapping(String host, int port) {
    }

}
