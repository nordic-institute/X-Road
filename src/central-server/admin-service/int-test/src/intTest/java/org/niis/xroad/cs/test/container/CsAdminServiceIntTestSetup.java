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

package org.niis.xroad.cs.test.container;

import com.nortal.test.testcontainers.TestableContainerInitializer;
import lombok.SneakyThrows;
import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.test.logging.ComposeLoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.Container;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.time.Duration;

@Slf4j
@Service
public class CsAdminServiceIntTestSetup implements TestableContainerInitializer, DisposableBean {
    private static final Duration STARTUP_TIMEOUT = Duration.ofSeconds(45);

    public static final String CS = "cs-admin-service";
    public static final String MOCKSERVER = "mock-server";
    public static final String POSTGRES = CS;

    private static final String COMPOSE_FILE = "build/resources/intTest/compose.intTest.yaml";

    private ComposeContainer env;

    @Override
    public void initialize() {
        env = new ComposeContainer("cs-", new File(COMPOSE_FILE))
                .withLocalCompose(true)
                .withExposedService(CS, Port.UI, Wait.forHealthcheck().withStartupTimeout(STARTUP_TIMEOUT))
                .withExposedService(POSTGRES, Port.DB, Wait.forListeningPort())
                .withExposedService(MOCKSERVER, Port.MOCKSERVER, Wait.forLogMessage(".*started on port: 1080.*", 1))
                .withLogConsumer(CS, createLogConsumer(CS));

        env.start();
    }

    @Override
    public void destroy() {
        if (env != null) {
            env.stop();
        }
    }

    private Slf4jLogConsumer createLogConsumer(String containerName) {
        return new Slf4jLogConsumer(new ComposeLoggerFactory().create("%s-".formatted(containerName)));
    }

    public ContainerMapping getContainerMapping(String service, int originalPort) {
        return new ContainerMapping(
                env.getServiceHost(service, originalPort),
                env.getServicePort(service, originalPort)
        );
    }

    @SneakyThrows
    public Container.ExecResult execInContainer(String container, String... command) {
        return env.getContainerByServiceName(container).orElseThrow()
                .execInContainer(command);
    }

    public record ContainerMapping(String host, int port) {
    }

    @UtilityClass
    public final class Port {
        public static final int DB = 5432;
        public static final int UI = 4000;
        public static final int MOCKSERVER = 1080;
    }
}
