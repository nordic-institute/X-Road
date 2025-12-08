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
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.test.framework.core.config.TestFrameworkCoreProperties;
import org.niis.xroad.test.framework.core.logging.ComposeLoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.output.Slf4jLogConsumer;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@RequiredArgsConstructor
@SuppressWarnings("checkstyle:SneakyThrowsCheck")
public abstract class BaseComposeSetup implements InitializingBean, DisposableBean {
    private static final int EXEC_TIMEOUT_SECONDS = 20;
    private static final Duration DEFAULT_GRACE_PERIOD_SECONDS = Duration.ofSeconds(5);

    protected static final String ENV_SELENIUM_IMG = "SELENIUM_IMG";
    protected static final String ENV_HOST_TZ = "HOST_TZ";
    protected static final String BROWSER = "browser";
    protected static final int PORT_CHROMEDRIVER = 4444;

    protected final TestFrameworkCoreProperties coreProperties;
    protected ComposeContainer env;
    private DockerStatsMonitor dockerStatsMonitor;

    @Override
    public void afterPropertiesSet() {
        init();
    }

    @SneakyThrows
    protected void init() {
        env = initEnv();
        env.withEnv(ENV_SELENIUM_IMG, coreProperties.selenide().remoteSeleniumImage());
        env.withEnv(ENV_HOST_TZ, ZonedDateTime.now().getZone().toString());
        env.start();

        dockerStatsMonitor = new DockerStatsMonitor();
        dockerStatsMonitor.start();
        log.info("Waiting grace period of {} before continuing..", DEFAULT_GRACE_PERIOD_SECONDS);
        Thread.sleep(DEFAULT_GRACE_PERIOD_SECONDS.toMillis());
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
        if (dockerStatsMonitor != null) {
            dockerStatsMonitor.close();
        }
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

    @SneakyThrows
    public Container.ExecResult execInContainer(String container, String... command) {
        log.debug("Executing command in container {}: {}", container, String.join(" ", command));

        Callable<Container.ExecResult> task = () -> env.getContainerByServiceName(container)
                .orElseThrow(() -> new IllegalStateException("Container not found: " + container))
                .execInContainer(command);

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            return executor.submit(task).get(EXEC_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new TimeoutException("Command execution timed out after %ds in container %s: %s"
                    .formatted(EXEC_TIMEOUT_SECONDS, container, String.join(" ", command)));
        } catch (ExecutionException e) {
            throw e.getCause();
        }
    }

    protected void initSelenideRemoteWebDriver() {
        var chromeContainer = getContainerMapping(BROWSER, PORT_CHROMEDRIVER);
        com.codeborne.selenide.Configuration.remote = "http://%s:%d/wd/hub".formatted(
                chromeContainer.host(), chromeContainer.port());
    }

    public record ContainerMapping(String host, int port) {
    }

}
