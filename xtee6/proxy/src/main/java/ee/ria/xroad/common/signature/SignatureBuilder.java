package ee.ria.xroad.common.signature;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.ocsp.OCSPResp;

import ee.ria.xroad.proxy.signedmessage.SigningKey;

/**
 * Collects all the parts to be signed and creates the signature.
 */
@Slf4j
public class SignatureBuilder {

    private final List<MessagePart> parts = new ArrayList<>();

    private final List<X509Certificate> extraCertificates = new ArrayList<>();
    private final List<OCSPResp> ocspResponses = new ArrayList<>();

    private X509Certificate signingCert;

    /**
     * Adds a hash to be signed.
     * @param part input part to be added to the signature
     */
    public void addPart(MessagePart part) {
        this.parts.add(part);
    }

    /**
     * Adds hashes to be signed.
     * @param partList list of input parts to be added to the signature
     */
    public void addParts(List<MessagePart> partList) {
        this.parts.addAll(partList);
    }

    /**
     * Sets the signing certificate and its corresponding OCSP response.
     * @param cert the signing certificate
     * @param ocsp OCSP response of the certificate
     */
    public void setSigningCert(X509Certificate cert, OCSPResp ocsp) {
        this.signingCert = cert;
        this.ocspResponses.add(ocsp);
    }

    /**
     * Sets the signing certificate.
     * @param cert the signing certificate
     */
    public void setSigningCert(X509Certificate cert) {
        this.signingCert = cert;
    }

    /**
     * Adds extra certificates.
     * @param certificates list of extra certificates to add
     */
    public void addExtraCertificates(List<X509Certificate> certificates) {
        this.extraCertificates.addAll(certificates);
    }

    /**
     * Adds extra OCSP responses.
     * @param extraOcspResponses list of extra OCSP responses to add
     */
    public void addOcspResponses(List<OCSPResp> extraOcspResponses) {
        this.ocspResponses.addAll(extraOcspResponses);
    }

    /**
     * Builds signature data using the given signing key and signature algorithm.
     * @param signingKey the signing key
     * @param signatureAlgorithmId ID of the signature algorithm
     * @return the signature data
     * @throws Exception in case of any errors
     */
    public SignatureData build(SigningKey signingKey,
            String signatureAlgorithmId) throws Exception {
        log.trace("Sign, {} part(s)", parts.size());

        SigningRequest request = new SigningRequest(signingCert, parts);
        request.getExtraCertificates().addAll(extraCertificates);
        request.getOcspResponses().addAll(ocspResponses);

        return signingKey.calculateSignature(request, signatureAlgorithmId);
    }

}
