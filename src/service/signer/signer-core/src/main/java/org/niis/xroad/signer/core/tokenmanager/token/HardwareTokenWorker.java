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
import ee.ria.xroad.common.crypto.SignDataPreparer;
import ee.ria.xroad.common.crypto.identifier.KeyAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignMechanism;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.EncoderUtils;

import iaik.pkcs.pkcs11.Mechanism;
import iaik.pkcs.pkcs11.Session;
import iaik.pkcs.pkcs11.Token;
import iaik.pkcs.pkcs11.objects.ECDSAPublicKey;
import iaik.pkcs.pkcs11.objects.PrivateKey;
import iaik.pkcs.pkcs11.objects.RSAPublicKey;
import iaik.pkcs.pkcs11.objects.X509PublicKeyCertificate;
import iaik.pkcs.pkcs11.wrapper.PKCS11Exception;
import jakarta.xml.bind.DatatypeConverter;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.DefaultSignatureAlgorithmIdentifierFinder;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.niis.xroad.signer.api.dto.KeyInfo;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.core.config.SignerProperties;
import org.niis.xroad.signer.core.passwordstore.PasswordStore;
import org.niis.xroad.signer.core.tokenmanager.TokenManager;
import org.niis.xroad.signer.core.tokenmanager.token.helper.KeyPairHelper;
import org.niis.xroad.signer.proto.ActivateTokenReq;
import org.niis.xroad.signer.proto.GenerateKeyReq;
import org.niis.xroad.signer.protocol.dto.TokenStatusInfo;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.security.PublicKey;
import java.security.cert.CertPath;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_KEY_NOT_FOUND;
import static ee.ria.xroad.common.ErrorCodes.X_TOKEN_READONLY;
import static ee.ria.xroad.common.ErrorCodes.X_UNSUPPORTED_SIGN_ALGORITHM;
import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.crypto.Digests.calculateDigest;
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
public class HardwareTokenWorker extends AbstractTokenWorker {

    private final TokenType tokenType;

    // maps signature algorithm id and signing mechanism
    private final Map<SignAlgorithm, Mechanism> signMechanisms;

    // maps key id (hex) to PrivateKey
    private final Map<String, PrivateKey> privateKeys = new HashMap<>();
    private final Map<String, List<X509PublicKeyCertificate>> certs = new HashMap<>();

    private PooledSession activeSession;

    /**
     * @param tokenInfo the token info
     * @param tokenType the token type
     */
    public HardwareTokenWorker(TokenInfo tokenInfo, TokenType tokenType, SignerProperties signerProperties, TokenManager tokenManager) {
        super(tokenInfo, signerProperties, tokenManager);

        this.tokenType = tokenType;

        var tempSignMechanisms = new HashMap<SignAlgorithm, Mechanism>();

        Arrays.stream(KeyAlgorithm.values())
                .map(tokenType::resolveSignMechanismName)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .map(HardwareTokenUtil::createSignMechanisms)
                .forEach(tempSignMechanisms::putAll);

        this.signMechanisms = Map.copyOf(tempSignMechanisms);
    }

    @Override
    public void start() {
        log.trace("start()");
        try {
            initialize();
            tokenManager.setTokenAvailable(tokenId, true);
        } catch (Exception e) {
            tokenManager.setTokenAvailable(tokenId, false);

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
            activeSession.destroy();
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

        if (tokenManager.isTokenAvailable(tokenId) && activeSession != null) {
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
                if (activeSession == null) {
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
    protected GenerateKeyResult generateKey(GenerateKeyReq message) throws Exception {
        log.trace("generateKeys()");

        assertTokenWritable();
        assertActiveSession();

        return activeSession.executeWithSession(session -> {
            var keyPairHelper = KeyPairHelper.of(mapAlgorithm(message.getAlgorithm()));
            var generatedKP = keyPairHelper.createKeypair(
                    session,
                    message.getKeyLabel(),
                    tokenType.getPubKeyAttributes(),
                    tokenType.getPrivKeyAttributes());

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

            privateKeys.put(keyId, privateKey);

            return new GenerateKeyResult(keyId, publicKeyBase64);
        });
    }

    @Override
    protected void deleteKey(String keyId) throws Exception {
        log.trace("deleteKey({})", keyId);

        assertTokenWritable();
        assertActiveSession();

        activeSession.executeWithSession(session -> {
            PrivateKey privateKey = getPrivateKey(session, keyId);

            if (privateKey != null) {
                log.info("Deleting private key '{}' on token '{}'", keyId, getWorkerId());

                try {
                    session.destroyObject(privateKey);
                    privateKeys.remove(keyId);
                } catch (Exception e) {
                    throw new CodedException(X_INTERNAL_ERROR, "Failed to delete private key '%s' on token '%s': %s",
                            keyId, getWorkerId(), e);
                }
            } else {
                log.warn("Could not find private key '{}' on token '{}'", keyId, getWorkerId());
            }

            var publicKey = findPublicKey(session, keyId,
                    tokenType.getPubKeyAttributes().getAllowedMechanisms());

            if (publicKey != null) {
                log.info("Deleting public key '{}' on token '{}'", keyId, getWorkerId());

                try {
                    session.destroyObject(publicKey);
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
        assertActiveSession();

        KeyInfo keyInfo = tokenManager.getKeyInfoForCertId(certId);

        if (keyInfo == null) {
            throw certWithIdNotFound(certId);
        }

        if (!certs.containsKey(keyInfo.getId())) {
            return;
        }

        activeSession.executeWithSession(session -> {
            for (CertificateInfo certInfo : keyInfo.getCerts()) {
                if (certInfo.getId().equals(certId)) {
                    List<X509PublicKeyCertificate> certsOnModule = certs.get(keyInfo.getId());

                    for (X509PublicKeyCertificate cert : certsOnModule) {
                        if (Arrays.equals(certInfo.getCertificateBytes(), cert.getValue().getByteArrayValue())) {
                            destroyCert(session, cert);
                            certsOnModule.remove(cert);
                            tokenManager.removeCert(certId);
                            break;
                        }
                    }

                    return;
                }
            }
        });
    }

    @Override
    protected byte[] sign(String keyId, SignAlgorithm signatureAlgorithmId, byte[] data) throws Exception {
        log.trace("sign({}, {})", keyId, signatureAlgorithmId);

        assertActiveSession();
        pinVerificationPerSigningLogin();
        assertKeyAvailable(keyId);

        return activeSession.executeWithSession(session -> {
            PrivateKey key = getPrivateKey(session, keyId);
            if (key == null) {
                throw CodedException.tr(X_KEY_NOT_FOUND, "key_not_found_on_token", "Key '%s' not found on token '%s'",
                        keyId, tokenId);
            }

            log.debug("Signing with key '{}' and signature algorithm '{}'", keyId, signatureAlgorithmId);
            try {
                var signMechanism = verifyAndReturnSignMechanism(signatureAlgorithmId, KeyAlgorithm.valueOf(key.getKeyType().toString()));

                session.signInit(signMechanism, key);
                return session.sign(data);
            } finally {
                pinVerificationPerSigningLogout();
            }
        });
    }

    private Mechanism verifyAndReturnSignMechanism(SignAlgorithm signatureAlgorithmId, KeyAlgorithm algorithm) throws CodedException {
        Mechanism signMechanism = signMechanisms.get(signatureAlgorithmId);

        if (signMechanism == null) {
            throw CodedException.tr(X_UNSUPPORTED_SIGN_ALGORITHM, "unsupported_sign_algorithm",
                    "Unsupported signature algorithm '%s'", signatureAlgorithmId);
        }

        if (!algorithm.equals(signatureAlgorithmId.algorithm())) {
            throw CodedException.tr(X_UNSUPPORTED_SIGN_ALGORITHM, "unsupported_sign_algorithm",
                    "Unsupported signature algorithm '%s' for key algorithm '%s'", signatureAlgorithmId.name(), algorithm);
        }

        return signMechanism;
    }

    protected byte[] signCertificate(String keyId, SignAlgorithm signatureAlgorithmId, String subjectName, PublicKey publicKey)
            throws Exception {
        log.trace("signCertificate({}, {}, {})", keyId, signatureAlgorithmId, subjectName);

        assertKeyAvailable(keyId);
        KeyInfo keyInfo = tokenManager.getKeyInfo(keyId);
        CertificateInfo certificateInfo = keyInfo.getCerts().getFirst();
        X509Certificate issuerX509Certificate = CryptoUtils.readCertificate(certificateInfo.getCertificateBytes());

        return activeSession.executeWithSession(session -> {
            ContentSigner contentSigner = new HardwareTokenContentSigner(session, keyId, signatureAlgorithmId);

            JcaX509v3CertificateBuilder certificateBuilder = getCertificateBuilder(subjectName, publicKey,
                    issuerX509Certificate);
            X509CertificateHolder certHolder = certificateBuilder.build(contentSigner);
            X509Certificate signedCert = new JcaX509CertificateConverter().getCertificate(certHolder);

            CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509", BOUNCY_CASTLE);
            CertPath certPath = certificateFactory.generateCertPath(Arrays.asList(signedCert, issuerX509Certificate));
            return certPath.getEncoded("PEM");
        });
    }

    @Override
    protected SignMechanism resolveSignMechanism(KeyAlgorithm algorithm) {
        return tokenType.resolveSignMechanismName(algorithm)
                .orElseThrow(() -> new CryptoException("Unsupported key algorithm: " + algorithm));
    }


    // ------------------------------------------------------------------------

    private void findKeysNotInConf() throws Exception {
        log.trace("findKeysNotInConf()");

        try {
            activeSession.executeWithSession(session -> {
                var keysOnToken = findPublicKeys(session, tokenType.getPubKeyAttributes().getAllowedMechanisms());

                for (var keyOnToken : keysOnToken) {
                    String keyId = keyId(keyOnToken);

                    if (keyId == null) {
                        log.debug("Ignoring public key with no ID");

                        continue;
                    }

                    KeyInfo key = tokenManager.getKeyInfo(keyId);

                    if (key == null) {
                        key = tokenManager.addKey(tokenId, keyId, null, resolveSignMechanism(KeyAlgorithm.RSA));
                        tokenManager.setKeyAvailable(keyId, true);

                        log.debug("Found new key with id '{}' on token '{}'", keyId, getWorkerId());
                    }

                    // update the key label
                    char[] label = keyOnToken.getLabel().getCharArrayValue();

                    if (label != null) {
                        tokenManager.setKeyLabel(keyId, new String(label));
                    }

                    if (key.getPublicKey() == null) {
                        updatePublicKey(keyId);
                    }
                }
            });
        } catch (PKCS11Exception e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to find keys from token '{}'", getWorkerId(), e);
        }
    }

    private PrivateKey getPrivateKey(Session session, String keyId) throws Exception {
        PrivateKey privateKey = privateKeys.get(keyId);
        if (privateKey == null) {
            log.debug("Key {} not found in cache, trying to find it from hardware token", keyId);
            privateKey = findPrivateKey(session, keyId, tokenType.getPrivKeyAttributes().getAllowedMechanisms());
            privateKeys.put(keyId, privateKey);
        }
        return privateKey;
    }

    private void findPublicKeysForPrivateKeys() throws Exception {
        log.trace("findPublicKeysForPrivateKeys()");

        for (KeyInfo key : tokenManager.listKeys(tokenId)) {
            if (key.getPublicKey() == null && key.getId() != null) {
                updatePublicKey(key.getId());
            }
        }
    }

    private void findCertificatesNotInConf() throws Exception {
        log.trace("findCertificatesNotInConf()");

        try {
            activeSession.executeWithSession(session -> {
                List<X509PublicKeyCertificate> certsOnModule = findPublicKeyCertificates(session);
                List<KeyInfo> existingKeys = tokenManager.listKeys(tokenId);

                for (X509PublicKeyCertificate certOnModule : certsOnModule) {
                    byte[] certBytes = certOnModule.getValue().getByteArrayValue();

                    for (KeyInfo key : existingKeys) {
                        if (key.getId().equals(keyId(certOnModule)) && !hasCert(key, certBytes)) {
                            log.debug("Found new certificate for key '{}'", key.getId());

                            tokenManager.addCert(key.getId(), certBytes);
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
            activeSession.executeWithSession(session -> {
                String publicKeyBase64;

                var publicKey = findPublicKey(session, keyId, tokenType.getPubKeyAttributes().getAllowedMechanisms());

                switch (publicKey) {
                    case RSAPublicKey rsaPublicKey -> {
                        publicKeyBase64 = EncoderUtils.encodeBase64(KeyPairHelper.of(KeyAlgorithm.RSA).generateX509PublicKey(rsaPublicKey));
                        tokenManager.setPublicKey(keyId, publicKeyBase64);
                    }
                    case ECDSAPublicKey ecPublicKey -> {
                        publicKeyBase64 = EncoderUtils.encodeBase64(KeyPairHelper.of(KeyAlgorithm.EC).generateX509PublicKey(ecPublicKey));
                        tokenManager.setPublicKey(keyId, publicKeyBase64);
                    }
                    case null, default -> {
                        X509PublicKeyCertificate first = certs.get(keyId).getFirst();
                        X509Certificate cert = CryptoUtils.readCertificate(first.getValue().getByteArrayValue());
                        publicKeyBase64 = encodeBase64(cert.getPublicKey().getEncoded());
                    }
                }

                if (publicKeyBase64 != null) {
                    log.debug("Found public key for key '{}'...", keyId);

                    tokenManager.setPublicKey(keyId, publicKeyBase64);
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

        createSession();
        updateTokenInfo();
    }

    private void login() throws Exception {
        char[] password = PasswordStore.getPassword(tokenId);

        if (password == null) {
            log.debug("Cannot login, no password stored");

            return;
        }

        assertActiveSession();

        log.trace("login()");

        try {
            activeSession.executeWithSession(session -> {
                HardwareTokenUtil.login(session, password);

                log.info("User successfully logged in");

                tokenManager.setTokenStatus(tokenId, TokenStatusInfo.OK);
                tokenManager.setTokenActive(tokenId, true);
                loadPrivateKeys(session);
            });
        } catch (PKCS11Exception e) {
            setTokenStatusFromErrorCode(e.getErrorCode());

            throw e;
        }
    }

    private void logout() throws Exception {
        if (activeSession == null) {
            return;
        }

        privateKeys.clear();

        log.trace("logout()");

        try {
            activeSession.executeWithSession(session -> {
                HardwareTokenUtil.logout(session);

                log.info("User successfully logged out");

                tokenManager.setTokenStatus(tokenId, TokenStatusInfo.OK);
            });
        } catch (PKCS11Exception e) {
            setTokenStatusFromErrorCode(e.getErrorCode());

            throw e;
        } finally {
            tokenManager.setTokenActive(tokenId, false);
        }
    }

    private void pinVerificationPerSigningLogin() {
        if (tokenType.isPinVerificationPerSigning()) {
            try {
                login();
            } catch (Exception e) {
                log.warn("Login failed", e);

                throw loginFailed(e.getMessage());
            }
        }
    }

    private void pinVerificationPerSigningLogout() {
        if (tokenType.isPinVerificationPerSigning()) {
            try {
                logout();
            } catch (Exception e) {
                log.error("Logout failed", e);
            }
        }
    }

    private void createSession() throws Exception {
        log.trace("createSession()");
        closeActiveSession();

        if (getToken() != null) {
            activeSession = new PooledSession(getToken(), tokenId,
                    signerProperties.addon().hwTokenPoolSize(),
                    signerProperties.addon().hwTokenSessionAcquireTimeoutMillis());
        }
    }

    private void loadPrivateKeys(Session session) throws Exception {
        if (activeSession == null) {
            return;
        }

        privateKeys.clear();

        List<PrivateKey> keysOnToken = findPrivateKeys(session, tokenType.getPrivKeyAttributes().getAllowedMechanisms());

        log.trace("Found {} private key(s) on token '{}'", keysOnToken.size(), getWorkerId());

        for (var keyOnToken : keysOnToken) {
            String keyId = keyId(keyOnToken);

            if (keyId == null) {
                log.debug("Ignoring private key with no ID");

                continue;
            }

            privateKeys.put(keyId, keyOnToken);

            log.trace("Private key '{}' added to token '{}'", keyId, getWorkerId());

            if (isKeyMissing(keyId)) {
                tokenManager.addKey(tokenId, keyId, null, resolveSignMechanism(KeyAlgorithm.RSA));
            } else {
                log.debug("Private key ({}) found in token '{}'", keyId, getWorkerId());
            }

            tokenManager.setKeyAvailable(keyId, true);
        }

        for (KeyInfo keyInfo : tokenManager.listKeys(tokenId)) {
            String keyId = keyInfo.getId();

            if (!privateKeys.containsKey(keyId)) {
                tokenManager.setKeyAvailable(keyId, false);

                log.debug("Private key ({}) not found in token '{}'", keyId, getWorkerId());
            }
        }

        if (privateKeys.isEmpty()) {
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

    private void closeActiveSession() throws Exception {
        if (activeSession != null) {
            try {
                logout();
            } finally {
                activeSession.destroy();
                activeSession = null;
            }
        }
    }

    private Token getToken() {
        return ((HardwareTokenType) tokenType).getToken();
    }

    private void setTokenStatusFromErrorCode(long errorCode) throws Exception {
        TokenStatusInfo status = getTokenStatus(getToken().getTokenInfo(), errorCode);

        if (status != null) {
            tokenManager.setTokenStatus(tokenId, status);
        }
    }

    private void assertActiveSession() {
        if (activeSession == null) {
            throw new CodedException(X_INTERNAL_ERROR, "No active session on token %s", tokenId);
        }
    }

    private void assertTokenWritable() {
        if (tokenType.isReadOnly()) {
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

    private void destroyCert(Session session, X509PublicKeyCertificate cert) {
        try {
            session.destroyObject(cert);
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

    private class HardwareTokenContentSigner implements ContentSigner {

        private final ByteArrayOutputStream out;
        private final Session session;
        private final String keyId;
        private final SignAlgorithm signatureAlgorithmId;

        HardwareTokenContentSigner(Session session, String keyId, SignAlgorithm signatureAlgorithmId) {
            this.session = session;
            this.keyId = keyId;
            this.signatureAlgorithmId = signatureAlgorithmId;
            out = new ByteArrayOutputStream();
        }

        @Override
        public byte[] getSignature() {
            try {
                assertActiveSession();
                pinVerificationPerSigningLogin();
                byte[] dataToSign = out.toByteArray();
                PrivateKey privateKey = getPrivateKey(session, keyId);
                if (privateKey == null) {
                    throw CodedException.tr(X_KEY_NOT_FOUND, "key_not_found_on_token", "Key '%s' not found on token '%s'",
                            keyId, tokenId);
                }
                log.debug("Signing with key '{}' and signature algorithm '{}'", keyId, signatureAlgorithmId);
                Mechanism signatureMechanism = verifyAndReturnSignMechanism(signatureAlgorithmId,
                        KeyAlgorithm.valueOf(privateKey.getKeyType().toString()));
                session.signInit(signatureMechanism, privateKey);
                byte[] digest = calculateDigest(signatureAlgorithmId.digest(), dataToSign);
                byte[] dataDigestToSign = SignDataPreparer.of(signatureAlgorithmId).prepare(digest);
                return session.sign(dataDigestToSign);
            } catch (Exception e) {
                log.error(e.getMessage());
                throw translateException(e);
            } finally {
                pinVerificationPerSigningLogout();
            }
        }

        @Override
        public OutputStream getOutputStream() {
            return out;
        }

        @Override
        public AlgorithmIdentifier getAlgorithmIdentifier() {
            return new DefaultSignatureAlgorithmIdentifierFinder().find(signatureAlgorithmId.name());
        }
    }
}
