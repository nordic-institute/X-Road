/*
 * The MIT License
 *
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

package org.niis.xroad.edc.sig.xades;

import ee.ria.xroad.common.identifier.ClientId;

import eu.europa.esig.dss.diagnostic.DiagnosticData;
import eu.europa.esig.dss.diagnostic.SignatureWrapper;
import eu.europa.esig.dss.diagnostic.TimestampWrapper;
import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.DigestDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.validation.SignedDocumentValidator;
import eu.europa.esig.dss.validation.reports.Reports;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.niis.xroad.edc.sig.XrdSignatureVerificationException;
import org.niis.xroad.edc.sig.XrdSignatureVerifier;
import org.niis.xroad.edc.sig.XrdSignatureVerifierBase;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;


@RequiredArgsConstructor
@Slf4j
public class XrdXAdESVerifier extends XrdSignatureVerifierBase implements XrdSignatureVerifier {

    private final DigestAlgorithm digestAlgorithm;

    @Override
    public void verifySignature(String signatureBase64, byte[] message,
                                byte[] attachment, ClientId signerClientId)
            throws XrdSignatureVerificationException {
        try {
            byte[] decoded = Base64.getDecoder().decode(signatureBase64);
            InMemoryDocument signatureDocument = new InMemoryDocument(decoded);

            List<DSSDocument> detachedPayloads = new ArrayList<>();
            Optional.ofNullable(message).ifPresent(m -> detachedPayloads.add(new InMemoryDocument(m, "/message.xml")));
            Optional.ofNullable(attachment).ifPresent(a -> detachedPayloads.add(new InMemoryDocument(attachment, "/attachment1")));

            validate(signatureDocument, detachedPayloads, signerClientId);
        } catch (Exception e) {
            throw new XrdSignatureVerificationException("Verification has failed", e);
        }
    }

    @Override
    public void verifySignature(String signatureBase64, byte[] message, String attachmentDigest, ClientId signerClientId)
            throws XrdSignatureVerificationException {

        try {
            byte[] decoded = Base64.getDecoder().decode(signatureBase64);
            InMemoryDocument signatureDocument = new InMemoryDocument(decoded);

            List<DSSDocument> detachedPayloads = new ArrayList<>();
            Optional.ofNullable(message).ifPresent(m -> detachedPayloads.add(new InMemoryDocument(m, "/message.xml")));
            Optional.ofNullable(attachmentDigest)
                    .ifPresent(a -> detachedPayloads.add(new DigestDocument(digestAlgorithm, attachmentDigest, "/attachment1")));

            validate(signatureDocument, detachedPayloads, signerClientId);
        } catch (Exception e) {
            throw new XrdSignatureVerificationException("Verification has failed", e);
        }
    }

    private void validate(InMemoryDocument signatureDocument, List<DSSDocument> detachedPayloads, ClientId signerClientId)
            throws Exception {
        SignedDocumentValidator validator = getValidator(signatureDocument);

        validator.setDetachedContents(detachedPayloads);

        Reports reports = validator.validateDocument();
        DiagnosticData diagnosticData = reports.getDiagnosticData();

        var cert = validator.getSignatures().get(0).getCertificates().get(0);
        var ocspResp = new OCSPResp(validator.getSignatures().get(0).getOCSPSource().getRevocationValuesBinaries().get(0).getBinaries());
        validateXroad(cert.getCertificate(), signerClientId, ocspResp);

        List<SignatureWrapper> signatures = diagnosticData.getSignatures();
        for (SignatureWrapper signatureWrapper : signatures) {
            assertTrue(signatureWrapper.isSignatureValid());

            List<TimestampWrapper> timestampList = signatureWrapper.getTimestampList();
            for (TimestampWrapper timestampWrapper : timestampList) {
                assertTrue(timestampWrapper.isMessageImprintDataFound());
                assertTrue(timestampWrapper.isMessageImprintDataIntact());
                assertTrue(timestampWrapper.isSignatureValid());
            }
        }
        log.info("DSS checks: signature is valid.");
    }

}
