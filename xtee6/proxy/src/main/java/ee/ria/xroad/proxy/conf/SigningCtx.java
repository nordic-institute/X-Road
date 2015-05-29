package ee.ria.xroad.proxy.conf;

import ee.ria.xroad.common.signature.SignatureBuilder;
import ee.ria.xroad.common.signature.SignatureData;

/**
 * Encapsulates security-related parameters of a given member,
 * such as currently used signing key and cert.
 */
public interface SigningCtx {
    /**
     * Creates and signs the signature and returns the signature XML as string.
     * @param builder the signature builder instance
     * @return the signature data
     * @throws Exception in case of any errors
     */
    SignatureData buildSignature(SignatureBuilder builder) throws Exception;
}
