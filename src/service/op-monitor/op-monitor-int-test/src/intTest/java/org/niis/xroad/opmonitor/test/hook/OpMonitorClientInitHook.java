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

package org.niis.xroad.opmonitor.test.hook;

import ee.ria.xroad.common.SystemProperties;

import com.nortal.test.core.services.TestableApplicationInfoProvider;
import com.nortal.test.core.services.hooks.BeforeSuiteHook;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.opmonitor.api.OpMonitoringSystemProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import static ee.ria.xroad.common.PortNumbers.OP_MONITOR_DAEMON_GRPC_PORT;

@Slf4j
@Component
@ConditionalOnProperty(value = "test-automation.custom.op-monitor-container-enabled", havingValue = "true")
@RequiredArgsConstructor
public class OpMonitorClientInitHook implements BeforeSuiteHook {
    private final TestableApplicationInfoProvider testableApplicationInfoProvider;

    @Value("${test-automation.custom.grpc-client-host-override:#{null}}")
    private String grpcHostOverride;

    @Override
    @SneakyThrows
    public void beforeSuite() {
        var host = grpcHostOverride != null ? grpcHostOverride : testableApplicationInfoProvider.getHost();
        var port = testableApplicationInfoProvider.getMappedPort(OP_MONITOR_DAEMON_GRPC_PORT);
        log.info("Will use {}:{} (original port {})  for op monitor RPC connection..", host, port, OP_MONITOR_DAEMON_GRPC_PORT);

        System.setProperty(OpMonitoringSystemProperties.OP_MONITOR_HOST, host);
        System.setProperty(OpMonitoringSystemProperties.OP_MONITOR_GRPC_PORT, String.valueOf(port));
        System.setProperty(SystemProperties.GRPC_INTERNAL_TLS_ENABLED, Boolean.toString(false));
    }

}
