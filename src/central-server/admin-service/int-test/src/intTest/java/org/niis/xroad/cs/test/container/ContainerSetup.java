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
package org.niis.xroad.cs.test.container;

import com.nortal.test.testcontainers.configurator.SpringBootTestContainerConfigurator;
import com.nortal.test.testcontainers.configurator.TestContainerConfigurator;
import com.nortal.test.testcontainers.images.builder.ImageFromDockerfile;
import org.jetbrains.annotations.NotNull;
import org.niis.xroad.cs.test.container.database.LiquibaseExecutor;
import org.niis.xroad.cs.test.container.database.PostgresContextualContainer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.dockerfile.DockerfileBuilder;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Configuration
@SuppressWarnings("checkstyle:MagicNumber")
public class ContainerSetup {

    @Bean
    public TestContainerConfigurator testContainerConfigurator() {
        return new SpringBootTestContainerConfigurator();
    }

    @Bean
    public SpringBootTestContainerConfigurator.TestContainerCustomizer testContainerCustomizer(
            ExtMockServerContainer mockServerContainer,
            PostgresContextualContainer postgresContextualContainer) {
        return new SpringBootTestContainerConfigurator.TestContainerCustomizer() {
            @Override
            public void customizeImageDefinition(@NotNull ImageFromDockerfile imageFromDockerfile) {
                var filesToAdd = Paths.get("src/intTest/resources/container-files/").toFile();
                imageFromDockerfile.withFileFromFile(".", filesToAdd);
            }

            @Override
            public void customizeDockerFileBuilder(@NotNull DockerfileBuilder dockerfileBuilder) {
                dockerfileBuilder.copy(".", ".");
            }

            @NotNull
            @Override
            public List<String> customizeCommandParts() {
                return List.of(
                        "-Xmx600m",
                        "-XX:MaxMetaspaceSize=200m",
                        "-Dlogging.config=/etc/xroad/conf.d/centralserver-admin-service-logback.xml",
                        "-Dxroad.signer.enforce-token-pin-policy=true");
            }

            @NotNull
            @Override
            public Map<String, String> additionalEnvironmentalVariables() {
                Map<String, String> envConfig = new HashMap<>();
                envConfig.put("spring.datasource.url", postgresContextualContainer.getJdbcUrl());
                envConfig.put("spring.datasource.username", "xrd");
                envConfig.put("spring.datasource.password", "secret");
                envConfig.put("signerProxyMockUri", mockServerContainer.getEndpoint());
                envConfig.put("script.generate-backup.path", "/usr/share/xroad/scripts/backup_xroad_center_configuration.sh");
                envConfig.put("script.restore-configuration.path", "/usr/share/xroad/scripts/restore_xroad_center_configuration.sh");
                return envConfig;
            }

            @NotNull
            @Override
            public List<Integer> additionalExposedPorts() {
                return List.of(4000);
            }
        };
    }

    @Bean
    public TestContainerConfigurator.TestContainerInitListener testContainerInitListener(LiquibaseExecutor liquibaseExecutor) {
        return new TestContainerConfigurator.TestContainerInitListener() {

            @Override
            public void beforeStart(@NotNull GenericContainer<?> genericContainer) {
                genericContainer.waitingFor(Wait.forLogMessage(".*Started Main in.*", 1))
                        .withCreateContainerCmdModifier(cmd -> Objects.requireNonNull(cmd.getHostConfig()).withMemory(768 * 1024 * 1024L));
                liquibaseExecutor.executeChangesets();
            }

            @Override
            public void afterStart(@NotNull GenericContainer<?> genericContainer) {
                //do nothing
            }
        };
    }

}
