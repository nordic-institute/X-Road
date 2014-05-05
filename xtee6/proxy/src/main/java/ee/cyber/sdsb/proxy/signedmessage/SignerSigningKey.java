package ee.cyber.sdsb.proxy.signedmessage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.signature.BatchSigningWorker;
import ee.cyber.sdsb.common.signature.SignatureData;
import ee.cyber.sdsb.common.signature.SigningRequest;

import static ee.cyber.sdsb.common.ErrorCodes.X_CANNOT_CREATE_SIGNATURE;
import static ee.cyber.sdsb.common.ErrorCodes.translateWithPrefix;

/**
 * Signing key that is located in SSCD (secure signature creation device).
 */
public class SignerSigningKey implements SigningKey {

    private static final Logger LOG =
            LoggerFactory.getLogger(SignerSigningKey.class);

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
        LOG.debug("Calculating signature using algorithm {}", algorithmId);
        try {
            return BatchSigningWorker.sign(keyId, algorithmId, request);
        } catch (Exception e) {
            throw translateWithPrefix(X_CANNOT_CREATE_SIGNATURE, e);
        }
    }
}
