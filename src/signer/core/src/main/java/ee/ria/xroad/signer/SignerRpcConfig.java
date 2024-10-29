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
package ee.ria.xroad.signer;

import io.grpc.BindableService;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.rpc.RpcServiceProperties;
import org.niis.xroad.common.rpc.server.RpcServer;
import org.niis.xroad.confclient.proto.ConfClientRpcClientConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.util.List;

@Slf4j
@Import(ConfClientRpcClientConfiguration.class)
@EnableConfigurationProperties({SignerRpcConfig.SignerRpcServiceProperties.class})
@Configuration
public class SignerRpcConfig {

    @Bean
    RpcServer rpcServer(final List<BindableService> bindableServices, SignerRpcServiceProperties serviceProperties) throws Exception {
        return RpcServer.newServer(
                serviceProperties,
                builder -> bindableServices.forEach(bindableService -> {
                    log.info("Registering {} RPC service.", bindableService.getClass().getSimpleName());
                    builder.addService(bindableService);
                }));
    }


    @ConfigurationProperties(prefix = "xroad.signer.grpc")
    static class SignerRpcServiceProperties extends RpcServiceProperties {

        public SignerRpcServiceProperties(String listenAddress, int port,
                                          String tlsTrustStore, char[] tlsTrustStorePassword,
                                          String tlsKeyStore, char[] tlsKeyStorePassword) {
            super(listenAddress, port, tlsTrustStore, tlsTrustStorePassword, tlsKeyStore, tlsKeyStorePassword);
        }
    }
}
