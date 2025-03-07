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
package org.niis.xroad.signer.client.spring;

import lombok.Setter;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.common.rpc.spring.SpringRpcConfig;
import org.niis.xroad.signer.client.SignerRpcChannelProperties;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.niis.xroad.signer.client.SignerSignClient;
import org.niis.xroad.signer.client.impl.SignerSignRpcClient;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

@Configuration
@Import(SpringRpcConfig.class)
@EnableConfigurationProperties(SpringSignerClientConfiguration.SpringSignerRpcChannelProperties.class)
public class SpringSignerClientConfiguration {

    @Bean
    SignerRpcClient signerRpcClient(RpcChannelFactory rpcChannelFactory, SignerRpcChannelProperties signerRpcChannelProperties) {
        return new SignerRpcClient(rpcChannelFactory, signerRpcChannelProperties);
    }

    @Bean
    SignerSignClient signerSignClient(RpcChannelFactory rpcChannelFactory, SignerRpcChannelProperties signerRpcChannelProperties) {
        return new SignerSignRpcClient(rpcChannelFactory, signerRpcChannelProperties);
    }

    @Setter
    @ConfigurationProperties(prefix = SignerRpcChannelProperties.PREFIX)
    public static class SpringSignerRpcChannelProperties implements SignerRpcChannelProperties {
        private String host = DEFAULT_HOST;
        private int port = Integer.parseInt(DEFAULT_PORT);
        private int deadlineAfter = Integer.parseInt(DEFAULT_DEADLINE_AFTER);

        @Override
        public String host() {
            return host;
        }

        @Override
        public int port() {
            return port;
        }

        @Override
        public int deadlineAfter() {
            return deadlineAfter;
        }
    }
}
