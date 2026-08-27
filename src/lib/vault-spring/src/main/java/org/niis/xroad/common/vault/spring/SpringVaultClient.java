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
package org.niis.xroad.common.vault.spring;

import ee.ria.xroad.common.conf.InternalSSLKey;
import ee.ria.xroad.common.util.CryptoUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.vault.AcmeAccountKey;
import org.niis.xroad.common.vault.DsTlsEnrollmentStatus;
import org.niis.xroad.common.vault.MessageLogVaultDataUtils;
import org.niis.xroad.common.vault.VaultClient;
import org.springframework.vault.core.VaultKeyValueOperations;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.niis.xroad.common.core.exception.ErrorCode.MISSING_SECRET;

@Slf4j
@RequiredArgsConstructor
public class SpringVaultClient implements VaultClient {
    private final VaultKeyValueOperations vaultClient;

    @Override
    public InternalSSLKey getInternalTlsCredentials() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        return getTlsCredentials(INTERNAL_TLS_CREDENTIALS_PATH);
    }

    @Override
    public InternalSSLKey getOpmonitorTlsCredentials() {
        throw new NotImplementedException();
    }

    @Override
    public InternalSSLKey getAdminServiceTlsCredentials() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        return getTlsCredentials(ADMIN_SERVICE_TLS_CREDENTIALS_PATH);
    }

    @Override
    public InternalSSLKey getManagementServicesTlsCredentials() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        return getTlsCredentials(MANAGEMENT_SERVICE_TLS_CREDENTIALS_PATH);
    }

    @Override
    public void createInternalTlsCredentials(InternalSSLKey internalSSLKey) {
        throw new NotImplementedException();
    }

    @Override
    public void createOpmonitorTlsCredentials(InternalSSLKey internalSSLKey) {
        throw new NotImplementedException();
    }

    @Override
    public void createAdminServiceTlsCredentials(InternalSSLKey internalSSLKey) throws IOException, CertificateEncodingException {
        createTlsCredentials(ADMIN_SERVICE_TLS_CREDENTIALS_PATH, internalSSLKey);
    }

    @Override
    public void createManagementServiceTlsCredentials(InternalSSLKey internalSSLKey) throws IOException, CertificateEncodingException {
        createTlsCredentials(MANAGEMENT_SERVICE_TLS_CREDENTIALS_PATH, internalSSLKey);
    }

    @Override
    public InternalSSLKey getConfigurationProxyTlsCredentials() {
        throw new NotImplementedException();
    }

    @Override
    public void createConfigurationProxyTlsCredentials(InternalSSLKey internalSSLKey) {
        throw new NotImplementedException();
    }

    @Override
    public InternalSSLKey getDsHttpsTlsCredentials() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        return getTlsCredentials(DS_HTTPS_TLS_CREDENTIALS_PATH);
    }

    @Override
    public void createDsHttpsTlsCredentials(InternalSSLKey internalSSLKey) throws IOException, CertificateEncodingException {
        createTlsCredentials(DS_HTTPS_TLS_CREDENTIALS_PATH, internalSSLKey);
    }

    @Override
    public Optional<DsTlsEnrollmentStatus> getDsTlsEnrollmentStatus() {
        return readSecret(DS_HTTPS_ENROLLMENT_STATUS_PATH).map(this::toDsTlsEnrollmentStatus);
    }

    @Override
    public void createDsTlsEnrollmentStatus(DsTlsEnrollmentStatus status) {
        vaultClient.put(DS_HTTPS_ENROLLMENT_STATUS_PATH, toDsTlsEnrollmentStatusSecret(status));
    }

    @Override
    public void setMLogArchivalSigningSecretKey(String armoredPrivateKey) {
        var secret = new HashMap<String, String>();

        secret.put(PAYLOAD_KEY, armoredPrivateKey);
        vaultClient.put(MLOG_ARCHIVAL_PGP_SECRET_KEY_PATH, secret);
    }

    @Override
    public Optional<String> getMLogArchivalSigningSecretKey() {
        return readSecret(MLOG_ARCHIVAL_PGP_SECRET_KEY_PATH)
                .map(secret -> secret.get(PAYLOAD_KEY).toString());
    }

    @Override
    public void setMLogArchivalEncryptionPublicKeys(String armoredRecipientPublicKeys) {
        var secret = new HashMap<String, String>();

        secret.put(PAYLOAD_KEY, armoredRecipientPublicKeys);
        vaultClient.put(MLOG_ARCHIVAL_PGP_PUBLIC_KEYS_PATH, secret);
    }

    @Override
    public Optional<String> getMLogArchivalEncryptionPublicKeys() {
        return readSecret(MLOG_ARCHIVAL_PGP_PUBLIC_KEYS_PATH)
                .map(secret -> secret.get(PAYLOAD_KEY).toString());
    }

    @Override
    public void setMLogDBEncryptionSecretKey(String keyId, String base64SecretKey) {
        var secret = MessageLogVaultDataUtils.createEncryptionKeySecret(base64SecretKey);
        String path = MessageLogVaultDataUtils.buildEncryptionKeyPath(keyId);
        vaultClient.put(path, secret);
        log.info("Stored encryption key in Vault at path: {}", path);
    }

    @Override
    public Map<String, String> getMLogDBEncryptionSecretKeys() {
        return MessageLogVaultDataUtils.getMLogDBEncryptionSecretKeys(
                vaultClient::list,
                this::readSecret
        );
    }

    @Override
    public void setTokenPin(String tokenId, char[] pn) {
        throw new NotImplementedException();
    }

    @Override
    public Optional<char[]> getTokenPin(String tokenId) {
        throw new NotImplementedException();
    }

    @Override
    public void deleteTokenPin(String tokenId) {
        throw new NotImplementedException();
    }

    @Override
    public void createAcmeAccountKey(String alias, AcmeAccountKey acmeAccountKey) {
        var secret = new HashMap<String, String>();
        try {
            secret.put(PRIVATEKEY_KEY, toPem(acmeAccountKey.privateKey()));
        } catch (IOException e) {
            throw XrdRuntimeException.systemException(e);
        }
        secret.put(PUBLICKEY_KEY, toPem(acmeAccountKey.publicKey()));
        secret.put(EXPIRES_AT_KEY, acmeAccountKey.expiresAt().toString());
        vaultClient.put(getAcmeAccountKeyPath(alias), secret);
    }

    @Override
    public Optional<AcmeAccountKey> getAcmeAccountKey(String alias) {
        var maybeSecret = readSecret(getAcmeAccountKeyPath(alias));
        if (maybeSecret.isEmpty()) {
            return Optional.empty();
        }
        var vaultResponse = maybeSecret.get();

        try {
            var privateKey = CryptoUtils.getPrivateKey(
                    new ByteArrayInputStream(vaultResponse.get(PRIVATEKEY_KEY).toString().getBytes(StandardCharsets.UTF_8))
            );
            var publicKey = toPublicKey(vaultResponse.get(PUBLICKEY_KEY).toString());
            var expiresAt = Instant.parse(vaultResponse.get(EXPIRES_AT_KEY).toString());

            return Optional.of(new AcmeAccountKey(privateKey, publicKey, expiresAt));
        } catch (IOException | GeneralSecurityException e) {
            throw XrdRuntimeException.systemException(e);
        }
    }

    private Optional<Map<String, Object>> readSecret(String path) {
        if (vaultClient == null) {
            throw new IllegalStateException("Vault KV Secret Engine is not initialized. Check configuration.");
        }

        var vaultResponse = vaultClient.get(path);
        if (vaultResponse == null || vaultResponse.getData() == null) {
            return Optional.empty();
        }
        return Optional.of(vaultResponse.getData());
    }

    private InternalSSLKey getTlsCredentials(String path) throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {

        var vaultResponse = readSecret(path).orElseThrow(() ->
                XrdRuntimeException.systemException(MISSING_SECRET)
                        .details("Failed to get secret from Vault. Secret not found at path: " + path)
                        .build()
        );

        var certificates = CryptoUtils.readCertificates(vaultResponse.get(CERTIFICATE_KEY).toString().getBytes());
        var privateKey = CryptoUtils.getPrivateKey(
                new ByteArrayInputStream(vaultResponse.get(PRIVATEKEY_KEY).toString().getBytes(StandardCharsets.UTF_8))
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
        vaultClient.put(path, secret);
    }


}
