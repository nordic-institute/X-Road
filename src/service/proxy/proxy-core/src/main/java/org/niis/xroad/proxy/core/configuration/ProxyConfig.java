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

import ee.ria.xroad.common.conf.InternalSSLKey;
import ee.ria.xroad.common.util.process.ExternalProcessRunner;

import io.quarkus.vault.VaultKVSecretEngine;
import io.quarkus.vault.VaultPKISecretEngineFactory;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Disposes;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.vault.VaultClient;
import org.niis.xroad.common.vault.VaultKeyClient;
import org.niis.xroad.common.vault.quarkus.QuarkusVaultClient;
import org.niis.xroad.common.vault.quarkus.QuarkusVaultKeyClient;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.opmonitor.api.OpMonitoringBuffer;
import org.niis.xroad.proxy.core.addon.opmonitoring.NoOpMonitoringBuffer;
import org.niis.xroad.proxy.core.addon.opmonitoring.OpMonitoringBufferImpl;
import org.niis.xroad.serverconf.ServerConfCommonProperties;
import org.niis.xroad.serverconf.ServerConfProvider;
import org.niis.xroad.serverconf.impl.ServerConfDatabaseCtx;
import org.niis.xroad.serverconf.impl.ServerConfFactory;

import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.util.stream.Stream;

import static java.util.Arrays.stream;

@Slf4j
class ProxyConfig {

    @ApplicationScoped
    VaultKeyClient vaultKeyClient(VaultPKISecretEngineFactory pkiSecretEngineFactory, ProxyTlsProperties tlsProperties) {
        return new QuarkusVaultKeyClient(pkiSecretEngineFactory, tlsProperties.certificateProvisioning());
    }

    @ApplicationScoped
    VaultClient vaultClient(VaultKeyClient vaultKeyClient, VaultKVSecretEngine kvSecretEngine) {
        QuarkusVaultClient vaultClient = new QuarkusVaultClient(kvSecretEngine);
        try {
            ensureInternalTlsKeyPresent(vaultKeyClient, vaultClient);
        } catch (Exception e) {
            throw XrdRuntimeException.systemException(e);
        }
        return vaultClient;
    }

    private void ensureInternalTlsKeyPresent(VaultKeyClient vaultKeyClient, VaultClient vaultClient) throws CertificateException,
            IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        try {
            vaultClient.getInternalTlsCredentials();
        } catch (Exception e) {
            log.warn("Unable to locate internal TLS credentials, attempting to create new ones", e);
            VaultKeyClient.VaultKeyData vaultKeyData = vaultKeyClient.provisionNewCerts();
            var certChain = Stream.concat(stream(vaultKeyData.identityCertChain()), stream(vaultKeyData.trustCerts()))
                    .toArray(X509Certificate[]::new);
            var internalTlsKey = new InternalSSLKey(vaultKeyData.identityPrivateKey(), certChain);
            vaultClient.createInternalTlsCredentials(internalTlsKey);
            log.info("Successfully created internal TLS credentials");
        }
    }

    @ApplicationScoped
    static class OpMonitoringBufferInitializer {

        @ApplicationScoped
        OpMonitoringBuffer opMonitoringBuffer(ServerConfProvider serverConfProvider,
                                              ProxyProperties proxyProperties,
                                              VaultClient vaultClient)
                throws UnrecoverableKeyException, CertificateException, KeyStoreException, IOException, NoSuchAlgorithmException,
                InvalidKeySpecException, KeyManagementException {

            if (proxyProperties.addon().opMonitor().enabled()) {
                log.debug("Initializing op-monitoring addon: OpMonitoringBufferImpl");
                var opMonitoringBuffer = new OpMonitoringBufferImpl(
                        serverConfProvider, proxyProperties.addon().opMonitor(), vaultClient,
                        proxyProperties.clientProxy().poolEnableConnectionReuse());
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
                                          VaultClient vaultClient) {
        return ServerConfFactory.create(databaseCtx, globalConfProvider, vaultClient, serverConfProperties);
    }

    @ApplicationScoped
    ExternalProcessRunner externalProcessRunner() {
        return new ExternalProcessRunner();
    }

}
