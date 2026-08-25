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
package org.niis.xroad.common.vault;

import ee.ria.xroad.common.conf.InternalSSLKey;

import org.bouncycastle.util.io.pem.PemObject;
import org.bouncycastle.util.io.pem.PemReader;
import org.bouncycastle.util.io.pem.PemWriter;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;

import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

import static org.bouncycastle.openssl.PEMParser.TYPE_CERTIFICATE;
import static org.bouncycastle.openssl.PEMParser.TYPE_PRIVATE_KEY;
import static org.bouncycastle.openssl.PEMParser.TYPE_PUBLIC_KEY;

public interface VaultClient {
    String PAYLOAD_KEY = "payload";
    String PRIVATEKEY_KEY = "privateKey";
    String PUBLICKEY_KEY = "publicKey";
    String EXPIRES_AT_KEY = "expiresAt";
    String CERTIFICATE_KEY = "certificate";
    String PIN_KEY = "pin";

    String INTERNAL_TLS_CREDENTIALS_PATH = "tls/internal";
    String OPMONITOR_TLS_CREDENTIALS_PATH = "tls/opmonitor";
    String ADMIN_SERVICE_TLS_CREDENTIALS_PATH = "tls/admin-service";
    String MANAGEMENT_SERVICE_TLS_CREDENTIALS_PATH = "tls/management-service";
    String CONFIGURATION_PROXY_TLS_CREDENTIALS_PATH = "tls/configuration-proxy";
    String DS_HTTPS_TLS_CREDENTIALS_PATH = "tls/ds-https";

    String MLOG_ARCHIVAL_PGP_SECRET_KEY_PATH = "message-log/archival/pgp/secret-key";
    String MLOG_ARCHIVAL_PGP_PUBLIC_KEYS_PATH = "message-log/archival/pgp/public-keys";

    String MLOG_DB_ENCRYPTION_SECRET_KEYS_BASE_PATH = "message-log/database-encryption/keys";

    String SIGNER_TOKEN_PINS_BASE_PATH = "signer/token-pins";

    String ACME_ACCOUNT_KEYS_BASE_PATH = "acme/account-keys";

    InternalSSLKey getInternalTlsCredentials() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException;

    InternalSSLKey getOpmonitorTlsCredentials() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException;

    InternalSSLKey getAdminServiceTlsCredentials() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException;

    InternalSSLKey getManagementServicesTlsCredentials() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException;

    void createInternalTlsCredentials(InternalSSLKey internalSSLKey) throws IOException, CertificateEncodingException;

    void createOpmonitorTlsCredentials(InternalSSLKey internalSSLKey) throws IOException, CertificateEncodingException;

    void createAdminServiceTlsCredentials(InternalSSLKey internalSSLKey) throws IOException, CertificateEncodingException;

    void createManagementServiceTlsCredentials(InternalSSLKey internalSSLKey) throws IOException, CertificateEncodingException;

    InternalSSLKey getConfigurationProxyTlsCredentials() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException;

    void createConfigurationProxyTlsCredentials(InternalSSLKey internalSSLKey) throws IOException, CertificateEncodingException;

    InternalSSLKey getDsHttpsTlsCredentials() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException;

    void createDsHttpsTlsCredentials(InternalSSLKey internalSSLKey) throws IOException, CertificateEncodingException;

    void setMLogArchivalSigningSecretKey(String armoredPrivateKey);

    Optional<String> getMLogArchivalSigningSecretKey();

    void setMLogArchivalEncryptionPublicKeys(String armoredRecipientPublicKeys);

    Optional<String> getMLogArchivalEncryptionPublicKeys();

    /**
     * Stores message log database encryption secret key for a specific key ID.
     *
     * @param keyId The key identifier (e.g., "key1", "key2")
     * @param base64SecretKey Base64-encoded secret key bytes
     */
    void setMLogDBEncryptionSecretKey(String keyId, String base64SecretKey);

    /**
     * Retrieves all message log database encryption secret keys.
     * Returns a map where key is keyId and value is base64-encoded secret key.
     * This supports key rotation by allowing multiple keys to be stored.
     *
     * @return Map of keyId to base64-encoded secret keys
     */
    Map<String, String> getMLogDBEncryptionSecretKeys();

    /**
     * Stores a token PIN in OpenBao.
     *
     * @param tokenId The token identifier
     * @param pin Base64-encoded PIN bytes
     */
    void setTokenPin(String tokenId, char[] pin);

    /**
     * Retrieves a token PIN from OpenBao.
     *
     * @param tokenId The token identifier
     * @return Optional containing Base64-encoded PIN bytes, or empty if not found
     */
    Optional<char[]> getTokenPin(String tokenId);

    /**
     * Deletes a token PIN from OpenBao.
     *
     * @param tokenId The token identifier
     */
    void deleteTokenPin(String tokenId);

    /**
     * Stores an ACME account key pair and its rotation-expiry timestamp for the given alias.
     * Aliases are dynamic, derived from an X-Road member ID, so each alias is stored under its own path.
     *
     * @param alias alias identifying the account key pair, distinct per member and key purpose
     * @param acmeAccountKey the key pair and its rotation-expiry timestamp to store
     */
    void createAcmeAccountKey(String alias, AcmeAccountKey acmeAccountKey);

    /**
     * Retrieves the ACME account key pair and its rotation-expiry timestamp for the given alias.
     *
     * @param alias alias identifying the account key pair, distinct per member and key purpose
     * @return the stored key pair and its rotation-expiry timestamp, or empty if none exists yet for this alias
     */
    Optional<AcmeAccountKey> getAcmeAccountKey(String alias);

    default String toPem(PrivateKey privateKey) throws IOException {
        StringWriter stringWriter = new StringWriter();
        try (PemWriter pemWriter = new PemWriter(stringWriter)) {
            PemObject pemObject = new PemObject(TYPE_PRIVATE_KEY, privateKey.getEncoded());
            pemWriter.writeObject(pemObject);
        }
        return stringWriter.toString();
    }

    default String toPem(PublicKey publicKey) {
        StringWriter stringWriter = new StringWriter();
        try (PemWriter pemWriter = new PemWriter(stringWriter)) {
            PemObject pemObject = new PemObject(TYPE_PUBLIC_KEY, publicKey.getEncoded());
            pemWriter.writeObject(pemObject);
        } catch (IOException e) {
            throw XrdRuntimeException.systemException(e);
        }
        return stringWriter.toString();
    }

    default String toPem(X509Certificate certificate) throws IOException, CertificateEncodingException {
        StringWriter stringWriter = new StringWriter();
        try (PemWriter pemWriter = new PemWriter(stringWriter)) {
            PemObject pemObject = new PemObject(TYPE_CERTIFICATE, certificate.getEncoded());
            pemWriter.writeObject(pemObject);
        }
        return stringWriter.toString();
    }

    default PublicKey toPublicKey(String pem) {
        byte[] derBytes;
        try (PemReader pemReader = new PemReader(new StringReader(pem))) {
            PemObject pemObject = pemReader.readPemObject();
            if (pemObject == null) {
                throw XrdRuntimeException.systemException(ErrorCode.CRYPTO_ERROR)
                        .details("Failed to parse PEM-encoded public key")
                        .build();
            }
            derBytes = pemObject.getContent();
        } catch (IOException e) {
            throw XrdRuntimeException.systemException(e);
        }

        var keySpec = new X509EncodedKeySpec(derBytes);
        try {
            return KeyFactory.getInstance("RSA").generatePublic(keySpec);
        } catch (GeneralSecurityException rsaFailure) {
            try {
                return KeyFactory.getInstance("EC").generatePublic(keySpec);
            } catch (GeneralSecurityException ecFailure) {
                throw XrdRuntimeException.systemException(ErrorCode.CRYPTO_ERROR)
                        .details("Neither RSA nor EC worked for PEM-encoded public key")
                        .build();
            }
        }
    }

    /**
     * Builds the KV path for an alias's ACME account key pair, sanitizing the alias into a single,
     * unambiguous path segment so that no two aliases can collide.
     *
     * @param alias alias identifying the account key pair
     * @return the KV path for the alias
     */
    default String getAcmeAccountKeyPath(String alias) {
        return ACME_ACCOUNT_KEYS_BASE_PATH + "/" + encodeAliasAsPathSegment(alias);
    }

    private String encodeAliasAsPathSegment(String alias) {
        if (alias == null || alias.isBlank()) {
            throw XrdRuntimeException.systemException(ErrorCode.INVALID_CHARACTERS)
                    .details("ACME account key pair alias must not be blank")
                    .build();
        }
        return Base64.getUrlEncoder().withoutPadding().encodeToString(alias.getBytes(StandardCharsets.UTF_8));
    }
}
