package ee.cyber.sdsb.proxy.signedmessage;

import lombok.extern.slf4j.Slf4j;

import ee.cyber.sdsb.common.signature.BatchSigner;
import ee.cyber.sdsb.common.signature.SignatureData;
import ee.cyber.sdsb.common.signature.SigningRequest;

import static ee.cyber.sdsb.common.ErrorCodes.X_CANNOT_CREATE_SIGNATURE;
import static ee.cyber.sdsb.common.ErrorCodes.translateWithPrefix;

/**
 * Signing key that is located in SSCD (secure signature creation device).
 */
@Slf4j
public class SignerSigningKey implements SigningKey {

    /** The private key ID. */
    private final String keyId;

    /**
     * Creates a new SignerSigningKey with provided keyId.
     * @param keyId the private key ID.
     */
    public SignerSigningKey(String keyId) {
        if (keyId == null) {
            throw new IllegalArgumentException("KeyId is must not be null");
        }

        this.keyId = keyId;
    }

    @Override
    public SignatureData calculateSignature(SigningRequest request,
            String algorithmId) throws Exception {
        log.trace("Calculating signature using algorithm {}", algorithmId);
        try {
            return BatchSigner.sign(keyId, algorithmId, request);
        } catch (Exception e) {
            throw translateWithPrefix(X_CANNOT_CREATE_SIGNATURE, e);
        }
    }
}
