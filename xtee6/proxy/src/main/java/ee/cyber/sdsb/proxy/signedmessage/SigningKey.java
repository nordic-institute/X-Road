package ee.cyber.sdsb.proxy.signedmessage;

import ee.cyber.sdsb.common.signature.SignatureData;
import ee.cyber.sdsb.common.signature.SigningRequest;

/**
 * API for implementing signing key.
 */
public interface SigningKey {

    /**
     * Calculates signature.
     */
    SignatureData calculateSignature(SigningRequest request,
            String signatureAlgorithmId) throws Exception;
}
