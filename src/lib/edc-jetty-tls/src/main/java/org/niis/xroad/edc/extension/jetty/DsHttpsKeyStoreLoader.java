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
package org.niis.xroad.edc.extension.jetty;

import ee.ria.xroad.common.conf.InternalSSLKey;

import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.vault.VaultClient;
import org.niis.xroad.edc.reload.PeriodicMaterialReloader;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.HexFormat;

/**
 * Builds the DataSpace TLS keystore from the credentials OpenBao holds at {@code tls/ds-https}: a PKCS12
 * keystore with one entry, so it plugs directly into Jetty's {@code SslContextFactory}. The fingerprint used
 * to detect rotation is a hash of the certificate chain bytes — the manual CSR flow and ACME renewal both
 * always pair a certificate change with the key that was current when it was requested, so the chain alone
 * identifies "what is currently being served".
 */
final class DsHttpsKeyStoreLoader implements PeriodicMaterialReloader.MaterialLoader<KeyStore> {

    private static final String KEYSTORE_TYPE = "PKCS12";
    private static final String FINGERPRINT_ALGORITHM = "SHA-256";

    private final VaultClient vaultClient;

    DsHttpsKeyStoreLoader(VaultClient vaultClient) {
        this.vaultClient = vaultClient;
    }

    @Override
    public PeriodicMaterialReloader.Loaded<KeyStore> load() {
        var credentials = readCredentials();
        if (credentials.getCertChain().length == 0) {
            throw new DsTlsKeyStoreLoadException(
                    "DataSpace TLS key exists in OpenBao at tls/ds-https but has no certificate yet "
                            + "(certificate pending). Complete ACME enrollment or upload the signed certificate "
                            + "chain through the admin API.");
        }
        var keyStore = buildKeyStore(credentials);
        return new PeriodicMaterialReloader.Loaded<>(keyStore, fingerprint(credentials.getCertChain()));
    }

    private InternalSSLKey readCredentials() {
        try {
            return vaultClient.getDsHttpsTlsCredentials();
        } catch (XrdRuntimeException e) {
            if (e.isCausedBy(ErrorCode.MISSING_SECRET)) {
                throw new DsTlsKeyStoreLoadException(
                        "No DataSpace TLS certificate found in OpenBao at tls/ds-https. Enable DataSpace TLS "
                                + "enrollment or complete the manual CSR upload through the admin API.", e);
            }
            throw new DsTlsKeyStoreLoadException(
                    "Could not reach OpenBao to load the DataSpace TLS certificate from tls/ds-https: " + e.getMessage(), e);
        } catch (Exception e) {
            throw new DsTlsKeyStoreLoadException(
                    "Could not reach OpenBao to load the DataSpace TLS certificate from tls/ds-https: " + e.getMessage(), e);
        }
    }

    private static KeyStore buildKeyStore(InternalSSLKey credentials) {
        try {
            var keyStore = KeyStore.getInstance(KEYSTORE_TYPE);
            keyStore.load(null, null);
            keyStore.setKeyEntry(InternalSSLKey.KEY_ALIAS, credentials.getKey(),
                    InternalSSLKey.getKEY_PASSWORD(), credentials.getCertChain());
            return keyStore;
        } catch (GeneralSecurityException | IOException e) {
            throw new DsTlsKeyStoreLoadException(
                    "Failed to build the DataSpace TLS keystore from the certificate stored in OpenBao", e);
        }
    }

    private static String fingerprint(X509Certificate[] chain) {
        try {
            var digest = MessageDigest.getInstance(FINGERPRINT_ALGORITHM);
            for (X509Certificate certificate : chain) {
                digest.update(certificate.getEncoded());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException | CertificateEncodingException e) {
            throw new DsTlsKeyStoreLoadException("Failed to fingerprint the DataSpace TLS certificate chain", e);
        }
    }
}
