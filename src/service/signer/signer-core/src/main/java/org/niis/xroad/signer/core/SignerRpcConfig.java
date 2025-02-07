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
package org.niis.xroad.signer.core;

import ee.ria.xroad.common.SystemProperties;

import io.grpc.BindableService;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.common.rpc.credentials.RpcCredentialsConfigurer;
import org.niis.xroad.common.rpc.server.RpcServer;
import org.niis.xroad.confclient.rpc.ConfClientRpcChannelProperties;
import org.niis.xroad.confclient.rpc.ConfClientRpcClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Slf4j
@Configuration
public class SignerRpcConfig {

    @Bean
    RpcServer rpcServer(final List<BindableService> bindableServices,
                        RpcCredentialsConfigurer rpcCredentialsConfigurer) throws Exception {
        RpcServer rpcServer = new RpcServer(
                SystemProperties.getGrpcInternalHost(),
                SystemProperties.getGrpcSignerPort(),
                rpcCredentialsConfigurer.createServerCredentials(),
                builder -> bindableServices.forEach(bindableService -> {
                    log.info("Registering {} RPC service.", bindableService.getClass().getSimpleName());
                    builder.addService(bindableService);
                }));
        // rpcServer.afterPropertiesSet();
        return rpcServer;
    }

    @Bean
    ConfClientRpcClient confClientRpcClient(RpcChannelFactory rpcChannelFactory, ConfClientRpcChannelProperties channelProperties) {
        return new ConfClientRpcClient(rpcChannelFactory, channelProperties);
    }

    @Bean
    @Deprecated
    ConfClientRpcChannelProperties confClientRpcChannelProperties() {
        return new ConfClientRpcChannelProperties() {
            @Override
            public String host() {
                return DEFAULT_HOST;
            }

            @Override
            public int port() {
                return Integer.parseInt(DEFAULT_PORT);
            }

            @Override
            public int deadlineAfter() {
                return Integer.parseInt(DEFAULT_DEADLINE_AFTER);
            }
        };
    }
}
