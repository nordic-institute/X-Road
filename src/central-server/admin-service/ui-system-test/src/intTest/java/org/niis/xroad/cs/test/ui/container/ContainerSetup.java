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
package org.niis.xroad.cs.test.ui.container;

import com.nortal.test.testcontainers.configurator.TestContainerConfigurator;
import com.nortal.test.testcontainers.images.builder.ImageFromDockerfile;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.containers.GenericContainer;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Configuration
@SuppressWarnings("checkstyle:MagicNumber")
public class ContainerSetup {
    private static final String VERIFY_EXTERNAL_CONFIGURATION_PATH = "usr/share/xroad/scripts/verify_external_configuration.sh";
    private static final String VERIFY_EXTERNAL_CONFIGURATION_FILE_PATH =
            "src/intTest/resources/container-files/" + VERIFY_EXTERNAL_CONFIGURATION_PATH;

    @Bean
    public TestContainerConfigurator testContainerConfigurator(
            @Value("${test-automation.custom.docker-root}") String dockerRoot,
            @Value("${test-automation.custom.package-repo}") String packageRepo,
            @Value("${test-automation.custom.package-repo-key}") String packageRepoKey) {
        return new TestContainerConfigurator() {
            @NotNull
            @Override
            public ImageFromDockerfile imageDefinition() {
                Path csDockerRoot = Paths.get(dockerRoot);
                Path dockerfilePath = csDockerRoot.resolve("Dockerfile");

                return new ImageFromDockerfile("cs-system-test", true)
                        .withBuildArg("DIST", "jammy")
                        .withBuildArg("REPO", packageRepo)
                        .withBuildArg("REPO_KEY", packageRepoKey)
                        .withFileFromPath("Dockerfile", dockerfilePath)
                        .withFileFromFile(".", csDockerRoot.resolve("build/").toFile())
                        .withFileFromPath(VERIFY_EXTERNAL_CONFIGURATION_PATH, Paths.get(VERIFY_EXTERNAL_CONFIGURATION_FILE_PATH));
            }

            @NotNull
            @Override
            public Map<String, String> environmentalVariables() {
                return new HashMap<>();
            }

            @NotNull
            @Override
            public List<Integer> exposedPorts() {
                return List.of(4000);
            }
        };
    }

    @Bean
    public TestContainerConfigurator.TestContainerInitListener testContainerInitListener() {
        return new TestContainerConfigurator.TestContainerInitListener() {

            @Override
            public void beforeStart(@NotNull GenericContainer<?> genericContainer) {
                //do nothing
            }

            @Override
            public void afterStart(@NotNull GenericContainer<?> genericContainer) {
                //do nothing
            }
        };
    }


}
