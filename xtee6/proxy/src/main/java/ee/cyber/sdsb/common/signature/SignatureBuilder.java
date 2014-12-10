package ee.cyber.sdsb.common.signature;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;

import org.bouncycastle.cert.ocsp.OCSPResp;

import ee.cyber.sdsb.proxy.signedmessage.SigningKey;

/**
 * Collects all the parts to be signed and creates the signature.
 */
@Slf4j
public class SignatureBuilder {

    private final List<MessagePart> parts = new ArrayList<>();

    private final List<X509Certificate> extraCertificates = new ArrayList<>();
    private final List<OCSPResp> ocspResponses = new ArrayList<>();

    private X509Certificate signingCert;

    /** Adds a hash to be signed. */
    public void addPart(MessagePart part) {
        this.parts.add(part);
    }

    /** Adds hashes to be signed. */
    public void addParts(List<MessagePart> parts) {
        this.parts.addAll(parts);
    }

    /** Sets the signing certificate and its corresponding OCSP response. */
    public void setSigningCert(X509Certificate signingCert, OCSPResp ocsp) {
        this.signingCert = signingCert;
        this.ocspResponses.add(ocsp);
    }

    /** Sets the signing certificate. */
    public void setSigningCert(X509Certificate signingCert) {
        this.signingCert = signingCert;
    }

    /** Adds extra certificates. */
    public void addExtraCertificates(List<X509Certificate> certificates) {
        this.extraCertificates.addAll(certificates);
    }

    /** Adds extra OCSP responses. */
    public void addOcspResponses(List<OCSPResp> ocspResponses) {
        this.ocspResponses.addAll(ocspResponses);
    }

    public SignatureData build(SigningKey signingKey,
            String signatureAlgorithmId) throws Exception {
        log.trace("Sign, {} part(s)", parts.size());

        SigningRequest request = new SigningRequest(signingCert, parts);
        request.getExtraCertificates().addAll(extraCertificates);
        request.getOcspResponses().addAll(ocspResponses);

        return signingKey.calculateSignature(request, signatureAlgorithmId);
    }

}
