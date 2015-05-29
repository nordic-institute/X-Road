package ee.ria.xroad.proxy.signedmessage;

import ee.ria.xroad.common.signature.SignatureData;
import ee.ria.xroad.common.signature.SigningRequest;

/**
 * API for implementing signing key.
 */
public interface SigningKey {

    /**
     * Calculates signature.
     * @param request singing request information
     * @param signatureAlgorithmId algorithm to use for signing
     * @throws Exception in case of any errors
     * @return the signature data
     */
    SignatureData calculateSignature(SigningRequest request,
            String signatureAlgorithmId) throws Exception;
}
