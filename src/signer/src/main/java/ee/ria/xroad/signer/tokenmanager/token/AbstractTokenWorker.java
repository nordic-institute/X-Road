/**
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
package ee.ria.xroad.signer.tokenmanager.token;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.util.PasswordStore;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;
import ee.ria.xroad.signer.protocol.message.ActivateToken;
import ee.ria.xroad.signer.protocol.message.DeleteCert;
import ee.ria.xroad.signer.protocol.message.DeleteKey;
import ee.ria.xroad.signer.protocol.message.GenerateKey;
import ee.ria.xroad.signer.protocol.message.SignCertificate;
import ee.ria.xroad.signer.protocol.message.SignCertificateResponse;
import ee.ria.xroad.signer.tokenmanager.TokenManager;
import ee.ria.xroad.signer.util.AbstractUpdateableActor;
import ee.ria.xroad.signer.util.CalculateSignature;
import ee.ria.xroad.signer.util.CalculatedSignature;
import ee.ria.xroad.signer.util.SignerUtil;

import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.CertIOException;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;

import java.math.BigInteger;
import java.security.PublicKey;
import java.security.cert.X509Certificate;

import static ee.ria.xroad.common.ErrorCodes.X_CANNOT_SIGN;
import static ee.ria.xroad.common.ErrorCodes.X_FAILED_TO_GENERATE_R_KEY;
import static ee.ria.xroad.signer.tokenmanager.TokenManager.isKeyAvailable;
import static ee.ria.xroad.signer.tokenmanager.TokenManager.setTokenAvailable;
import static ee.ria.xroad.signer.util.ExceptionHelper.keyNotAvailable;

/**
 * Token worker base class.
 */
@Slf4j
public abstract class AbstractTokenWorker extends AbstractUpdateableActor {

    protected final String tokenId;

    private final String workerId;

    AbstractTokenWorker(TokenInfo tokenInfo) {
        this.tokenId = tokenInfo.getId();
        this.workerId = SignerUtil.getWorkerId(tokenInfo);
    }

    protected boolean hasKey(String keyId) {
        return TokenManager.getKeyInfo(keyId) != null;
    }

    protected boolean isPinStored() {
        try {
            return PasswordStore.getPassword(tokenId) != null;
        } catch (Exception e) {
            log.error("Error when checking if token is active", e);

            return false;
        }
    }

    protected String getWorkerId() {
        return workerId;
    }

    protected Exception customizeException(Exception e) {
        return e;
    }

    @Override
    protected void onMessage(Object message) throws Exception {
        log.trace("onMessage()");

        if (message instanceof ActivateToken) {
            handleActivateToken((ActivateToken) message);
        } else if (message instanceof GenerateKey) {
            handleGenerateKey((GenerateKey) message);
        } else if (message instanceof DeleteKey) {
            handleDeleteKey((DeleteKey) message);
        } else if (message instanceof DeleteCert) {
            handleDeleteCert((DeleteCert) message);
        } else if (message instanceof CalculateSignature) {
            handleCalculateSignature((CalculateSignature) message);
        } else if (message instanceof SignCertificate) {
            handleSignCertificate((SignCertificate) message);
        } else {
            unhandled(message);
        }
    }

    @Override
    public void postStop() throws Exception {
        setTokenAvailable(tokenId, false);
    }

    private void handleActivateToken(ActivateToken message) throws Exception {
        try {
            activateToken(message);

            onUpdate();

            sendSuccessResponse();
        } catch (Exception e) {
            log.error("Failed to activate token '{}': {}", getWorkerId(), e.getMessage());

            TokenManager.setTokenActive(tokenId, false);

            throw customizeException(e);
        }
    }

    private void handleGenerateKey(GenerateKey message) {
        GenerateKeyResult result;

        try {
            result = generateKey(message);
        } catch (Exception e) {
            log.error("Failed to generate key", e);

            throw translateError(customizeException(e)).withPrefix(X_FAILED_TO_GENERATE_R_KEY);
        }

        String keyId = result.getKeyId();

        log.debug("Generated new key with id '{}'", keyId);

        if (!hasKey(keyId)) {
            TokenManager.addKey(tokenId, keyId, result.getPublicKeyBase64());
            TokenManager.setKeyAvailable(keyId, true);
            TokenManager.setKeyLabel(keyId, message.getKeyLabel());
            TokenManager.setKeyFriendlyName(keyId, message.getKeyLabel());
        }

        sendResponse(TokenManager.findKeyInfo(keyId));
    }

    private void handleDeleteKey(DeleteKey message) {
        try {
            deleteKey(message.getKeyId());
        } catch (Exception e) {
            log.error("Failed to delete key '{}'", message.getKeyId(), e);

            throw translateError(customizeException(e));
        }

        TokenManager.removeKey(message.getKeyId());

        sendSuccessResponse();
    }

    private void handleDeleteCert(DeleteCert message) {
        try {
            deleteCert(message.getCertId());
        } catch (Exception e) {
            log.error("Failed to delete cert '{}'", message.getCertId(), e);

            throw translateError(customizeException(e));
        }

        sendSuccessResponse();
    }

    private void handleCalculateSignature(CalculateSignature signRequest) {
        try {
            byte data[] = SignerUtil.createDataToSign(signRequest.getDigest(), signRequest.getSignatureAlgorithmId());

            byte[] signature = sign(signRequest.getKeyId(), signRequest.getSignatureAlgorithmId(), data);
            sendResponse(new CalculatedSignature(signRequest, signature, null));
        } catch (Exception e) { // catch-log-rethrow
            log.error("Error while signing with key '{}'", signRequest.getKeyId(), e);

            CodedException tr = translateError(customizeException(e)).withPrefix(X_CANNOT_SIGN);
            sendResponse(new CalculatedSignature(signRequest, null, tr));
        }
    }

    private void handleSignCertificate(SignCertificate message) {
        try {
            byte[] certificate = signCertificate(message.getKeyId(), message.getSignatureAlgorithmId(),
                                                 message.getSubjectName(), message.getPublicKey());
            sendResponse(new SignCertificateResponse(certificate));
        } catch (Exception e) {
            log.error("Error while signing certificate with key '{}'", message.getKeyId(), e);
            throw translateError(customizeException(e)).withPrefix(X_CANNOT_SIGN);
        }
    }

    // ------------------------------------------------------------------------

    protected abstract void activateToken(ActivateToken message) throws Exception;

    protected abstract GenerateKeyResult generateKey(GenerateKey message) throws Exception;

    protected abstract void deleteKey(String keyId) throws Exception;
    protected abstract void deleteCert(String certId) throws Exception;

    protected abstract byte[] sign(String keyId, String signatureAlgorithmId, byte[] data) throws Exception;

    protected abstract byte[] signCertificate(String keyId, String signatureAlgorithmId, String subjectName,
                                              PublicKey publicKey) throws Exception;

    protected void assertKeyAvailable(String keyId) {
        if (!isKeyAvailable(keyId)) {
            throw keyNotAvailable(keyId);
        }
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

    // ------------------------------------------------------------------------

    @Value
    protected static class GenerateKeyResult {
        private final String keyId;
        private final String publicKeyBase64;
    }
}
