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

package org.niis.xroad.opmonitor.test.container;

import ee.ria.xroad.common.PortNumbers;

import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.common.rpc.credentials.InsecureRpcCredentialsConfigurer;
import org.niis.xroad.opmonitor.client.OpMonitorClient;
import org.niis.xroad.opmonitor.client.OpMonitorRpcChannelProperties;
import org.springframework.stereotype.Component;

import static org.niis.xroad.opmonitor.test.container.OpMonitorIntTestSetup.OP_MONITOR;

/**
 * Holder for OpMonitorClient instance. Holds the opMonitorClient instance that is used in the tests. Otherwise, would
 * need to recreate on every feature.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpMonitorClientHolder {

    private final OpMonitorIntTestSetup opMonitorIntTestSetup;

    private OpMonitorClient opMonitorRpcClientInstance;

    public OpMonitorClient get() {
        return opMonitorRpcClientInstance;
    }

    @SneakyThrows
    public OpMonitorClient initializeOpMonitorClient() {
        var properties = new OpMonitorRpcChannelProperties() {

            @Override
            public String host() {
                return opMonitorIntTestSetup.getContainerMapping(OP_MONITOR, PortNumbers.OP_MONITOR_DAEMON_GRPC_PORT).host();
            }

            @Override
            public int port() {
                return opMonitorIntTestSetup.getContainerMapping(OP_MONITOR, PortNumbers.OP_MONITOR_DAEMON_GRPC_PORT).port();
            }

            @Override
            public int deadlineAfter() {
                return Integer.parseInt(OpMonitorRpcChannelProperties.DEFAULT_DEADLINE_AFTER);
            }
        };

        if (opMonitorRpcClientInstance == null) {
            opMonitorRpcClientInstance = new OpMonitorClient(getFactory(), properties);
            opMonitorRpcClientInstance.init();
        }
        return opMonitorRpcClientInstance;
    }

    private RpcChannelFactory getFactory() {
        return new RpcChannelFactory(new InsecureRpcCredentialsConfigurer());
    }

}
