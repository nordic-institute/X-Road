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
import ee.ria.xroad.common.crypto.KeyManagers;
import ee.ria.xroad.common.crypto.identifier.KeyAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignMechanism;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.PasswordPolicy;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.builder.EqualsBuilder;
import org.apache.commons.lang3.builder.HashCodeBuilder;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;
import org.jetbrains.annotations.NotNull;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.api.exception.SignerException;
import org.niis.xroad.signer.core.config.SignerProperties;
import org.niis.xroad.signer.core.passwordstore.PasswordStore;
import org.niis.xroad.signer.core.tokenmanager.CertManager;
import org.niis.xroad.signer.core.tokenmanager.KeyManager;
import org.niis.xroad.signer.core.tokenmanager.TokenLookup;
import org.niis.xroad.signer.core.tokenmanager.TokenManager;
import org.niis.xroad.signer.core.tokenmanager.TokenPinManager;
import org.niis.xroad.signer.core.tokenmanager.TokenRegistry;
import org.niis.xroad.signer.core.util.SignerUtil;
import org.niis.xroad.signer.proto.ActivateTokenReq;
import org.niis.xroad.signer.proto.GenerateKeyReq;
import org.niis.xroad.signer.protocol.dto.TokenStatusInfo;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.cert.CertPath;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static ee.ria.xroad.common.ErrorCodes.X_FAILED_TO_GENERATE_R_KEY;
import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_TOKEN_PIN_POLICY_FAILURE;
import static ee.ria.xroad.common.ErrorCodes.X_UNSUPPORTED_SIGN_ALGORITHM;
import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.crypto.identifier.Providers.BOUNCY_CASTLE;
import static ee.ria.xroad.common.util.EncoderUtils.encodeBase64;
import static org.niis.xroad.signer.core.tokenmanager.token.SoftwareTokenUtil.createKeyStore;
import static org.niis.xroad.signer.core.util.ExceptionHelper.keyNotFound;
import static org.niis.xroad.signer.core.util.ExceptionHelper.loginFailed;
import static org.niis.xroad.signer.core.util.ExceptionHelper.pinIncorrect;
import static org.niis.xroad.signer.core.util.ExceptionHelper.tokenNotActive;
import static org.niis.xroad.signer.core.util.ExceptionHelper.tokenNotInitialized;

/**
 * Encapsulates the software token worker which handles software signing and key
 * management.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class SoftwareTokenWorkerFactory {

    private static final Set<SignAlgorithm> SUPPORTED_ALGORITHMS = Set.of(
            SignAlgorithm.SHA1_WITH_RSA,
            SignAlgorithm.SHA256_WITH_RSA,
            SignAlgorithm.SHA384_WITH_RSA,
            SignAlgorithm.SHA512_WITH_RSA,

            SignAlgorithm.SHA1_WITH_ECDSA,
            SignAlgorithm.SHA256_WITH_ECDSA,
            SignAlgorithm.SHA384_WITH_ECDSA,
            SignAlgorithm.SHA512_WITH_ECDSA
    );
    private static final String UNSUPPORTED_SIGN_ALGORITHM = "unsupported_sign_algorithm";

    private final SignerProperties signerProperties;
    private final TokenManager tokenManager;
    private final KeyManager keyManager;
    private final CertManager certManager;
    private final TokenLookup tokenLookup;
    private final TokenPinManager pinManager;
    private final TokenRegistry tokenRegistry;

    public SoftwareTokenWorker create(TokenInfo tokenInfo, TokenDefinition tokenDefinition) {
        return new SoftwareTokenWorker(tokenInfo, tokenDefinition);
    }

    public class SoftwareTokenWorker extends AbstractTokenWorker {
        private final Map<String, PrivateKey> privateKeys = new ConcurrentHashMap<>();
        private final TokenDefinition tokenDefinition;

        private volatile boolean isTokenLoginAllowed = true;

        /**
         * Creates new worker.
         *
         * @param tokenInfo the token info
         */
        public SoftwareTokenWorker(TokenInfo tokenInfo, TokenDefinition tokenDefinition) {
            super(tokenInfo,
                    SoftwareTokenWorkerFactory.this.tokenManager,
                    SoftwareTokenWorkerFactory.this.keyManager,
                    SoftwareTokenWorkerFactory.this.tokenLookup);

            this.tokenDefinition = tokenDefinition;
        }

        @Override
        public void refresh() {
            log.trace("onUpdate()");

            updateStatus();

            try {
                log.debug("Searching for new software keys");
                tokenRegistry.refresh();

                if (isPinStored()) {
                    updateKeys();
                }
            } catch (Exception e) {
                log.error("Failed to add keys not in conf", e);
            }
        }

        @Override
        public void onActionHandled() {
            //No-OP
        }

        @Override
        protected void activateToken(ActivateTokenReq message) {
            if (message.getActivate()) {
                if (!isTokenLoginAllowed) {
                    throw loginFailed("PIN change in progress – token login not allowed");
                }
                activateToken();
            } else {
                deactivateToken();
            }

            tokenManager.setTokenStatus(tokenId, TokenStatusInfo.OK);
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
                keyManager.addKey(tokenId, keyId, result.publicKeyBase64(), result.privateKey(), signMechanism,
                        message.getKeyLabel(), message.getKeyLabel());
                keyManager.setKeyAvailable(keyId, true);
            }

            return tokenLookup.findKeyInfo(keyId);
        }

        private GenerateKeyResult generateKey(GenerateKeyReq message)
                throws Exception {
            log.trace("generateKeys()");

            assertTokenAvailable();

            var keyPair = KeyManagers.getFor(mapAlgorithm(message.getAlgorithm())).generateKeyPair();

            String keyId = SignerUtil.randomId();
            var privateKeyStore = savePkcs12Keystore(keyPair, keyId, getPin());

            String publicKeyBase64 = encodeBase64(keyPair.getPublic().getEncoded());

            return new GenerateKeyResult(keyId, publicKeyBase64, privateKeyStore);
        }

        @Override
        protected void deleteKey(String keyId) {
            // Do nothing
        }

        @Override
        protected void deleteCert(String certId) {
            certManager.removeCert(certId);
        }

        @Override
        protected byte[] sign(String keyId, SignAlgorithm signatureAlgorithmId, byte[] data) throws Exception {
            log.trace("sign({}, {})", keyId, signatureAlgorithmId);

            assertTokenAvailable();

            assertKeyAvailable(keyId);

            PrivateKey key = getPrivateKey(keyId);

            var keyAlgorithm = KeyAlgorithm.valueOf(key.getAlgorithm());
            checkSignatureAlgorithm(signatureAlgorithmId, keyAlgorithm);

            if (!keyAlgorithm.equals(signatureAlgorithmId.algorithm())) {
                throw CodedException.tr(X_UNSUPPORTED_SIGN_ALGORITHM, UNSUPPORTED_SIGN_ALGORITHM,
                        "Unsupported signature algorithm '%s' for key algorithm '%s'", signatureAlgorithmId.name(), keyAlgorithm);
            }

            log.debug("Signing with key '{}' and signature algorithm '{}'", keyId, signatureAlgorithmId);


            SignAlgorithm signAlgorithm = KeyManagers.getFor(keyAlgorithm).getSoftwareTokenSignAlgorithm();
            Signature signature = Signature.getInstance(signAlgorithm.name(), BOUNCY_CASTLE);
            signature.initSign(key);
            signature.update(data);

            return signature.sign();
        }

        private static void checkSignatureAlgorithm(SignAlgorithm signatureAlgorithmId, KeyAlgorithm algorithm) throws CodedException {
            if (!SUPPORTED_ALGORITHMS.contains(signatureAlgorithmId)) {
                throw CodedException.tr(X_UNSUPPORTED_SIGN_ALGORITHM, UNSUPPORTED_SIGN_ALGORITHM,
                        "Unsupported signature algorithm '%s'", signatureAlgorithmId.name());
            }

            if (!algorithm.equals(signatureAlgorithmId.algorithm())) {
                throw CodedException.tr(X_UNSUPPORTED_SIGN_ALGORITHM, UNSUPPORTED_SIGN_ALGORITHM,
                        "Unsupported signature algorithm '%s' for key algorithm '%s'", signatureAlgorithmId.name(), algorithm);
            }
        }

        @Override
        protected byte[] signCertificate(String keyId, SignAlgorithm signatureAlgorithmId, String subjectName, PublicKey publicKey)
                throws Exception {
            log.trace("signCertificate({}, {}, {})", keyId, signatureAlgorithmId, subjectName);
            assertTokenAvailable();
            assertKeyAvailable(keyId);
            KeyInfo keyInfo = tokenLookup.getKeyInfo(keyId);
            PrivateKey privateKey = getPrivateKey(keyId);

            var keyAlgorithm = KeyAlgorithm.valueOf(privateKey.getAlgorithm());
            checkSignatureAlgorithm(signatureAlgorithmId, keyAlgorithm);

            CertificateInfo certificateInfo = keyInfo.getCerts().getFirst();
            X509Certificate issuerX509Certificate = CryptoUtils.readCertificate(certificateInfo.getCertificateBytes());
            JcaX509v3CertificateBuilder certificateBuilder = getCertificateBuilder(subjectName, publicKey,
                    issuerX509Certificate);

            log.debug("Signing certificate with key '{}' and signature algorithm '{}'", keyId, signatureAlgorithmId);
            ContentSigner signer = new JcaContentSignerBuilder(signatureAlgorithmId.name()).build(privateKey);
            X509CertificateHolder certHolder = certificateBuilder.build(signer);
            X509Certificate signedCert = new JcaX509CertificateConverter().getCertificate(certHolder);
            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509", BOUNCY_CASTLE);
            CertPath certPath = certificateFactory.generateCertPath(Arrays.asList(signedCert, issuerX509Certificate));
            return certPath.getEncoded("PEM");
        }

        private void updateStatus() {
            boolean isInitialized = pinManager.tokenHasPin(tokenId);

            if (!isInitialized) {
                tokenManager.setTokenStatus(tokenId, TokenStatusInfo.NOT_INITIALIZED);
            }

            boolean isActive = isInitialized && isPinStored();
            tokenManager.setTokenActive(tokenId, isActive);

            if (isActive) {
                try {
                    activateToken();
                } catch (Exception e) {
                    tokenManager.setTokenActive(tokenId, false);

                    log.trace("Failed to activate token", e);
                }
            }
        }

        private void updateKeys() {
            for (KeyInfo keyInfo : tokenLookup.listKeys(tokenId)) {
                String keyId = keyInfo.getId();

                keyManager.setKeyAvailable(keyId, true);

                if (privateKeys.containsKey(keyId)) {
                    continue;
                }

                try {
                    initializePrivateKey(keyId);
                } catch (Exception e) {
                    keyManager.setKeyAvailable(keyId, false);

                    log.trace("Failed to load private key from key store", e);
                }
            }
        }

        protected SignMechanism resolveSignMechanism(KeyAlgorithm algorithm) {
            return tokenDefinition.resolveSignMechanismName(algorithm)
                    .orElseThrow(() -> new CryptoException("Unsupported key algorithm: " + algorithm));
        }

        private PrivateKey getPrivateKey(String keyId) {
            PrivateKey pkey = privateKeys.get(keyId);

            if (pkey == null) {
                initializePrivateKey(keyId);
            }

            return privateKeys.get(keyId);
        }

        private void initializePrivateKey(String keyId) {
            PrivateKey pkey = loadPrivateKey(keyId);
            if (pkey == null) {
                throw keyNotFound(keyId);
            }
            log.debug("Found usable key '{}'", keyId);
            privateKeys.put(keyId, pkey);
        }

        @Override
        public void initializeToken(char[] pin) {
            verifyPinProvided(pin);

            log.info("Initializing software token with new pin...");

            if (signerProperties.enforceTokenPinPolicy() && !PasswordPolicy.validate(pin)) {
                throw new CodedException(X_TOKEN_PIN_POLICY_FAILURE, "Token PIN does not meet complexity requirements");
            }

            try {
                pinManager.setTokenPin(tokenId, pin);
                tokenManager.enableToken(tokenDefinition);
                tokenManager.setTokenStatus(tokenId, TokenStatusInfo.OK);
            } catch (Exception e) {
                throw translateException(e);
            }
        }

        private void verifyOldAndNewPin(char[] oldPin, char[] newPin) throws Exception {
            // Verify that pin is provided and get the correct pin
            char[] oldPinFromStore = getPin();
            try {
                verifyPin(oldPin);
            } catch (Exception e) {
                log.error("Error verifying token PIN", e);
                tokenManager.setTokenStatus(tokenId, TokenStatusInfo.USER_PIN_INCORRECT);
                throw pinIncorrect();
            }
            if (!Arrays.equals(oldPinFromStore, oldPin)) {
                log.error("The PIN provided for updating the pin was incorrect");
                tokenManager.setTokenStatus(tokenId, TokenStatusInfo.USER_PIN_INCORRECT);
                throw pinIncorrect();
            }
            // Verify new pin complexity
            if (signerProperties.enforceTokenPinPolicy() && !PasswordPolicy.validate(newPin)) {
                throw new CodedException(X_TOKEN_PIN_POLICY_FAILURE,
                        "Token PIN does not meet complexity requirements");
            }
        }

        @Override
        public void handleUpdateTokenPin(char[] oldPin, char[] newPin) {
            log.info("Updating the software token pin to a new one...");

            isTokenLoginAllowed = false; // Prevent token login for the time of pin update
            try {
                verifyOldAndNewPin(oldPin, newPin);
                // Clear pin from pwstore and deactivate token
                PasswordStore.storePassword(tokenId, null);
                deactivateToken();

                pinManager.updateTokenPin(tokenId, oldPin, newPin);
                // Create a new temp folder for working
                log.info("Updating the software token pin was successful!");
            } catch (Exception e) {
                log.info("Updating the software token pin failed!");
                throw translateException(e);
            } finally {
                isTokenLoginAllowed = true; // Allow token login again
            }
        }

        private void activateToken() {
            try {
                verifyPin(PasswordStore.getPassword(tokenId));

                tokenManager.setTokenStatus(tokenId, TokenStatusInfo.OK);
                tokenManager.setTokenActive(tokenId, true);
            } catch (FileNotFoundException e) {
                log.error("Software token not initialized", e);

                tokenManager.setTokenStatus(tokenId, TokenStatusInfo.NOT_INITIALIZED);

                throw tokenNotInitialized(tokenId);
            } catch (Exception e) {
                log.error("Error verifying token PIN", e);

                tokenManager.setTokenStatus(tokenId, TokenStatusInfo.USER_PIN_INCORRECT);

                throw pinIncorrect();
            }
        }

        private void deactivateToken() {
            privateKeys.clear();

            tokenManager.setTokenActive(tokenId, false);
        }

        private PrivateKey loadPrivateKey(String keyId) {
            log.trace("Loading pkcs#12 private key '{}' from", keyId);
            return tokenLookup.getSoftwareTokenKeyStore(keyId).map(privateKeyBytes -> {
                try {
                    var pin = getPin();
                    return SoftwareTokenUtil.loadPrivateKey(privateKeyBytes, keyId, pin);
                } catch (Exception e) {
                    log.error("Failed to load private key from key store", e);
                    return null;
                }
            }).orElse(null);
        }

        private void verifyPin(char[] pin) {
            verifyPinProvided(pin);

            if (!pinManager.verifyTokenPin(tokenId, pin)) {
                throw new SignerException("PIN verification failed for token " + tokenId);
            }
        }

        private char[] getPin() throws Exception {
            final char[] pin = PasswordStore.getPassword(tokenId);
            verifyPinProvided(pin);

            return pin;
        }

        private static void verifyPinProvided(char[] pin) {
            if (pin == null || pin.length == 0) {
                throw new CodedException(X_INTERNAL_ERROR, "PIN not provided");
            }
        }

        private static byte[] savePkcs12Keystore(KeyPair kp, String alias, char[] password)
                throws Exception {
            KeyStore keyStore = createKeyStore(kp, alias, password);

            log.debug("Creating inmemory pkcs#12 keystore with id'{}'", alias);

            try (ByteArrayOutputStream fos = new ByteArrayOutputStream()) {
                keyStore.store(fos, password);
                return fos.toByteArray();
            }
        }

        private void assertTokenAvailable() {
            if (!tokenLookup.isTokenActive(tokenId)) {
                throw tokenNotActive(tokenId);
            }
        }

        @Override
        public boolean isSoftwareToken() {
            return true;
        }

        private record GenerateKeyResult(String keyId, String publicKeyBase64, byte[] privateKey) {
            @NotNull
            @Override
            public String toString() {
                return new ToStringBuilder(this)
                        .append("keyId", keyId)
                        .append("publicKeyBase64", publicKeyBase64)
                        .toString();
            }

            @Override
            public boolean equals(Object o) {
                if (this == o) return true;

                if (o == null || getClass() != o.getClass()) return false;

                GenerateKeyResult that = (GenerateKeyResult) o;

                return new EqualsBuilder().append(keyId, that.keyId)
                        .append(privateKey, that.privateKey)
                        .append(publicKeyBase64, that.publicKeyBase64)
                        .isEquals();
            }

            @Override
            public int hashCode() {
                return new HashCodeBuilder().append(keyId).append(publicKeyBase64).append(privateKey).toHashCode();
            }
        }
    }
}
