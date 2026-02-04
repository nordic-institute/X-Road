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
package org.niis.xroad.opmonitor.core.config;

import ee.ria.xroad.common.conf.InternalSSLKey;

import io.quarkus.vault.VaultKVSecretEngine;
import io.quarkus.vault.VaultPKISecretEngineFactory;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.vault.VaultClient;
import org.niis.xroad.common.vault.VaultKeyClient;
import org.niis.xroad.common.vault.quarkus.QuarkusVaultClient;
import org.niis.xroad.common.vault.quarkus.QuarkusVaultKeyClient;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.util.stream.Stream;

import static java.util.Arrays.stream;

@Slf4j
public class OpMonitorConfig {

    @ApplicationScoped
    public VaultKeyClient vaultKeyClient(VaultPKISecretEngineFactory pkiSecretEngineFactory, OpMonitorTlsProperties tlsProperties) {
        return new QuarkusVaultKeyClient(pkiSecretEngineFactory, tlsProperties.certificateProvisioning());
    }

    @ApplicationScoped
    VaultClient vaultClient(VaultKVSecretEngine kvSecretEngine, VaultKeyClient vaultKeyClient) {
        VaultClient vaultClient = new QuarkusVaultClient(kvSecretEngine);
        try {
            ensureOpMonitorTlsKeyPresent(vaultKeyClient, vaultClient);
        } catch (Exception e) {
            throw XrdRuntimeException.systemException(e);
        }
        return vaultClient;
    }

    private void ensureOpMonitorTlsKeyPresent(VaultKeyClient vaultKeyClient, VaultClient vaultClient)
            throws CertificateException, IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        try {
            vaultClient.getOpmonitorTlsCredentials();
        } catch (Exception e) {
            log.warn("Unable to locate op-monitor TLS credentials, attempting to create new ones", e);
            var vaultKeyData = vaultKeyClient.provisionNewCerts();
            var certChain = Stream.concat(stream(vaultKeyData.identityCertChain()), stream(vaultKeyData.trustCerts()))
                    .toArray(X509Certificate[]::new);
            var internalTlsKey = new InternalSSLKey(vaultKeyData.identityPrivateKey(), certChain);
            vaultClient.createOpmonitorTlsCredentials(internalTlsKey);
            log.info("Successfully created op-monitor TLS credentials");
        }
    }

}
