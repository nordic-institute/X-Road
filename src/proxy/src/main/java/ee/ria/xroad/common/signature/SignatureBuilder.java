/*
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
package ee.ria.xroad.common.signature;

import ee.ria.xroad.proxy.signedmessage.SigningKey;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.ocsp.OCSPResp;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

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
     * Builds signature data using the given signing key and signature digest algorithm.
     * @param signingKey the signing key
     * @param signatureDigestAlgorithmId ID of the signature digest algorithm
     * @return the signature data
     * @throws Exception in case of any errors
     */
    public SignatureData build(SigningKey signingKey, String signatureDigestAlgorithmId) throws Exception {
        log.trace("Sign, {} part(s)", parts.size());

        SigningRequest request = new SigningRequest(signingCert, parts);
        request.getExtraCertificates().addAll(extraCertificates);
        request.getOcspResponses().addAll(ocspResponses);

        return signingKey.calculateSignature(request, signatureDigestAlgorithmId);
    }

}
