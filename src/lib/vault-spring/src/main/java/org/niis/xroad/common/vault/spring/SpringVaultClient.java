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

import jakarta.annotation.Nullable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.core.node.NodeIdentity;
import org.niis.xroad.common.vault.AcmeAccountKey;
import org.niis.xroad.common.vault.AgreementTokenVaultDataUtils;
import org.niis.xroad.common.vault.DsTlsEnrollmentStatus;
import org.niis.xroad.common.vault.MessageLogVaultDataUtils;
import org.niis.xroad.common.vault.NodeScopedSecretPaths;
import org.niis.xroad.common.vault.ResilientVaultCall;
import org.niis.xroad.common.vault.VaultClient;
import org.springframework.vault.core.VaultKeyValueOperations;
import org.springframework.vault.support.VaultResponse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.niis.xroad.common.core.exception.ErrorCode.MISSING_SECRET;

@Slf4j
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class SpringVaultClient implements VaultClient {

    private final VaultKeyValueOperations vaultClient;
    private final VaultKeyValueOperations dsIssuerServiceVaultClient;
    private final String adminServiceTlsPath;
    private final String managementServiceTlsPath;
    private final ResilientVaultCall resilientCall;

    /**
     * @param vaultClient  the key-value operations of this client's own secret store mount
     * @param dsIssuerServiceVaultClient the key-value operations of the Data Space secret store mount that
     *                     holds the Issuer Service's key-pair secrets, or {@code null} if this client has no
     *                     need to read it
     * @param nodeIdentity the identity of the node whose name scopes the admin and management service TLS keys,
     *                     or {@code null} to keep them at the plain, node-independent paths
     * @param retryBudget  the total time a failing secret store call is retried
     */
    public SpringVaultClient(VaultKeyValueOperations vaultClient, @Nullable VaultKeyValueOperations dsIssuerServiceVaultClient,
                              @Nullable NodeIdentity nodeIdentity, Duration retryBudget) {
        this.vaultClient = vaultClient;
        this.dsIssuerServiceVaultClient = dsIssuerServiceVaultClient;
        if (nodeIdentity == null) {
            this.adminServiceTlsPath = ADMIN_SERVICE_TLS_CREDENTIALS_PATH;
            this.managementServiceTlsPath = MANAGEMENT_SERVICE_TLS_CREDENTIALS_PATH;
        } else {
            var nodePaths = new NodeScopedSecretPaths(nodeIdentity);
            this.adminServiceTlsPath = nodePaths.adminServiceTls();
            this.managementServiceTlsPath = nodePaths.managementServiceTls();
        }
        this.resilientCall = new ResilientVaultCall(SpringVaultFailures::isRetryable, retryBudget);
    }

    @Override
    public VaultClient withRetryBudget(Duration budget) {
        return new SpringVaultClient(vaultClient, dsIssuerServiceVaultClient, adminServiceTlsPath, managementServiceTlsPath,
                resilientCall.withBudget(budget));
    }

    @Override
    public InternalSSLKey getInternalTlsCredentials() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        return getTlsCredentials(INTERNAL_TLS_CREDENTIALS_PATH);
    }

    @Override
    public InternalSSLKey getAdminServiceTlsCredentials() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        return getTlsCredentials(adminServiceTlsPath);
    }

    @Override
    public InternalSSLKey getManagementServicesTlsCredentials() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
        return getTlsCredentials(managementServiceTlsPath);
    }

    @Override
    public void createInternalTlsCredentials(InternalSSLKey internalSSLKey) {
        throw new NotImplementedException();
    }

    @Override
    public void createAdminServiceTlsCredentials(InternalSSLKey internalSSLKey) throws IOException, CertificateEncodingException {
        createTlsCredentials(adminServiceTlsPath, internalSSLKey);
    }

    @Override
    public void createManagementServiceTlsCredentials(InternalSSLKey internalSSLKey) throws IOException, CertificateEncodingException {
        createTlsCredentials(managementServiceTlsPath, internalSSLKey);
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
        writeSecret(DS_HTTPS_ENROLLMENT_STATUS_PATH, toDsTlsEnrollmentStatusSecret(status));
    }

    @Override
    public Optional<X509Certificate> getOpMonitorCa() {
        return readSecret(OPMONITOR_CA_PATH).flatMap(this::toOpMonitorCa);
    }

    @Override
    public void createOpMonitorCa(X509Certificate certificate) {
        writeSecret(OPMONITOR_CA_PATH, toOpMonitorCaSecret(certificate));
    }

    @Override
    public void clearOpMonitorCa() {
        writeSecret(OPMONITOR_CA_PATH, toClearedOpMonitorCaSecret());
    }

    @Override
    public void setMLogArchivalSigningSecretKey(String armoredPrivateKey) {
        var secret = new HashMap<String, String>();

        secret.put(PAYLOAD_KEY, armoredPrivateKey);
        writeSecret(MLOG_ARCHIVAL_PGP_SECRET_KEY_PATH, secret);
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
        writeSecret(MLOG_ARCHIVAL_PGP_PUBLIC_KEYS_PATH, secret);
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
        writeSecret(path, secret);
        log.info("Stored encryption key in Vault at path: {}", path);
    }

    @Override
    public Map<String, String> getMLogDBEncryptionSecretKeys() {
        return MessageLogVaultDataUtils.getMLogDBEncryptionSecretKeys(
                this::listSecrets,
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
        writeSecret(getAcmeAccountKeyPath(alias), secret);
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

    @Override
    public void createAgreementTokenSigningKey(String keyId, String keyPairJwk) {
        var secret = AgreementTokenVaultDataUtils.createSigningKeySecret(keyPairJwk);
        String path = AgreementTokenVaultDataUtils.buildSigningKeyPath(keyId);
        writeSecret(path, secret);
        log.info("Stored agreement-token signing key in Vault at path: {}", path);
    }

    @Override
    public Map<String, String> getAgreementTokenSigningKeys() {
        return AgreementTokenVaultDataUtils.getAgreementTokenSigningKeys(
                this::listAgreementTokenSigningKeyIds,
                this::readSecret
        );
    }

    @Override
    public void deleteAgreementTokenSigningKey(String keyId) {
        deleteSecret(AgreementTokenVaultDataUtils.buildSigningKeyPath(keyId));
    }

    @Override
    public boolean dsIssuerServiceKeypairSecretExists(String alias) {
        if (dsIssuerServiceVaultClient == null) {
            throw new IllegalStateException("Data Space secret store mount is not configured for this client");
        }
        var path = DS_ISSUER_SERVICE_KEYPAIR_SECRET_FOLDER + "/" + alias;
        VaultResponse vaultResponse = resilientCall.execute("read", path, () -> dsIssuerServiceVaultClient.get(path));
        return vaultResponse != null && vaultResponse.getData() != null;
    }

    /**
     * Spring Vault's {@code list} already resolves a missing path to an empty list rather than throwing —
     * matching {@link #readSecret(String)}'s contract where a missing secret comes back as {@code null}
     * rather than an exception — so this only guards a possible {@code null} return; any exception it does
     * raise is a genuine infrastructure failure and is left to propagate, never swallowed into "no keys yet".
     */
    private List<String> listAgreementTokenSigningKeyIds(String path) {
        var keys = listSecrets(path);
        return keys == null ? List.of() : keys;
    }

    private Optional<Map<String, Object>> readSecret(String path) {
        if (vaultClient == null) {
            throw new IllegalStateException("Vault KV Secret Engine is not initialized. Check configuration.");
        }

        var vaultResponse = readRawSecret(path);
        if (vaultResponse == null || vaultResponse.getData() == null) {
            return Optional.empty();
        }
        return Optional.of(vaultResponse.getData());
    }

    private void writeSecret(String path, Map<String, String> secret) {
        resilientCall.run("write", path, () -> vaultClient.put(path, secret));
    }

    private VaultResponse readRawSecret(String path) {
        return resilientCall.execute("read", path, () -> vaultClient.get(path));
    }

    private void deleteSecret(String path) {
        resilientCall.run("delete", path, () -> vaultClient.delete(path));
    }

    private List<String> listSecrets(String path) {
        return resilientCall.execute("list", path, () -> vaultClient.list(path));
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
        writeSecret(path, secret);
    }
}
