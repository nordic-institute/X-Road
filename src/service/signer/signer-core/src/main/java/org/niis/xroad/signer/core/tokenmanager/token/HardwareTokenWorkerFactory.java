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
package org.niis.xroad.signer.core.tokenmanager.token;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.crypto.CryptoException;
import ee.ria.xroad.common.crypto.identifier.KeyAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignMechanism;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.EncoderUtils;

import iaik.pkcs.pkcs11.Token;
import iaik.pkcs.pkcs11.TokenException;
import iaik.pkcs.pkcs11.objects.ECDSAPublicKey;
import iaik.pkcs.pkcs11.objects.PrivateKey;
import iaik.pkcs.pkcs11.objects.RSAPublicKey;
import iaik.pkcs.pkcs11.objects.X509PublicKeyCertificate;
import iaik.pkcs.pkcs11.wrapper.PKCS11Exception;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.xml.bind.DatatypeConverter;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.niis.xroad.common.core.annotation.ArchUnitSuppressed;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.core.config.SignerHwTokenAddonProperties;
import org.niis.xroad.signer.core.passwordstore.PasswordStore;
import org.niis.xroad.signer.core.tokenmanager.CertManager;
import org.niis.xroad.signer.core.tokenmanager.KeyManager;
import org.niis.xroad.signer.core.tokenmanager.TokenLookup;
import org.niis.xroad.signer.core.tokenmanager.TokenManager;
import org.niis.xroad.signer.core.tokenmanager.token.helper.KeyPairHelper;
import org.niis.xroad.signer.proto.ActivateTokenReq;
import org.niis.xroad.signer.proto.GenerateKeyReq;
import org.niis.xroad.signer.protocol.dto.TokenStatusInfo;

import java.security.PublicKey;
import java.security.cert.CertPath;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ee.ria.xroad.common.ErrorCodes.X_FAILED_TO_GENERATE_R_KEY;
import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_TOKEN_READONLY;
import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.crypto.identifier.KeyAlgorithm.EC;
import static ee.ria.xroad.common.crypto.identifier.KeyAlgorithm.RSA;
import static ee.ria.xroad.common.crypto.identifier.Providers.BOUNCY_CASTLE;
import static ee.ria.xroad.common.util.EncoderUtils.encodeBase64;
import static org.niis.xroad.signer.core.tokenmanager.token.HardwareTokenUtil.findPrivateKey;
import static org.niis.xroad.signer.core.tokenmanager.token.HardwareTokenUtil.findPrivateKeys;
import static org.niis.xroad.signer.core.tokenmanager.token.HardwareTokenUtil.findPublicKey;
import static org.niis.xroad.signer.core.tokenmanager.token.HardwareTokenUtil.findPublicKeyCertificates;
import static org.niis.xroad.signer.core.tokenmanager.token.HardwareTokenUtil.findPublicKeys;
import static org.niis.xroad.signer.core.tokenmanager.token.HardwareTokenUtil.getTokenStatus;
import static org.niis.xroad.signer.core.util.ExceptionHelper.certWithIdNotFound;
import static org.niis.xroad.signer.core.util.ExceptionHelper.loginFailed;
import static org.niis.xroad.signer.core.util.ExceptionHelper.logoutFailed;
import static org.niis.xroad.signer.core.util.SignerUtil.keyId;

/**
 * Token worker for hardware tokens.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
@ArchUnitSuppressed("NoVanillaExceptions") //TODO XRDDEV-2962 review and refactor if needed
public class HardwareTokenWorkerFactory {
    private final SignerHwTokenAddonProperties hwTokenAddonProperties;
    private final CertManager certManager;
    private final TokenManager tokenManager;
    private final KeyManager keyManager;
    private final TokenLookup tokenLookup;

    public HardwareTokenWorker create(TokenInfo tokenInfo, TokenDefinition tokenDefinition) {
        return new HardwareTokenWorker(tokenInfo, tokenDefinition);
    }

    @ArchUnitSuppressed("NoVanillaExceptions") //TODO XRDDEV-2962 review and refactor if needed
    public class HardwareTokenWorker extends AbstractTokenWorker implements HardwareTokenSigner.SignPrivateKeyProvider {
        // maps key id (hex) to PrivateKey
        private final Map<String, PrivateKey> privateKeyCache = new HashMap<>();
        private final Map<String, List<X509PublicKeyCertificate>> certs = new HashMap<>();
        private final TokenDefinition tokenDefinition;
        @Getter
        private BlockingPKCS11SessionManager managementSessionProvider;
        private HardwareTokenSigner signer;

        /**
         * @param tokenInfo       the token info
         * @param tokenDefinition the token type
         */
        public HardwareTokenWorker(TokenInfo tokenInfo, TokenDefinition tokenDefinition) {
            super(tokenInfo,
                    HardwareTokenWorkerFactory.this.tokenManager,
                    HardwareTokenWorkerFactory.this.keyManager,
                    HardwareTokenWorkerFactory.this.tokenLookup);

            this.tokenDefinition = tokenDefinition;
        }

        @Override
        public void start() {
            log.trace("start()");
            try {
                initialize();
                tokenManager.enableToken(tokenDefinition);
                tokenManager.setTokenStatus(tokenId, TokenStatusInfo.OK);
            } catch (Exception e) {
                tokenManager.disableToken(tokenId);

                log.error("Error initializing token ({})", getWorkerId(), e);

                return;
            }

            try {
                login();
            } catch (Exception e) {
                log.error("Failed to log in to token '{}' at initialization", getWorkerId(), e);
            }
        }

        @Override
        public void destroy() {
            super.destroy();

            try {
                closeActiveSessions();
            } catch (Exception e) {
                log.warn("Failed to close active session", e);
            }
        }

        @Override
        public void reload() {
            start();
        }

        @Override
        public void refresh() throws Exception {
            log.trace("refresh()");

            if (tokenLookup.isTokenAvailable(tokenId) && managementSessionProvider != null) {
                findKeysNotInConf();
                findPublicKeysForPrivateKeys();
                findCertificatesNotInConf();
            }
        }

        @Override
        public void onActionHandled() {
            updateTokenInfo();
        }

        // ----------------------- Message handlers -------------------------------

        @Override
        protected void activateToken(ActivateTokenReq message) {
            if (message.getActivate()) { // login
                log.info("Logging in token '{}'", getWorkerId());

                try {
                    if (managementSessionProvider == null) {
                        initialize();
                    }

                    login();
                } catch (Exception e) {
                    log.warn("Token login failed", e);

                    throw loginFailed(e.getMessage());
                }
            } else { // logout
                log.info("Logging out token '{}'", getWorkerId());

                try {
                    logout();
                } catch (Exception e) {
                    log.warn("Token logout failed", e);

                    throw logoutFailed(e.getMessage());
                }
            }
        }

        @Override
        public KeyInfo handleGenerateKey(GenerateKeyReq message) {
            GenerateKeyResult result;

            try {
                result = generateKey(message);
            } catch (Exception e) {
                log.error("Failed to generate key", e);

                throw translateException(e).withPrefix(X_FAILED_TO_GENERATE_R_KEY);
            }

            String keyId = result.keyId();

            log.debug("Generated new key with id '{}'", keyId);

            if (isKeyMissing(keyId)) {
                var signMechanism = resolveSignMechanism(mapAlgorithm(message.getAlgorithm()));
                keyManager.addKey(tokenId, keyId, result.publicKeyBase64(), signMechanism, message.getKeyLabel(), message.getKeyLabel());
                keyManager.setKeyAvailable(keyId, true);
            }

            return tokenLookup.findKeyInfo(keyId);
        }

        private GenerateKeyResult generateKey(GenerateKeyReq message) throws Exception {
            log.trace("generateKeys()");

            assertTokenWritable();

            return getActiveManagementSessionProvider().executeWithSession(session -> {
                var keyPairHelper = KeyPairHelper.of(mapAlgorithm(message.getAlgorithm()));
                var generatedKP = keyPairHelper.createKeypair(
                        session.get(),
                        message.getKeyLabel(),
                        tokenDefinition.pubKeyAttributes(),
                        tokenDefinition.privKeyAttributes());

                var privateKey = generatedKP.getPrivateKey();

                if (privateKey == null) {
                    throw new CodedException(X_INTERNAL_ERROR, "Could not generate private key");
                }

                var publicKey = generatedKP.getPublicKey();

                if (publicKey == null) {
                    throw new CodedException(X_INTERNAL_ERROR, "Could not generate public key");
                }

                byte[] keyIdBytes = privateKey.getId().getByteArrayValue();
                String keyId = DatatypeConverter.printHexBinary(keyIdBytes);
                byte[] publicKeyBytes = keyPairHelper.generateX509PublicKey(publicKey);

                String publicKeyBase64 = encodeBase64(publicKeyBytes);

                privateKeyCache.put(keyId, privateKey);

                return new GenerateKeyResult(keyId, publicKeyBase64);
            });
        }

        @Override
        protected void deleteKey(String keyId) throws Exception {
            log.trace("deleteKey({})", keyId);

            assertTokenWritable();

            getActiveManagementSessionProvider().executeWithSession(session -> {
                PrivateKey privateKey = getPrivateKey(session, keyId);
                var rawSession = session.get();
                if (privateKey != null) {
                    log.info("Deleting private key '{}' on token '{}'", keyId, getWorkerId());

                    try {
                        rawSession.destroyObject(privateKey);
                        privateKeyCache.remove(keyId);
                    } catch (Exception e) {
                        throw new CodedException(X_INTERNAL_ERROR, "Failed to delete private key '%s' on token '%s': %s",
                                keyId, getWorkerId(), e);
                    }
                } else {
                    log.warn("Could not find private key '{}' on token '{}'", keyId, getWorkerId());
                }

                var publicKey = findPublicKey(session, keyId,
                        tokenDefinition.pubKeyAttributes().getAllowedMechanisms());

                if (publicKey != null) {
                    log.info("Deleting public key '{}' on token '{}'", keyId, getWorkerId());

                    try {
                        rawSession.destroyObject(publicKey);
                    } catch (Exception e) {
                        throw new CodedException(X_INTERNAL_ERROR, "Failed to delete public key '%s' on token '%s': %s",
                                keyId, getWorkerId(), e);
                    }
                } else {
                    log.warn("Could not find public key '{}' on token '{}'", keyId, getWorkerId());
                }

                if (!certs.containsKey(keyId)) {
                    return;
                }

                certs.get(keyId).forEach(cert -> destroyCert(session, cert));
                certs.remove(keyId);
            });
        }

        @Override
        protected void deleteCert(String certId) throws Exception {
            log.trace("deleteCert({})", certId);

            assertTokenWritable();

            KeyInfo keyInfo = tokenLookup.getKeyInfoForCertId(certId);

            if (keyInfo == null) {
                throw certWithIdNotFound(certId);
            }

            if (!certs.containsKey(keyInfo.getId())) {
                return;
            }

            getActiveManagementSessionProvider().executeWithSession(session -> {
                deleteCert(session, keyInfo, certId);
            });
        }

        private void deleteCert(ManagedPKCS11Session session, KeyInfo keyInfo, String certId) {
            keyInfo.getCerts().stream()
                    .filter(cert -> cert.getId().equals(certId))
                    .findFirst()
                    .ifPresent(certInfo -> {
                        List<X509PublicKeyCertificate> certsOnModule = certs.get(keyInfo.getId());

                        for (X509PublicKeyCertificate cert : certsOnModule) {
                            if (Arrays.equals(certInfo.getCertificateBytes(), cert.getValue().getByteArrayValue())) {
                                destroyCert(session, cert);
                                certsOnModule.remove(cert);
                                certManager.removeCert(certId);
                                break;
                            }
                        }
                    });
        }

        @Override
        protected byte[] sign(String keyId, SignAlgorithm signatureAlgorithmId, byte[] data) throws Exception {
            assertKeyAvailable(keyId);

            return signer.sign(keyId, signatureAlgorithmId, data);
        }

        @Override
        protected byte[] signCertificate(String keyId, SignAlgorithm signatureAlgorithmId, String subjectName, PublicKey publicKey)
                throws Exception {
            log.trace("signCertificate({}, {}, {})", keyId, signatureAlgorithmId, subjectName);

            assertKeyAvailable(keyId);
            KeyInfo keyInfo = tokenLookup.getKeyInfo(keyId);
            CertificateInfo certificateInfo = keyInfo.getCerts().getFirst();
            X509Certificate issuerX509Certificate = CryptoUtils.readCertificate(certificateInfo.getCertificateBytes());

            ContentSigner contentSigner = new HardwareTokenContentSigner(signer, keyId, signatureAlgorithmId);

            JcaX509v3CertificateBuilder certificateBuilder = getCertificateBuilder(subjectName, publicKey,
                    issuerX509Certificate);
            X509CertificateHolder certHolder = certificateBuilder.build(contentSigner);
            X509Certificate signedCert = new JcaX509CertificateConverter().getCertificate(certHolder);

            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509", BOUNCY_CASTLE);
            CertPath certPath = certificateFactory.generateCertPath(Arrays.asList(signedCert, issuerX509Certificate));
            return certPath.getEncoded("PEM");
        }

        @Override
        protected SignMechanism resolveSignMechanism(KeyAlgorithm algorithm) {
            return tokenDefinition.resolveSignMechanismName(algorithm)
                    .orElseThrow(() -> new CryptoException("Unsupported key algorithm: " + algorithm));
        }


        // ------------------------------------------------------------------------

        private void findKeysNotInConf() throws Exception {
            log.trace("findKeysNotInConf()");

            try {
                getActiveManagementSessionProvider().executeWithSession(session -> {
                    var keysOnToken = findPublicKeys(session, tokenDefinition.pubKeyAttributes().getAllowedMechanisms());

                    for (var keyOnToken : keysOnToken) {
                        String keyId = keyId(keyOnToken);

                        if (keyId == null) {
                            log.debug("Ignoring public key with no ID");

                            continue;
                        }

                        processKey(keyId, keyOnToken);
                    }
                });
            } catch (PKCS11Exception e) {
                throw e;
            } catch (Exception e) {
                log.error("Failed to find keys from token '{}'", getWorkerId(), e);
            }
        }

        private void processKey(String keyId, iaik.pkcs.pkcs11.objects.PublicKey keyOnToken) throws Exception {
            KeyInfo key = tokenLookup.getKeyInfo(keyId);

            if (key == null) {
                keyManager.addKey(tokenId, keyId, null, resolveSignMechanism(RSA), null, null);
                keyManager.setKeyAvailable(keyId, true);
                key = tokenLookup.getKeyInfo(keyId);
                log.debug("Found new key with id '{}' on token '{}'", keyId, getWorkerId());
            }

            // update the key label
            char[] label = keyOnToken.getLabel().getCharArrayValue();

            if (label != null) {
                keyManager.setKeyLabel(keyId, new String(label));
            }

            if (key.getPublicKey() == null) {
                updatePublicKey(keyId);
            }
        }

        @Override
        public PrivateKey getPrivateKey(ManagedPKCS11Session session, String keyId) throws TokenException {
            PrivateKey privateKey = privateKeyCache.get(keyId);
            if (privateKey == null) {
                log.debug("Key {} not found in cache, trying to find it from hardware token", keyId);
                privateKey = findPrivateKey(session, keyId, tokenDefinition.privKeyAttributes().getAllowedMechanisms());
                privateKeyCache.put(keyId, privateKey);
            }
            return privateKey;
        }

        private void findPublicKeysForPrivateKeys() throws Exception {
            log.trace("findPublicKeysForPrivateKeys()");

            for (KeyInfo key : tokenLookup.listKeys(tokenId)) {
                if (key.getPublicKey() == null && key.getId() != null) {
                    updatePublicKey(key.getId());
                }
            }
        }

        private void findCertificatesNotInConf() throws Exception {
            log.trace("findCertificatesNotInConf()");

            try {
                getActiveManagementSessionProvider().executeWithSession(session -> {
                    List<X509PublicKeyCertificate> certsOnModule = findPublicKeyCertificates(session);
                    List<KeyInfo> existingKeys = tokenLookup.listKeys(tokenId);

                    for (X509PublicKeyCertificate certOnModule : certsOnModule) {
                        byte[] certBytes = certOnModule.getValue().getByteArrayValue();

                        for (KeyInfo key : existingKeys) {
                            if (key.getId().equals(keyId(certOnModule)) && !hasCert(key, certBytes)) {
                                log.debug("Found new certificate for key '{}'", key.getId());

                                certManager.addTransientCert(key.getId(), certBytes);
                                putCert(key.getId(), certOnModule);
                            }
                        }
                    }
                });
            } catch (PKCS11Exception e) {
                throw e;
            } catch (Exception e) {
                log.error("Failed to find certificates not in conf", e);
            }
        }

        private void updatePublicKey(String keyId) throws Exception {
            log.trace("updatePublicKey({})", keyId);

            try {
                getActiveManagementSessionProvider().executeWithSession(session -> {
                    String publicKeyBase64;

                    var publicKey = findPublicKey(session, keyId, tokenDefinition.pubKeyAttributes().getAllowedMechanisms());

                    switch (publicKey) {
                        case RSAPublicKey rsaPublicKey -> {
                            publicKeyBase64 = EncoderUtils.encodeBase64(KeyPairHelper.of(RSA).generateX509PublicKey(rsaPublicKey));
                            keyManager.setPublicKey(keyId, publicKeyBase64);
                        }
                        case ECDSAPublicKey ecPublicKey -> {
                            publicKeyBase64 = EncoderUtils.encodeBase64(KeyPairHelper.of(EC).generateX509PublicKey(ecPublicKey));
                            keyManager.setPublicKey(keyId, publicKeyBase64);
                        }
                        case null, default -> {
                            X509PublicKeyCertificate first = certs.get(keyId).getFirst();
                            X509Certificate cert = CryptoUtils.readCertificate(first.getValue().getByteArrayValue());
                            publicKeyBase64 = encodeBase64(cert.getPublicKey().getEncoded());
                        }
                    }

                    if (publicKeyBase64 != null) {
                        log.debug("Found public key for key '{}'...", keyId);

                        keyManager.setPublicKey(keyId, publicKeyBase64);
                    }
                });
            } catch (PKCS11Exception e) {
                throw e;
            } catch (Exception e) {
                log.error("Failed to find public key for key " + keyId, e);
            }
        }

        // ------------------------------------------------------------------------

        private void initialize() throws Exception {
            log.trace("initialize()");

            closeActiveSessions();

            if (getToken() != null) {
                managementSessionProvider = new BlockingPKCS11SessionManager(getToken(), tokenId);
            }

            updateTokenInfo();
        }

        private void login() throws Exception {
            char[] password = PasswordStore.getPassword(tokenId);

            if (password == null) {
                log.debug("Cannot login, no password stored");

                return;
            }

            log.trace("login()");

            try {
                //Prepare management session
                var managementSession = getActiveManagementSessionProvider();
                if (managementSession.login()) {
                    log.info("User successfully logged in");
                    tokenManager.setTokenStatus(tokenId, TokenStatusInfo.OK);
                    tokenManager.setTokenActive(tokenId, true);
                    managementSession.executeWithSession(this::loadPrivateKeys);
                }
                this.signer = HardwareTokenSigner.create(this, tokenDefinition, getToken(), tokenId, hwTokenAddonProperties);
            } catch (PKCS11Exception e) {
                setTokenStatusFromErrorCode(e.getErrorCode());

                throw e;
            }
        }

        private void logout() throws Exception {
            if (managementSessionProvider == null) {
                return;
            }

            privateKeyCache.clear();

            log.trace("logout()");

            try {
                if (managementSessionProvider.logout()) {
                    log.info("HSM management session successfully logged out");
                    tokenManager.setTokenStatus(tokenId, TokenStatusInfo.OK);
                }
                this.signer.close();
                this.signer = null;
            } catch (PKCS11Exception e) {
                setTokenStatusFromErrorCode(e.getErrorCode());

                throw e;
            } finally {
                tokenManager.setTokenActive(tokenId, false);
            }
        }


        private void loadPrivateKeys(ManagedPKCS11Session session) throws Exception {
            if (managementSessionProvider == null) {
                return;
            }

            privateKeyCache.clear();

            List<PrivateKey> keysOnToken = findPrivateKeys(session, tokenDefinition.privKeyAttributes().getAllowedMechanisms());

            log.trace("Found {} private key(s) on token '{}'", keysOnToken.size(), getWorkerId());

            for (var keyOnToken : keysOnToken) {
                String keyId = keyId(keyOnToken);

                if (keyId == null) {
                    log.debug("Ignoring private key with no ID");

                    continue;
                }

                privateKeyCache.put(keyId, keyOnToken);

                log.trace("Private key '{}' added to token '{}'", keyId, getWorkerId());

                if (isKeyMissing(keyId)) {
                    keyManager.addKey(tokenId, keyId, null, resolveSignMechanism(RSA), null, null);
                } else {
                    log.debug("Private key ({}) found in token '{}'", keyId, getWorkerId());
                }

                keyManager.setKeyAvailable(keyId, true);
            }

            for (KeyInfo keyInfo : tokenLookup.listKeys(tokenId)) {
                String keyId = keyInfo.getId();

                if (!privateKeyCache.containsKey(keyId)) {
                    keyManager.setKeyAvailable(keyId, false);

                    log.debug("Private key ({}) not found in token '{}'", keyId, getWorkerId());
                }
            }

            if (privateKeyCache.isEmpty()) {
                log.warn("No private key(s) found in token '{}'", getWorkerId());
            }
        }

        private void updateTokenInfo() {
            if (getToken() == null) {
                return;
            }

            try {
                Map<String, String> tokenInfo = new HashMap<>();
                HardwareTokenInfo.fillInTokenInfo(getToken().getTokenInfo(), tokenInfo);

                tokenManager.setTokenInfo(tokenId, tokenInfo);
            } catch (Exception e) {
                log.error("Failed to update token info", e);
            }
        }

        private void closeActiveSessions() {
            if (managementSessionProvider != null) {
                managementSessionProvider.close();
            }

            if (signer != null) {
                signer.close();
                signer = null;
            }
        }

        private Token getToken() {
            return ((HardwareTokenDefinition) tokenDefinition).token();
        }

        private void setTokenStatusFromErrorCode(long errorCode) throws Exception {
            TokenStatusInfo status = getTokenStatus(getToken().getTokenInfo(), errorCode);

            if (status != null) {
                tokenManager.setTokenStatus(tokenId, status);
            }
        }

        private BlockingPKCS11SessionManager getActiveManagementSessionProvider() {
            if (managementSessionProvider == null) {
                throw new CodedException(X_INTERNAL_ERROR, "No active session on token %s", tokenId);
            }

            return managementSessionProvider;
        }

        private void assertTokenWritable() {
            if (tokenDefinition.readOnly()) {
                throw CodedException.tr(X_TOKEN_READONLY, "token_is_readonly", "Token '%s' is read only", tokenId);
            }
        }

        private void putCert(String keyId, X509PublicKeyCertificate cert) {
            if (certs.get(keyId) == null) {
                List<X509PublicKeyCertificate> certsOfKey = new ArrayList<>();
                certsOfKey.add(cert);
                certs.put(keyId, certsOfKey);
            } else {
                certs.get(keyId).add(cert);
            }
        }

        private void destroyCert(ManagedPKCS11Session session, X509PublicKeyCertificate cert) {
            try {
                session.get().destroyObject(cert);
            } catch (Exception e) {
                log.error("Failed to delete certificate on token '{}'", getWorkerId(), e);
            }
        }

        private static boolean hasCert(KeyInfo key, byte[] certBytes) {
            for (CertificateInfo certInfo : key.getCerts()) {
                if (Arrays.equals(certBytes, certInfo.getCertificateBytes())) {
                    return true;
                }
            }

            return false;
        }

        @Override
        public boolean isSoftwareToken() {
            return false;
        }

        @Override
        public void handleUpdateTokenPin(char[] oldPin, char[] newPin) {
            //NO-OP
        }

        @Override
        public void initializeToken(char[] pin) {
            //NO-OP
        }

        private record GenerateKeyResult(String keyId, String publicKeyBase64) {
        }
    }
}
