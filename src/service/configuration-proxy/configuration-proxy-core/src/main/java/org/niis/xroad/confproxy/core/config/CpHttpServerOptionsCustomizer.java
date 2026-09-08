/*
 * The MIT License
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.confproxy.core.config;

import io.quarkus.vertx.http.HttpServerOptionsCustomizer;
import io.vertx.core.buffer.Buffer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.net.KeyStoreOptions;
import jakarta.inject.Singleton;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.vault.VaultClient;

import java.io.ByteArrayOutputStream;
import java.security.KeyStore;

@Slf4j
@Singleton
@RequiredArgsConstructor
public class CpHttpServerOptionsCustomizer implements HttpServerOptionsCustomizer {
    private static final String KEY_ALIAS = "conf-proxy";
    private final VaultClient vaultClient;


    @Override
    public void customizeHttpsServer(HttpServerOptions httpsOptions) {
        try {
            var tlsCredentials = vaultClient.getConfigurationProxyTlsCredentials();

            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            keyStore.load(null, null);
            keyStore.setKeyEntry(KEY_ALIAS, tlsCredentials.getKey(),
                    null, tlsCredentials.getCertChain());

            try (var baos = new ByteArrayOutputStream()) {
                keyStore.store(baos, null);

                var keyCertOptions = new KeyStoreOptions()
                        .setType("PKCS12")
                        .setPassword(null)
                        .setValue(Buffer.buffer(baos.toByteArray()));
                httpsOptions.setSsl(true);
                httpsOptions.setKeyCertOptions(keyCertOptions);
            }

            log.info("HTTPS server configured with TLS credentials from Vault");
        } catch (Exception e) {
            throw new IllegalStateException("Failed to configure HTTPS server with Vault TLS credentials", e);
        }
    }
}
