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
package org.niis.xroad.common.managementservice;

import ee.ria.xroad.common.conf.InternalSSLKey;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.vault.VaultClient;
import org.niis.xroad.common.vault.VaultKeyClient;
import org.springframework.boot.autoconfigure.ssl.SslBundleRegistrar;
import org.springframework.boot.ssl.SslBundle;
import org.springframework.boot.ssl.SslBundleRegistry;
import org.springframework.boot.ssl.SslStoreBundle;

import java.io.IOException;
import java.security.KeyStore;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.util.stream.Stream;

import static java.util.Arrays.stream;

@Slf4j
@RequiredArgsConstructor
public class ManagementServiceSslBundleRegistrar implements SslBundleRegistrar {
    public static final String BUNDLE_NAME = "management-service";

    private final VaultKeyClient vaultKeyClient;
    private final VaultClient vaultClient;

    @Override
    public void registerBundles(SslBundleRegistry registry) {
        log.info("Registering '{}' SSL Bundle", BUNDLE_NAME);
        try {
            ensureTlsKeyPresent();
            var tlsCredentials = vaultClient.getManagementServicesTlsCredentials();

            KeyStore keystore = KeyStore.getInstance("PKCS12");
            keystore.load(null, null);
            keystore.setKeyEntry(BUNDLE_NAME, tlsCredentials.getKey(), null, tlsCredentials.getCertChain());
            SslStoreBundle storeBundle = SslStoreBundle.of(keystore, null, null);
            SslBundle sslBundle = SslBundle.of(storeBundle);
            registry.registerBundle(BUNDLE_NAME, sslBundle);
        } catch (Exception e) {
            throw new IllegalStateException("Unable to register '%s' SSL bundle".formatted(BUNDLE_NAME), e);
        }
    }

    private void ensureTlsKeyPresent() throws CertificateException, IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        try {
            vaultClient.getManagementServicesTlsCredentials();
        } catch (Exception e) {
            log.warn("Unable to locate proxy-ui-api TLS credentials, attempting to create new ones", e);
            var vaultKeyData = vaultKeyClient.provisionNewCerts();
            var certChain = Stream.concat(stream(vaultKeyData.identityCertChain()), stream(vaultKeyData.trustCerts()))
                    .toArray(X509Certificate[]::new);
            var internalTlsKey = new InternalSSLKey(vaultKeyData.identityPrivateKey(), certChain);
            vaultClient.createAdminServiceTlsCredentials(internalTlsKey);
            log.info("Successfully created proxy-ui-api TLS credentials");
        }
    }
}
