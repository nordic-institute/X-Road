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

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.rpc.RpcClientProperties;
import org.niis.xroad.confclient.proto.ConfClientRpcClient;
import org.niis.xroad.proxy.proto.ProxyRpcClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@EnableConfigurationProperties({RpcClientsConfig.ConfigurationClientRpcClientProperties.class,
        RpcClientsConfig.ProxyRpcClientProperties.class,
        RpcClientsConfig.SignerRpcClientProperties.class})
@Profile("!test")
@Slf4j
public class RpcClientsConfig {

    @Bean
    ProxyRpcClient proxyRpcClient(ProxyRpcClientProperties proxyRpcClientProperties) throws Exception {
        return new ProxyRpcClient(proxyRpcClientProperties);
    }

    @Bean
    ConfClientRpcClient confClientRpcClient(ConfigurationClientRpcClientProperties properties) {
        return new ConfClientRpcClient(properties);
    }

    @ConfigurationProperties(prefix = "xroad.configuration-client")
    static class ConfigurationClientRpcClientProperties extends RpcClientProperties {
        ConfigurationClientRpcClientProperties(String grpcHost, int grpcPort, boolean grpcTlsEnabled,
                                                      String grpcTlsTrustStore, char[] grpcTlsTrustStorePassword,
                                                      String grpcTlsKeyStore, char[] grpcTlsKeyStorePassword) {
            super(grpcHost, grpcPort, grpcTlsEnabled, grpcTlsTrustStore, grpcTlsTrustStorePassword,
                    grpcTlsKeyStore, grpcTlsKeyStorePassword);
        }
    }

    @ConfigurationProperties(prefix = "xroad.signer")
    @Qualifier("signerRpcClientProperties")
    static class SignerRpcClientProperties extends RpcClientProperties {
        SignerRpcClientProperties(String grpcHost, int grpcPort, boolean grpcTlsEnabled,
                                         String grpcTlsTrustStore, char[] grpcTlsTrustStorePassword,
                                         String grpcTlsKeyStore, char[] grpcTlsKeyStorePassword) {
            super(grpcHost, grpcPort, grpcTlsEnabled, grpcTlsTrustStore, grpcTlsTrustStorePassword,
                    grpcTlsKeyStore, grpcTlsKeyStorePassword);
        }
    }

    @ConfigurationProperties(prefix = "xroad.proxy")
    static class ProxyRpcClientProperties extends RpcClientProperties {
        ProxyRpcClientProperties(String grpcHost, int grpcPort, boolean grpcTlsEnabled,
                                        String grpcTlsTrustStore, char[] grpcTlsTrustStorePassword,
                                        String grpcTlsKeyStore, char[] grpcTlsKeyStorePassword) {
            super(grpcHost, grpcPort, grpcTlsEnabled, grpcTlsTrustStore, grpcTlsTrustStorePassword,
                    grpcTlsKeyStore, grpcTlsKeyStorePassword);
        }
    }

}
