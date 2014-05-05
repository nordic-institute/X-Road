package ee.cyber.sdsb.common.conf;

import java.security.cert.X509Certificate;

import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.signature.SignatureVerifier;

/**
 * Encapsulates security-related info applicable to whole proxy (such as
 * global CA cert).
 * It reads data from configuration
 */
public interface VerificationCtx {
    /**
     * Verifies SSL certificate from another proxy.
     * TODO: correct return value.
     */
    void verifySslCert(X509Certificate cert);

    /**
     * Reads name of the member from the certificate.
     */
    String getOrganization(X509Certificate cert);

    /**
     * Verifies a signature.
     */
    void verifySignature(ClientId sender, SignatureVerifier verifier)
            throws Exception;
}
