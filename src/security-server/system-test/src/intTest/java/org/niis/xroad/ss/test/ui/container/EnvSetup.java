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
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.SystemUtils;
import org.niis.xroad.common.test.logging.ComposeLoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.utility.MountableFile;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.testcontainers.containers.wait.strategy.Wait.forListeningPort;

@Slf4j
@Component
public class EnvSetup implements TestableContainerInitializer, DisposableBean {

    public static final String UI = "ui";
    public static final String SIGNER = "signer";
    public static final String CONFIGURATION_CLIENT = "configuration-client";
    public static final String PROXY = "proxy";
    public static final String MONITOR = "monitor";
    public static final String BACKUP_MANAGER = "backup-manager";
    public static final String TESTCA = "testca";
    public static final String DB_SERVERCONF = "db-serverconf";
    public static final String DB_MESSAGELOG = "db-messagelog";
    public static final String OP_MONITOR = "op-monitor";
    public static final String NGINX = "nginx";
    public static final String DB_SERVERCONF_INIT = "db-serverconf-init";

    private static final String COMPOSE_SS_FILE = "../../../deployment/security-server/docker/compose.yaml";
    private static final String COMPOSE_SYSTEMTEST_FILE = "src/intTest/resources/compose.systemtest.yaml";

    private ComposeContainer env;

    @Override
    public void initialize() {
        // Creating directories for volume mounts with full permissions
        // to ensure containers have the necessary access to modify their contents.
        prepareDirectories("build/a2c_logs");
        env = new ComposeContainer("ss-",
                new File(COMPOSE_SS_FILE), new File(COMPOSE_SYSTEMTEST_FILE))
                .withLocalCompose(true)

                .withExposedService(PROXY, Port.PROXY_HTTP, forListeningPort())
                .withExposedService(PROXY, Port.PROXY_HEALTHCHECK, forListeningPort())
                .withExposedService(UI, Port.UI, forListeningPort())
                .withExposedService(DB_SERVERCONF, Port.DB, forListeningPort())
                .withExposedService(DB_MESSAGELOG, Port.DB, forListeningPort())
                .withExposedService(TESTCA, Port.TEST_CA, forListeningPort())

                .withLogConsumer(UI, createLogConsumer(UI))
                .withLogConsumer(PROXY, createLogConsumer(PROXY))
                .withLogConsumer(SIGNER, createLogConsumer(SIGNER))
                .withLogConsumer(CONFIGURATION_CLIENT, createLogConsumer(CONFIGURATION_CLIENT))
                .withLogConsumer(MONITOR, createLogConsumer(MONITOR))
                .withLogConsumer(BACKUP_MANAGER, createLogConsumer(BACKUP_MANAGER))
                .withLogConsumer(OP_MONITOR, createLogConsumer(OP_MONITOR));

        env.start();

        //copy nginx files to container to prevent changing local files
        var nginxFiles = MountableFile.forClasspathResource("nginx-container-files/var/lib");
        copyFilesToContainer(NGINX, nginxFiles, "/var/lib");
        execInContainer(BACKUP_MANAGER, "/etc/xroad/backup-keys/init_backup_encryption.sh");

    }

    @SneakyThrows
    private void prepareDirectories(String... paths) {
        if (SystemUtils.IS_OS_UNIX) {
            for (String path : paths) {
                Path dirPath = Paths.get(path);
                dirPath.toFile().mkdirs();
                Files.setPosixFilePermissions(dirPath, PosixFilePermissions.fromString("rwxrwxrwx"));
            }
        }
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

        dockerClient.restartContainerCmd(containerState.getContainerId()).exec();
        await().atMost(20, TimeUnit.SECONDS).until(containerState::isHealthy);
    }

    public void stop(String service) {
        var containerState = env.getContainerByServiceName(service).orElseThrow();
        var dockerClient = containerState.getDockerClient();
        dockerClient.stopContainerCmd(containerState.getContainerId()).exec();
    }

    @SuppressWarnings("checkstyle:magicnumber")
    public void start(String service, boolean waitForHealthy) {
        var containerState = env.getContainerByServiceName(service).orElseThrow();
        var dockerClient = containerState.getDockerClient();
        dockerClient.startContainerCmd(containerState.getContainerId()).exec();
        if (waitForHealthy) {
            await().atMost(20, TimeUnit.SECONDS).until(containerState::isHealthy);
        }
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
