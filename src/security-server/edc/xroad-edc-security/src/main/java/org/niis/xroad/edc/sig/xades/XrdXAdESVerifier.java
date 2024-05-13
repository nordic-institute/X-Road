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
import eu.europa.esig.dss.enumerations.RevocationType;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.validation.SignedDocumentValidator;
import eu.europa.esig.dss.validation.reports.Reports;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.edc.sig.XrdSignatureVerificationException;
import org.niis.xroad.edc.sig.XrdSignatureVerifier;
import org.niis.xroad.edc.sig.XrdSignatureVerifierBase;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;


@Slf4j
public class XrdXAdESVerifier extends XrdSignatureVerifierBase implements XrdSignatureVerifier {

    @Override
    public void verifySignature(String signature, byte[] detachedPayload, Map<String, String> detachedHeaders, ClientId signerClientId)
            throws XrdSignatureVerificationException {
        try {
            byte[] decoded = Base64.getDecoder().decode(signature);
            InMemoryDocument signatureDocument = new InMemoryDocument(decoded);
            validateSignature(signatureDocument, detachedPayload, detachedHeaders, signerClientId);
        } catch (Exception e) {
            throw new XrdSignatureVerificationException("Verification has failed", e);
        }
    }

    @Override
    public void verifySignature(String signatureBase64, Supplier<byte[]> messageSupplier, Supplier<byte[]> attachmentSupplier, ClientId signerClientId)
            throws XrdSignatureVerificationException {

        try {
            byte[] decoded = Base64.getDecoder().decode(signatureBase64);
            InMemoryDocument signatureDocument = new InMemoryDocument(decoded);
            validateSignature(signatureDocument, messageSupplier, attachmentSupplier, signerClientId);
        } catch (Exception e) {
            throw new XrdSignatureVerificationException("Verification has failed", e);
        }
    }

    private void validateSignature(InMemoryDocument signatureDocument, Supplier<byte[]> messageSupplier, Supplier<byte[]> attachmentSupplier, ClientId signerClientId) {

        List<DSSDocument> detachedPayloads = new ArrayList<>();

        byte[] messagePart = messageSupplier.get();
        if (messagePart != null) {
            detachedPayloads.add(new InMemoryDocument(messagePart, "/message.xml"));
        }

        byte[] attachmentPart = attachmentSupplier.get();
        if (attachmentPart != null) {
            detachedPayloads.add(new InMemoryDocument(attachmentPart, "/attachment1"));
        }

        validate(signatureDocument, detachedPayloads, signerClientId);
    }


    private void validateSignature(InMemoryDocument signatureDocument, byte[] detachedPayload, Map<String, String> detachedHeaders,
                                   ClientId signerClientId) throws Exception {

        List<DSSDocument> detachedPayloads = new ArrayList<>();
        if (detachedHeaders != null && !detachedHeaders.isEmpty()) {
//            detachedPayloads.add(new InMemoryDocument(XrdXAdESUtils.serializeHeaders(detachedHeaders).getBytes(), DOCUMENT_NAME_HEADERS));
        }
//        detachedPayloads.add(new InMemoryDocument(detachedPayload, DOCUMENT_NAME_PAYLOAD));
        detachedPayloads.add(new InMemoryDocument(detachedPayload, "/message.xml"));

        validate(signatureDocument, detachedPayloads, signerClientId);
    }

    private void validate(InMemoryDocument signatureDocument, List<DSSDocument> detachedPayloads, ClientId signerClientId) {
        SignedDocumentValidator validator = getValidator(signatureDocument);

        validator.setDetachedContents(detachedPayloads);

        Reports reports = validator.validateDocument();
        DiagnosticData diagnosticData = reports.getDiagnosticData();

        var cert = validator.getSignatures().get(0).getCertificates().get(0);
        // todo:
        //validateXroad(cert.getCertificate(), signerClientId, new OCSPResp(new byte[0]));

        List<SignatureWrapper> signatures = diagnosticData.getSignatures();
        for (SignatureWrapper signatureWrapper : signatures) {
            assertTrue(signatureWrapper.isSignatureValid());
            signatureWrapper.foundRevocations().getRelatedRevocationsByType(RevocationType.OCSP); //todo:

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
