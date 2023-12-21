/*
 * The MIT License
 * <p>
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */

package org.niis.xroad.common.test.container;

import com.nortal.test.testcontainers.AbstractAuxiliaryContainer;
import com.nortal.test.testcontainers.configuration.ContainerProperties;
import com.nortal.test.testcontainers.images.builder.ImageFromDockerfile;
import com.nortal.test.testcontainers.images.builder.ReusableImageFromDockerfile;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.testcontainers.containers.GenericContainer;

import java.util.Objects;
import java.util.concurrent.Future;


@Component
@Slf4j
@RequiredArgsConstructor
@ConditionalOnProperty(value = "test-automation.containers.context-containers.ca-server.enabled", havingValue = "true")
@SuppressWarnings("checkstyle:MagicNumber")
public class TestCaAuxiliaryContainer extends AbstractAuxiliaryContainer<TestCaAuxiliaryContainer.TestCaContainer> {
    private static final String NETWORK_ALIAS = "ca";

    private final ContainerProperties testableContainerProperties;

    public static class TestCaContainer extends GenericContainer<TestCaContainer> {
        public TestCaContainer(@NonNull Future<String> image) {
            super(image);
        }
    }

    @NotNull
    @Override
    public TestCaContainer configure() {
        return new TestCaContainer(imageDefinition())
                .withExposedPorts(8899, 8888, 8889)
                .withNetworkAliases(NETWORK_ALIAS)
                .withCreateContainerCmdModifier(cmd -> Objects.requireNonNull(cmd.getHostConfig()).withMemory(64 * 1024 * 1024L));
    }

    @SneakyThrows
    private ImageFromDockerfile imageDefinition() {
        log.info("Initializing test-ca..");

        var reuse = testableContainerProperties.getContextContainers().get(getConfigurationKey()).getReuseBetweenRuns();
        return new ReusableImageFromDockerfile("xrd-test-ca", !reuse, reuse)
                .withFileFromClasspath(".", "META-INF/ca-container/");
    }

    @NotNull
    @Override
    public String getConfigurationKey() {
        return "ca-server";
    }

}
