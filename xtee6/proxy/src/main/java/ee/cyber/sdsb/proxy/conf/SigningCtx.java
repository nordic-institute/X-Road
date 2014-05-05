package ee.cyber.sdsb.proxy.conf;

import ee.cyber.sdsb.common.signature.SignatureBuilder;
import ee.cyber.sdsb.common.signature.SignatureData;

/**
 * Encapsulates security-related parameters of a given member,
 * such as currently used signing key and cert.
 */
public interface SigningCtx {
    /**
     * Creates and signs the signature and returns the signature XML as string.
     * @param builder the signature builder instance
     */
    SignatureData buildSignature(SignatureBuilder builder) throws Exception;
}
