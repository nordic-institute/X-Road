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
package org.niis.xroad.ss.test.ui.container;

import com.google.common.base.Suppliers;
import com.nortal.test.testcontainers.configuration.TestableContainerProperties;
import com.nortal.test.testcontainers.configurator.TestContainerConfigurator;
import com.nortal.test.testcontainers.images.builder.ImageFromDockerfile;
import com.nortal.test.testcontainers.images.builder.ReusableImageFromDockerfile;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.containers.GenericContainer;

import java.io.File;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

@Slf4j
@Configuration
@SuppressWarnings("checkstyle:MagicNumber")
public class ContainerSetup {
    public static final Supplier<Integer> JMX_PORT_SUPPLIER = Suppliers.memoize(ContainerSetup::findRandomPort);
    public static final String JMX_PORT_PROPERTY_KEY = "CONTAINER_JMX_PORT";
    private static final String NETWORK_ALIAS = "ss1";

    @Bean
    public TestContainerConfigurator testContainerConfigurator(

            TestableContainerProperties testableContainerProperties,
            @Value("${test-automation.custom.docker-root}") String dockerRoot,
            @Value("${test-automation.custom.package-repo}") String packageRepo,
            @Value("${test-automation.custom.package-repo-key}") String packageRepoKey) {
        return new TestContainerConfigurator() {
            @NotNull
            @Override
            public ImageFromDockerfile imageDefinition() {
                Path dockerfileRoot = Paths.get(dockerRoot);
                File filesToAdd = Paths.get("src/intTest/resources/container-files/").toFile();

                return new ReusableImageFromDockerfile("ss-system-test",
                        !testableContainerProperties.getReuseBetweenRuns(),
                        testableContainerProperties.getReuseBetweenRuns())
                        .withBuildArg("DIST", "jammy")
                        .withBuildArg("REPO", packageRepo)
                        .withBuildArg("REPO_KEY", packageRepoKey)
                        .withFileFromPath("Dockerfile", dockerfileRoot.resolve("Dockerfile"))
                        .withFileFromPath("files/ss-entrypoint.sh", dockerfileRoot.resolve("files/ss-entrypoint.sh"))
                        .withFileFromPath("files/ss-xroad.conf", dockerfileRoot.resolve("files/ss-xroad.conf"))
                        .withFileFromPath("files/override-docker.ini", dockerfileRoot.resolve("files/override-docker.ini"))
                        .withFileFromFile(".", filesToAdd);
            }

            @NotNull
            @Override
            public Map<String, String> environmentalVariables() {
                return Map.of(JMX_PORT_PROPERTY_KEY, String.valueOf(ContainerSetup.JMX_PORT_SUPPLIER.get()));
            }

            @NotNull
            @Override
            public List<Integer> exposedPorts() {
                return List.of(Port.UI, Port.SERVICE, Port.DB, Port.HEALTHCHECK);
            }

            @NotNull
            @Override
            public List<Integer> fixedExposedPorts() {
                return List.of(ContainerSetup.JMX_PORT_SUPPLIER.get());
            }
        };
    }

    @Bean
    public TestContainerConfigurator.TestContainerInitListener testContainerInitListener() {
        return new TestContainerConfigurator.TestContainerInitListener() {

            @Override
            public void beforeStart(@NotNull GenericContainer<?> genericContainer) {
                genericContainer
                        .withNetworkAliases(NETWORK_ALIAS)
                        .withCreateContainerCmdModifier(cmd -> Objects.requireNonNull(cmd.getHostConfig()).withMemory(2048 * 1024 * 1024L));
            }

            @Override
            @SneakyThrows
            public void afterStart(@NotNull GenericContainer<?> genericContainer) {
                genericContainer.execInContainer("sudo", "-u", "xroad", "/etc/xroad/backup-keys/init_backup_encryption.sh");

                // allow connection to postgres
                genericContainer.execInContainer("sed", "-i",
                        "s/#listen_addresses = 'localhost'/listen_addresses = '*'/", "/etc/postgresql/14/main/postgresql.conf");
                genericContainer.execInContainer("sed", "-ri",
                        "s/host    replication     all             127.0.0.1\\/32/host    all             all             0.0.0.0\\/0/g",
                        "/etc/postgresql/14/main/pg_hba.conf");
                genericContainer.execInContainer("supervisorctl", "restart", "postgres");
            }
        };
    }

    /**
     * Get random available port for use.
     */
    @SneakyThrows
    private static Integer findRandomPort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }
}
