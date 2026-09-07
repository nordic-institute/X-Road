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

package org.niis.xroad.securityserver.restapi.config;

import org.niis.xroad.auxiliaryservice.proto.AuxiliaryServiceRpcChannelProperties;
import org.niis.xroad.auxiliaryservice.proto.AuxiliaryServiceRpcClient;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.common.rpc.spring.SpringRpcConfig;
import org.niis.xroad.confclient.rpc.ConfClientRpcChannelProperties;
import org.niis.xroad.confclient.rpc.ConfClientRpcClient;
import org.niis.xroad.monitor.rpc.EnvMonitorRpcChannelProperties;
import org.niis.xroad.monitor.rpc.MonitorRpcClient;
import org.niis.xroad.opmonitor.client.OpMonitorClient;
import org.niis.xroad.opmonitor.client.OpMonitorRpcChannelProperties;
import org.niis.xroad.proxy.proto.ProxyRpcChannelProperties;
import org.niis.xroad.proxy.proto.ProxyRpcClient;
import org.niis.xroad.signer.client.spring.SpringSignerClientConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import({SpringRpcConfig.class,
        SpringSignerClientConfiguration.class})
class RpcClientsConfig {

    @Bean
    IdentityHubProvisioningRpcChannelProperties identityHubProvisioningRpcChannelProperties(XRoadConfig xRoadConfig) {
        return new XRoadIdentityHubProvisioningRpcChannelProperties(xRoadConfig);
    }

    @Bean
    IdentityHubProvisioningRpcClient identityHubProvisioningRpcClient(RpcChannelFactory rpcChannelFactory,
                                                                      IdentityHubProvisioningRpcChannelProperties channelProperties) {
        return new IdentityHubProvisioningRpcClient(rpcChannelFactory, channelProperties);
    }

    @Bean
    ControlPlaneProvisioningRpcChannelProperties controlPlaneProvisioningRpcChannelProperties(XRoadConfig xRoadConfig) {
        return new XRoadControlPlaneProvisioningRpcChannelProperties(xRoadConfig);
    }

    @Bean
    ControlPlaneProvisioningRpcClient controlPlaneProvisioningRpcClient(
            RpcChannelFactory rpcChannelFactory, ControlPlaneProvisioningRpcChannelProperties channelProperties) {
        return new ControlPlaneProvisioningRpcClient(rpcChannelFactory, channelProperties);
    }

    @Bean
    EnvMonitorRpcChannelProperties envMonitorRpcChannelProperties(XRoadConfig xRoadConfig) {
        return new EnvMonitorRpcChannelProperties(xRoadConfig);
    }

    @Bean
    MonitorRpcClient monitorClient(RpcChannelFactory rpcChannelFactory,
                                   EnvMonitorRpcChannelProperties rpcChannelProperties) {
        return new MonitorRpcClient(rpcChannelFactory, rpcChannelProperties);
    }

    @Bean
    ConfClientRpcChannelProperties confClientRpcChannelProperties(XRoadConfig xRoadConfig) {
        return new ConfClientRpcChannelProperties(xRoadConfig);
    }

    @Bean
    ConfClientRpcClient confClientRpcClient(RpcChannelFactory rpcChannelFactory,
                                            ConfClientRpcChannelProperties channelProperties) {
        return new ConfClientRpcClient(rpcChannelFactory, channelProperties);
    }

    @Bean
    ProxyRpcChannelProperties proxyRpcChannelProperties(XRoadConfig xRoadConfig) {
        return new ProxyRpcChannelProperties(xRoadConfig);
    }

    @Bean
    ProxyRpcClient proxyRpcClient(RpcChannelFactory rpcChannelFactory,
                                  ProxyRpcChannelProperties proxyRpcChannelProperties) {
        return new ProxyRpcClient(rpcChannelFactory, proxyRpcChannelProperties);
    }

    @Bean
    AuxiliaryServiceRpcChannelProperties auxiliaryServiceRpcChannelProperties(XRoadConfig xRoadConfig) {
        return new AuxiliaryServiceRpcChannelProperties(xRoadConfig);
    }

    @Bean
    AuxiliaryServiceRpcClient auxiliaryServiceRpcClient(RpcChannelFactory rpcChannelFactory,
                                                        AuxiliaryServiceRpcChannelProperties auxiliaryServiceRpcChannelProperties) {
        return new AuxiliaryServiceRpcClient(rpcChannelFactory, auxiliaryServiceRpcChannelProperties);
    }

    @Bean
    OpMonitorRpcChannelProperties opMonitorRpcChannelProperties(XRoadConfig xRoadConfig) {
        return new OpMonitorRpcChannelProperties(xRoadConfig);
    }

    @Bean
    public OpMonitorClient opMonitorClient(RpcChannelFactory rpcChannelFactory,
                                           OpMonitorRpcChannelProperties rpcChannelProperties) {
        return new OpMonitorClient(rpcChannelFactory, rpcChannelProperties);
    }

}
