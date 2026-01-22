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
package org.niis.xroad.common.vault.quarkus;

import ee.ria.xroad.common.conf.InternalSSLKey;
import ee.ria.xroad.common.util.CryptoUtils;

import io.quarkus.vault.VaultKVSecretEngine;
import io.quarkus.vault.client.VaultClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.vault.MessageLogVaultDataUtils;
import org.niis.xroad.common.vault.VaultClient;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.niis.xroad.common.core.exception.ErrorCode.MISSING_SECRET;

@Slf4j
@RequiredArgsConstructor
public class QuarkusVaultClient implements VaultClient {

    private final VaultKVSecretEngine kvSecretEngine;

    @Override
    public InternalSSLKey getInternalTlsCredentials() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        return getTlsCredentials(INTERNAL_TLS_CREDENTIALS_PATH);
    }

    @Override
    public InternalSSLKey getOpmonitorTlsCredentials() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        return getTlsCredentials(OPMONITOR_TLS_CREDENTIALS_PATH);
    }

    @Override
    public InternalSSLKey getAdminServiceTlsCredentials() {
        throw new NotImplementedException();
    }

    @Override
    public InternalSSLKey getManagementServicesTlsCredentials() {
        throw new NotImplementedException();
    }

    @Override
    public void createInternalTlsCredentials(InternalSSLKey internalSSLKey) throws IOException, CertificateEncodingException {
        createTlsCredentials(INTERNAL_TLS_CREDENTIALS_PATH, internalSSLKey);
    }

    @Override
    public void createOpmonitorTlsCredentials(InternalSSLKey internalSSLKey) throws IOException, CertificateEncodingException {
        createTlsCredentials(OPMONITOR_TLS_CREDENTIALS_PATH, internalSSLKey);
    }

    @Override
    public void createAdminServiceTlsCredentials(InternalSSLKey internalSSLKey) {
        throw new NotImplementedException();
    }

    @Override
    public void createManagementServiceTlsCredentials(InternalSSLKey internalSSLKey) {
        throw new NotImplementedException();
    }

    @Override
    public void setMLogArchivalSigningSecretKey(String armoredPrivateKey) {
        var secret = new HashMap<String, String>();

        secret.put(PAYLOAD_KEY, armoredPrivateKey);
        kvSecretEngine.writeSecret(MLOG_ARCHIVAL_PGP_SECRET_KEY_PATH, secret);
    }

    @Override
    public Optional<String> getMLogArchivalSigningSecretKey() {
        return readSecret(MLOG_ARCHIVAL_PGP_SECRET_KEY_PATH)
                .map(secret -> secret.get(PAYLOAD_KEY));
    }

    @Override
    public void setMLogArchivalEncryptionPublicKeys(String armoredRecipientPublicKeys) {
        var secret = new HashMap<String, String>();

        secret.put(PAYLOAD_KEY, armoredRecipientPublicKeys);
        kvSecretEngine.writeSecret(MLOG_ARCHIVAL_PGP_PUBLIC_KEYS_PATH, secret);
    }

    @Override
    public Optional<String> getMLogArchivalEncryptionPublicKeys() {
        return readSecret(MLOG_ARCHIVAL_PGP_PUBLIC_KEYS_PATH)
                .map(secret -> secret.get(PAYLOAD_KEY));
    }

    @Override
    public void setMLogDBEncryptionSecretKey(String keyId, String base64SecretKey) {
        var secret = MessageLogVaultDataUtils.createEncryptionKeySecret(base64SecretKey);
        String path = MessageLogVaultDataUtils.buildEncryptionKeyPath(keyId);
        kvSecretEngine.writeSecret(path, secret);
        log.info("Stored encryption key in Vault at path: {}", path);
    }

    @Override
    public Map<String, String> getMLogDBEncryptionSecretKeys() {
        return MessageLogVaultDataUtils.getMLogDBEncryptionSecretKeys(
                kvSecretEngine::listSecrets,
                this::readSecret
        );
    }

    @Override
    public void setTokenPin(String tokenId, char[] pin) {
        var secret = new HashMap<String, String>();
        secret.put(PIN_KEY, new String(pin));
        String path = SIGNER_TOKEN_PINS_BASE_PATH + "/" + tokenId;
        kvSecretEngine.writeSecret(path, secret);
    }

    @Override
    public Optional<char[]> getTokenPin(String tokenId) {
        String path = SIGNER_TOKEN_PINS_BASE_PATH + "/" + tokenId;
        return readSecret(path)
                .map(secret -> secret.get(PIN_KEY).toCharArray());
    }

    @Override
    public void deleteTokenPin(String tokenId) {
        String path = SIGNER_TOKEN_PINS_BASE_PATH + "/" + tokenId;
        kvSecretEngine.deleteSecret(path);
    }

    private Optional<Map<String, String>> readSecret(String path) {
        if (kvSecretEngine == null) {
            throw new IllegalStateException("Vault KV Secret Engine is not initialized. Check configuration.");
        }

        try {
            var vaultResponse = kvSecretEngine.readSecret(path);
            if (vaultResponse == null) {
                return Optional.empty();
            }
            return Optional.of(vaultResponse);
        } catch (VaultClientException vaultResponse) {
            log.warn("Failed to read secret from Vault at path {}: {} status {}",
                    path, vaultResponse.getMessage(), vaultResponse.getStatus());
            return Optional.empty();
        }
    }

    private InternalSSLKey getTlsCredentials(String path) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        var vaultResponse = readSecret(path).orElseThrow(() ->
                XrdRuntimeException.systemException(MISSING_SECRET)
                        .details("Failed to get secret from Vault. Secret not found at path: " + path)
                        .build()
        );

        var certificates = CryptoUtils.readCertificates(vaultResponse.get(CERTIFICATE_KEY).getBytes());
        var privateKey = CryptoUtils.getPrivateKey(
                new ByteArrayInputStream(vaultResponse.get(PRIVATEKEY_KEY).getBytes(StandardCharsets.UTF_8))
        );

        return new InternalSSLKey(privateKey, certificates.toArray(X509Certificate[]::new));
    }

    private void createTlsCredentials(String path, InternalSSLKey internalSSLKey) throws IOException, CertificateEncodingException {
        var secret = new HashMap<String, String>();

        var sb = new StringBuilder();
        for (X509Certificate cert : internalSSLKey.getCertChain()) {
            var pem = toPem(cert);
            sb.append(pem);
        }

        secret.put(CERTIFICATE_KEY, sb.toString());
        secret.put(PRIVATEKEY_KEY, toPem(internalSSLKey.getKey()));
        kvSecretEngine.writeSecret(path, secret);
    }

}
