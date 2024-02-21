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

package org.niis.xroad.ss.test.ui.container;

import com.nortal.test.testcontainers.AbstractAuxiliaryContainer;
import com.nortal.test.testcontainers.configuration.ContainerProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.MountableFile;

import java.util.Objects;

@Slf4j
@Component
@SuppressWarnings("checkstyle:MagicNumber")
@RequiredArgsConstructor
public class ReverseProxyAuxiliaryContainer extends AbstractAuxiliaryContainer<ReverseProxyAuxiliaryContainer.NginxContainer> {
    private static final String NETWORK_ALIAS = "cs";
    private static final String NETWORK_ALIAS_MOCK_SERVER = "mock-server";

    private final ContainerProperties testableContainerProperties;

    public static class NginxContainer extends GenericContainer<NginxContainer> {
        public NginxContainer() {
            super("nginx:latest");
        }
    }

    @NotNull
    @Override
    public ReverseProxyAuxiliaryContainer.NginxContainer configure() {
        var nginxConfig = MountableFile.forClasspathResource("nginx-container-files/");

        var logger = LoggerFactory.getLogger(getConfigurationKey());
        var logConsumer = new Slf4jLogConsumer(logger).withSeparateOutputStreams();
        return new ReverseProxyAuxiliaryContainer.NginxContainer()
                .withReuse(testableContainerProperties.getContextContainers().get(getConfigurationKey()).getReuseBetweenRuns())
                .withNetworkAliases(NETWORK_ALIAS, NETWORK_ALIAS_MOCK_SERVER)
                .withCopyFileToContainer(nginxConfig, ".")
                .withCreateContainerCmdModifier(cmd -> Objects.requireNonNull(cmd.getHostConfig()).withMemory(64 * 1024 * 1024L))
                .withLogConsumer(logConsumer)
                .waitingFor(Wait.forLogMessage(".*start worker processes.*", 1));
    }

    @NotNull
    @Override
    public String getConfigurationKey() {
        return "reverse-proxy";
    }


}
