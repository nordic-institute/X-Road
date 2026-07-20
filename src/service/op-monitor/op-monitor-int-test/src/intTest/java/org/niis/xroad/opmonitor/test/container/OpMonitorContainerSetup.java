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

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.properties.config.keys.CommonRpcConfigKeys;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.common.rpc.credentials.InsecureRpcCredentialsConfigurer;
import org.niis.xroad.opmonitor.client.OpMonitorClient;
import org.niis.xroad.opmonitor.client.OpMonitorRpcChannelProperties;
import org.niis.xroad.test.apitest.core.config.ApiTestCoreProperties;
import org.niis.xroad.test.apitest.core.container.BaseComposeSetup;
import org.testcontainers.containers.ComposeContainer;
import org.testcontainers.containers.wait.strategy.Wait;

import java.io.File;
import java.time.Duration;

/**
 * Browserless op-monitor test stack: the op-monitor service and its own PostgreSQL database, seeded via
 * the {@code db-opmonitor-init} Liquibase container. Boots once per JVM launcher session; the
 * {@link OpMonitorClient} is created and initialised once the stack is up.
 */
@Slf4j
public class OpMonitorContainerSetup extends BaseComposeSetup {

    private static final Duration OP_MONITOR_STARTUP_TIMEOUT = Duration.ofSeconds(45);
    private static final String OP_MONITOR = "op-monitor";
    private static final String DB_OP_MONITOR = "db-opmonitor";
    private static final String DB_OP_MONITOR_INIT = "db-opmonitor-init";
    private static final int DB_OP_MONITOR_PORT = 5432;
    private static final String COMPOSE_FILE = "compose.intTest.yaml";

    private OpMonitorClient opMonitorClient;

    public OpMonitorContainerSetup(ApiTestCoreProperties coreProperties) {
        super(coreProperties);
    }

    @Override
    protected String composeProjectName() {
        return "op-monitor-";
    }

    @Override
    protected ComposeContainer initEnv() {
        return new ComposeContainer(composeProjectName(),
                new File(coreProperties.resourceDir() + COMPOSE_FILE))
                .withExposedService(OP_MONITOR,
                        CommonRpcConfigKeys.CHANNEL_OP_MONITOR_PORT.convertedDefaultValue(),
                        Wait.forHealthcheck().withStartupTimeout(OP_MONITOR_STARTUP_TIMEOUT))
                .withExposedService(DB_OP_MONITOR, DB_OP_MONITOR_PORT, Wait.forListeningPort())
                .withLogConsumer(OP_MONITOR, createLogConsumer(OP_MONITOR))
                .withLogConsumer(DB_OP_MONITOR_INIT, createLogConsumer(DB_OP_MONITOR_INIT));
    }

    @Override
    @SneakyThrows
    protected void onPostStart() {
        var mapping = getContainerMapping(OP_MONITOR, CommonRpcConfigKeys.CHANNEL_OP_MONITOR_PORT.convertedDefaultValue());
        var properties = new OpMonitorRpcChannelProperties() {
            @Override
            public String host() {
                return mapping.host();
            }

            @Override
            public int port() {
                return mapping.port();
            }

            @Override
            public int deadlineAfter() {
                return CommonRpcConfigKeys.CHANNEL_OP_MONITOR_DEADLINE_AFTER.convertedDefaultValue();
            }
        };

        opMonitorClient = new OpMonitorClient(new RpcChannelFactory(new InsecureRpcCredentialsConfigurer()), properties);
        opMonitorClient.init();
    }

    /**
     * Returns the op-monitor gRPC client, initialised once the stack is up.
     */
    public OpMonitorClient opMonitorClient() {
        return opMonitorClient;
    }
}
