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
package ee.ria.xroad.proxy;

import ee.ria.xroad.common.AddOnStatusDiagnostics;
import ee.ria.xroad.common.BackupEncryptionStatusDiagnostics;
import ee.ria.xroad.common.MessageLogEncryptionStatusDiagnostics;
import ee.ria.xroad.common.conf.serverconf.ServerConfProvider;
import ee.ria.xroad.proxy.addon.AddOn;
import ee.ria.xroad.proxy.admin.AdminService;

import io.grpc.BindableService;
import io.grpc.ServerBuilder;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.rpc.RpcServiceProperties;
import org.niis.xroad.common.rpc.server.RpcServer;
import org.niis.xroad.proxy.ProxyProperties;
import org.niis.xroad.proxy.edc.AssetsRegistrationJob;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Optional;

@Slf4j
@EnableConfigurationProperties({
        ProxyRpcConfig.ProxyRpcServiceProperties.class})
@Configuration
public class ProxyRpcConfig {

    @Bean
    RpcServer proxyRpcServer(final AddOn.BindableServiceRegistry bindableServiceRegistry,
                             List<BindableService> rpcServices, ProxyProperties proxyProperties) throws Exception {
        var proxyRpcServerProperties = proxyProperties.getGrpcServer();

        return RpcServer.newServer(
                proxyRpcServerProperties,
                builder -> {
                    registerServices(bindableServiceRegistry.getRegisteredServices(), builder);
                    registerServices(rpcServices, builder);
                });
    }

    private void registerServices(List<BindableService> services, ServerBuilder<?> builder) {
        services.forEach(service -> {
            log.info("Registering {} RPC service.", service.getClass().getSimpleName());
            builder.addService(service);
        });
    }

    @Bean
    AdminService adminService(ServerConfProvider serverConfProvider,
                              BackupEncryptionStatusDiagnostics backupEncryptionStatusDiagnostics,
                              AddOnStatusDiagnostics addOnStatusDiagnostics,
                              MessageLogEncryptionStatusDiagnostics messageLogEncryptionStatusDiagnostics,
                              Optional<AssetsRegistrationJob> assetsRegistrationJob) {
        return new AdminService(serverConfProvider,
                backupEncryptionStatusDiagnostics,
                addOnStatusDiagnostics,
                messageLogEncryptionStatusDiagnostics,
                assetsRegistrationJob);
    }

    @ConfigurationProperties(prefix = "xroad.proxy.grpc")
    public static class ProxyRpcServiceProperties extends RpcServiceProperties {

        public ProxyRpcServiceProperties(String listenAddress, int port,
                                         String tlsTrustStore, char[] tlsTrustStorePassword,
                                         String tlsKeyStore, char[] tlsKeyStorePassword) {
            super(listenAddress, port, tlsTrustStore, tlsTrustStorePassword, tlsKeyStore, tlsKeyStorePassword);
        }
    }
}
