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

package org.niis.xroad.ss.test.ui.container;

import com.nortal.test.testcontainers.TestableContainerInitializer;
import lombok.SneakyThrows;
import org.niis.xroad.common.test.logging.ComposeLoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.MountableFile;

import java.io.File;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.testcontainers.containers.wait.strategy.Wait.forListeningPort;

@Primary
@Service
public class EnvSetup implements TestableContainerInitializer, DisposableBean {

    public static final String UI = "ui";
    public static final String SIGNER = "signer";
    public static final String CONFIGURATION_CLIENT = "configuration-client";
    public static final String PROXY = "proxy";
    public static final String MONITOR = "monitor";
    public static final String TESTCA = "testca";
    public static final String DB_MESSAGELOG = "db-messagelog";
    public static final String OP_MONITOR = "op-monitor";
    public static final String NGINX = "nginx";

    private static final String COMPOSE_SS_FILE = "../../../deployment/security-server/docker/compose.yaml";
    private static final String COMPOSE_SYSTEMTEST_FILE = "src/intTest/resources/compose.systemtest.yaml";

    private ComposeContainer env;

    @Override
    public void initialize() {
        env = new ComposeContainer("ss-",
                new File(COMPOSE_SS_FILE), new File(COMPOSE_SYSTEMTEST_FILE))
                .withLocalCompose(true)

                .withExposedService(PROXY, Port.PROXY_HTTP, forListeningPort())
                .withExposedService(PROXY, Port.PROXY_HEALTHCHECK, forListeningPort())
                .withExposedService(UI, Port.UI, forListeningPort())
                .withExposedService(DB_MESSAGELOG, Port.DB, forListeningPort())
                .withExposedService(TESTCA, Port.TEST_CA, forListeningPort())

                .withEnv("MONITOR_JMX_PORT", String.valueOf(Port.MONITOR_JMX.get()))

                .withLogConsumer(UI, createLogConsumer(UI))
                .withLogConsumer(PROXY, createLogConsumer(PROXY))
                .withLogConsumer(SIGNER, createLogConsumer(SIGNER))
                .withLogConsumer(CONFIGURATION_CLIENT, createLogConsumer(CONFIGURATION_CLIENT))
                .withLogConsumer(MONITOR, createLogConsumer(MONITOR))
                .withLogConsumer(OP_MONITOR, createLogConsumer(OP_MONITOR));

        env.start();

        //copy nginx files to container to prevent changing local files
        var nginxFiles = MountableFile.forClasspathResource("nginx-container-files/var/lib");
        copyFilesToContainer(NGINX, nginxFiles, "/var/lib");
    }

    @Override
    public void destroy() {
        if (env != null) {
            env.stop();
        }
    }

    @SuppressWarnings("checkstyle:MagicNumber")
    public void restartContainer(String service) {
        var containerState = env.getContainerByServiceName(service).orElseThrow();

        var dockerClient = containerState.getDockerClient();

        dockerClient.stopContainerCmd(containerState.getContainerId()).exec();
        dockerClient.startContainerCmd(containerState.getContainerId()).exec();
        await().atMost(20, TimeUnit.SECONDS).until(containerState::isHealthy);
    }

    private Slf4jLogConsumer createLogConsumer(String containerName) {
        return new Slf4jLogConsumer(new ComposeLoggerFactory().create("%s-".formatted(containerName)));
    }

    @SneakyThrows
    public Container.ExecResult execInContainer(String container, String... command) {
        return env.getContainerByServiceName(container).orElseThrow()
                .execInContainer(command);
    }

    public void copyFilesToContainer(String container, MountableFile mountableFile, String location) {
        env.getContainerByServiceName(container).orElseThrow()
                .copyFileToContainer(mountableFile, location);
    }

    public ContainerMapping getContainerMapping(String service, int originalPort) {
        return new ContainerMapping(
                env.getServiceHost(service, originalPort),
                env.getServicePort(service, originalPort)
        );
    }

    public record ContainerMapping(String host, int port) {
    }
}
