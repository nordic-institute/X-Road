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
package org.niis.xroad.proxy.core.configuration;

import ee.ria.xroad.common.util.process.ExternalProcessRunner;

import io.quarkus.runtime.Startup;
import io.quarkus.vault.VaultPKISecretEngineFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.rpc.VaultKeyProvider;
import org.niis.xroad.common.tls.quarkus.vault.QuarkusVaultKeyClient;
import org.niis.xroad.common.tls.vault.VaultKeyClient;
import org.niis.xroad.common.tls.vault.VaultTlsCredentialsProvider;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.opmonitor.api.OpMonitorCommonProperties;
import org.niis.xroad.opmonitor.api.OpMonitoringBuffer;
import org.niis.xroad.proxy.core.ProxyProperties;
import org.niis.xroad.proxy.core.addon.opmonitoring.NoOpMonitoringBuffer;
import org.niis.xroad.proxy.core.addon.opmonitoring.OpMonitoringBufferImpl;
import org.niis.xroad.serverconf.ServerConfCommonProperties;
import org.niis.xroad.serverconf.ServerConfProvider;
import org.niis.xroad.serverconf.impl.ServerConfDatabaseCtx;
import org.niis.xroad.serverconf.impl.ServerConfFactory;

import java.time.Duration;
import java.util.List;

@Slf4j
public class ProxyConfig {

    @ApplicationScoped
    public static class OpMonitoringBufferInitializer {

        @ApplicationScoped
        public VaultKeyClient vaultKeyClient(VaultPKISecretEngineFactory pkiSecretEngineFactory) {
            return new QuarkusVaultKeyClient(
                    pkiSecretEngineFactory,
                    "xrd-pki",
                    Duration.ofDays(3650),
                    "xrd-internal",
                    "localhost",
                    List.of(),
                    List.of("127.0.0.1")
            );
        }

        @Startup
        @ApplicationScoped
        public OpMonitoringBuffer opMonitoringBuffer(ProxyProperties.ProxyAddonProperties addonProperties,
                                                     OpMonitorCommonProperties opMonitorCommonProperties,
                                                     ServerConfProvider serverConfProvider,
                                                     VaultTlsCredentialsProvider vaultTlsCredentialsProvider) throws Exception {

            if (addonProperties.opMonitor().enabled()) {
                log.debug("Initializing op-monitoring addon: OpMonitoringBufferImpl");
                var opMonitoringBuffer = new OpMonitoringBufferImpl(serverConfProvider, opMonitorCommonProperties, vaultTlsCredentialsProvider);
                opMonitoringBuffer.init();
                return opMonitoringBuffer;
            } else {
                log.debug("Initializing NoOpMonitoringBuffer");
                return new NoOpMonitoringBuffer();
            }
        }

        public void cleanup(@Disposes OpMonitoringBuffer opMonitoringBuffer) {
            if (opMonitoringBuffer instanceof OpMonitoringBufferImpl impl)
                impl.destroy();
        }

    }


    @ApplicationScoped
    ServerConfProvider serverConfProvider(ServerConfDatabaseCtx databaseCtx,
                                          ServerConfCommonProperties serverConfProperties,
                                          GlobalConfProvider globalConfProvider,
                                          VaultTlsCredentialsProvider vaultTlsCredentialsProvider) {
        return ServerConfFactory.create(databaseCtx, globalConfProvider, vaultTlsCredentialsProvider, serverConfProperties);
    }

    @ApplicationScoped
    ExternalProcessRunner externalProcessRunner() {
        return new ExternalProcessRunner();
    }

}
