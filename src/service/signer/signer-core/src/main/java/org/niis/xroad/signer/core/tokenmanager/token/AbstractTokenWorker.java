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

import ee.ria.xroad.common.crypto.KeyManagers;
import ee.ria.xroad.common.crypto.identifier.KeyAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignMechanism;

import iaik.pkcs.pkcs11.TokenException;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.OperatorCreationException;
import org.niis.xroad.signer.api.dto.TokenInfo;
import org.niis.xroad.signer.common.SignReqHandler;
import org.niis.xroad.signer.core.tokenmanager.KeyManager;
import org.niis.xroad.signer.core.tokenmanager.TokenLookup;
import org.niis.xroad.signer.core.tokenmanager.TokenManager;
import org.niis.xroad.signer.core.tokenpinstore.TokenPinStoreProvider;
import org.niis.xroad.signer.core.util.SignerUtil;
import org.niis.xroad.signer.proto.ActivateTokenReq;
import org.niis.xroad.signer.proto.Algorithm;
import org.niis.xroad.signer.proto.SignCertificateReq;

import java.io.IOException;
import java.math.BigInteger;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

import static ee.ria.xroad.common.ErrorCodes.X_CANNOT_SIGN;
import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.util.SignerProtoUtils.byteToChar;
import static org.niis.xroad.signer.core.util.ExceptionHelper.keyNotAvailable;

/**
 * Token worker base class.
 */
@Slf4j
public abstract class AbstractTokenWorker extends SignReqHandler implements TokenWorker, WorkerWithLifecycle {
    private final String workerId;

    protected final String tokenId;
    protected final TokenManager tokenManager;
    protected final KeyManager keyManager;
    protected final TokenLookup tokenLookup;
    protected final KeyManagers keyManagers;
    protected final TokenPinStoreProvider tokenPinStoreProvider;

    AbstractTokenWorker(TokenInfo tokenInfo, TokenManager tokenManager, KeyManager keyManager,
                        TokenLookup tokenLookup, KeyManagers keyManagers, TokenPinStoreProvider tokenPinStoreProvider) {
        this.tokenId = tokenInfo.getId();
        this.workerId = SignerUtil.getWorkerId(tokenInfo);

        this.tokenManager = tokenManager;
        this.keyManager = keyManager;
        this.tokenLookup = tokenLookup;
        this.keyManagers = keyManagers;
        this.tokenPinStoreProvider = tokenPinStoreProvider;
    }

    @Override
    public void handleActivateToken(ActivateTokenReq message) {
        try {
            if (!message.getActivate()) {
                tokenPinStoreProvider.clearPin(message.getTokenId());
            } else if (message.hasPin()) {
                tokenPinStoreProvider.addPin(message.getTokenId(), byteToChar(message.getPin().toByteArray()));
            }

            activateToken(message);

            refresh();
        } catch (Exception e) {
            log.error("Failed to activate token '{}': {}", getWorkerId(), e.getMessage());

            tokenManager.setTokenActive(tokenId, false);

            throw translateException(e);
        }
    }

    @Override
    public void handleDeleteKey(String keyId) {
        try {
            deleteKey(keyId);
        } catch (Exception e) {
            log.error("Failed to delete key '{}'", keyId, e);

            throw translateException(e);
        }

        if (!keyManager.removeKey(keyId)) {
            log.warn("Failed to remove (or was not found) key '{}' from configuration", keyId);
        }
    }

    @Override
    public void handleDeleteCert(String certificateId) {
        try {
            deleteCert(certificateId);
        } catch (Exception e) {
            log.error("Failed to delete cert '{}'", certificateId, e);
            throw translateException(e);
        }
    }

    @Override
    public byte[] handleSignCertificate(SignCertificateReq request) {
        try {
            var signatureAlgorithmId = SignAlgorithm.ofName(request.getSignatureAlgorithmId());
            var publicKey = keyManagers.getFor(signatureAlgorithmId).readX509PublicKey(request.getPublicKey().toByteArray());
            return signCertificate(request.getKeyId(), signatureAlgorithmId, request.getSubjectName(), publicKey);
        } catch (Exception e) {
            log.error("Error while signing certificate with key '{}'", request.getKeyId(), e);
            throw translateException(e).withPrefix(X_CANNOT_SIGN);
        }
    }

    @Override
    public void destroy() {
        tokenManager.disableToken(tokenId);
    }

    protected boolean isKeyMissing(String keyId) {
        return tokenLookup.getKeyInfo(keyId) == null;
    }

    protected String getWorkerId() {
        return workerId;
    }

    /**
     * Execute additional code post every token worker action.
     */
    public abstract void onActionHandled();

    // ------------------------------------------------------------------------

    protected abstract void activateToken(ActivateTokenReq message);

    protected abstract void deleteKey(String keyId) throws IOException, TokenException;

    protected abstract void deleteCert(String certId);

    protected abstract byte[] signCertificate(String keyId, SignAlgorithm signatureAlgorithmId, String subjectName,
                                              PublicKey publicKey)
            throws UnrecoverableKeyException, CertificateException, IOException, KeyStoreException,
            NoSuchAlgorithmException, NoSuchProviderException, OperatorCreationException;

    protected abstract SignMechanism resolveSignMechanism(KeyAlgorithm algorithm);

    protected void assertKeyAvailable(String keyId) {
        if (!tokenLookup.isKeyAvailable(keyId)) {
            throw keyNotAvailable(keyId);
        }
    }

    protected KeyAlgorithm mapAlgorithm(Algorithm algorithm) {
        return switch (algorithm) {
            case RSA, ALGORITHM_UNKNOWN, UNRECOGNIZED -> KeyAlgorithm.RSA;
            case EC -> KeyAlgorithm.EC;
        };
    }

    protected static JcaX509v3CertificateBuilder getCertificateBuilder(String subjectName,
                                                                       PublicKey publicKey,
                                                                       X509Certificate issuerX509Certificate)
            throws CertIOException {
        JcaX509v3CertificateBuilder certificateBuilder =
                new JcaX509v3CertificateBuilder(
                        new X500Name(issuerX509Certificate.getSubjectX500Principal().getName()),
                        BigInteger.ONE,
                        issuerX509Certificate.getNotBefore(),
                        issuerX509Certificate.getNotAfter(),
                        new X500Name(subjectName),
                        publicKey
                );
        certificateBuilder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature));
        certificateBuilder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
        return certificateBuilder;
    }


}
