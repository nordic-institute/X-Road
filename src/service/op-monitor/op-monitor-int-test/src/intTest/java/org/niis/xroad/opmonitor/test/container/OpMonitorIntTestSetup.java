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
package org.niis.xroad.opmonitor.test.container;

import org.niis.xroad.test.framework.core.config.TestFrameworkCoreProperties;
import org.niis.xroad.test.framework.core.container.BaseComposeSetup;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.time.Duration;

import static ee.ria.xroad.common.PortNumbers.OP_MONITOR_DAEMON_GRPC_PORT;

@Primary
@Service
public class OpMonitorIntTestSetup extends BaseComposeSetup {

    private static final Duration OP_MONITOR_STARTUP_TIMEOUT = Duration.ofSeconds(45);
    public static final String OP_MONITOR = "op-monitor";
    public static final String DB_OP_MONITOR = "db-opmonitor";
    public static final String DB_OP_MONITOR_INIT = "db-opmonitor-init";
    public static final Integer DB_OP_MONITOR_PORT = 5432;
    private static final String COMPOSE_FILE = "compose.intTest.yaml";

    public OpMonitorIntTestSetup(TestFrameworkCoreProperties coreProperties) {
        super(coreProperties);
    }

    @Override
    protected ComposeContainer initEnv() {
        return new ComposeContainer("op-monitor-",
                new File(coreProperties.resourceDir() + COMPOSE_FILE))
                .withExposedService(OP_MONITOR,
                        OP_MONITOR_DAEMON_GRPC_PORT,
                        Wait.forHealthcheck().withStartupTimeout(OP_MONITOR_STARTUP_TIMEOUT))
                .withExposedService(DB_OP_MONITOR, DB_OP_MONITOR_PORT, Wait.forListeningPort())
                .withLogConsumer(OP_MONITOR, createLogConsumer(OP_MONITOR))
                .withLogConsumer(DB_OP_MONITOR_INIT, createLogConsumer(DB_OP_MONITOR_INIT));
    }

}
