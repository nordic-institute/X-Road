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
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.images.RemoteDockerImage;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.LazyFuture;
import org.testcontainers.utility.MountableFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Configuration
@SuppressWarnings("checkstyle:MagicNumber")
public class ContainerSetup {
    private static final String VERIFY_EXTERNAL_CONFIGURATION_PATH = "usr/share/xroad/scripts/verify_external_configuration.sh";
    private static final String VERIFY_EXTERNAL_CONFIGURATION_FILE_PATH = "container-files/verify_external_configuration.sh";

    @Bean
    public TestContainerConfigurator testContainerConfigurator(
            @Value("${test-automation.custom.image-name}") String imageName) {
        return new TestContainerConfigurator() {
            @NotNull
            @Override
            public LazyFuture<String> imageDefinition() {
                return new RemoteDockerImage(DockerImageName.parse(imageName));
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
                var extConfVerifierScript = MountableFile.forClasspathResource(VERIFY_EXTERNAL_CONFIGURATION_FILE_PATH);
                genericContainer.copyFileToContainer(extConfVerifierScript, VERIFY_EXTERNAL_CONFIGURATION_PATH);
            }
        };
    }


}
