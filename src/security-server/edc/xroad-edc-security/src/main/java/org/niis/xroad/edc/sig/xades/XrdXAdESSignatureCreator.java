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


import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.signer.SignerProxy;

import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.DigestDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.spi.DSSUtils;
import eu.europa.esig.dss.validation.AdvancedSignature;
import eu.europa.esig.dss.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.xades.XAdESSignatureParameters;
import eu.europa.esig.dss.xades.signature.ExtensionBuilder;
import eu.europa.esig.dss.xades.signature.XAdESService;
import eu.europa.esig.dss.xades.validation.XAdESSignature;
import eu.europa.esig.dss.xades.validation.XMLDocumentValidator;
import eu.europa.esig.dss.xml.common.definition.DSSNamespace;
import eu.europa.esig.dss.xml.utils.DomUtils;
import eu.europa.esig.xades.definition.XAdESNamespace;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.edc.sig.XrdDssSigner;
import org.niis.xroad.edc.sig.XrdSignatureCreationException;
import org.niis.xroad.edc.sig.XrdSignatureCreator;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static ee.ria.xroad.common.util.CryptoUtils.readCertificate;
import static eu.europa.esig.dss.enumerations.SignaturePackaging.DETACHED;

@RequiredArgsConstructor
public class XrdXAdESSignatureCreator implements XrdSignatureCreator {

    private static final DigestAlgorithm DIGEST_ALGORITHM = DigestAlgorithm.forJavaName(CryptoUtils.DEFAULT_DIGEST_ALGORITHM_ID);

    private final XrdDssSigner signer = new XrdDssSigner();
    private final SignatureLevel signatureLevel = SignatureLevel.XAdES_BASELINE_B;

    @Override
    public String sign(SignerProxy.MemberSigningInfoDto signingInfo, byte[] message)
            throws XrdSignatureCreationException {

        List<DSSDocument> documentsToSign = new ArrayList<>();
        Optional.ofNullable(message)
                .ifPresent(messagePart -> documentsToSign.add(new InMemoryDocument(messagePart, "/message.xml")));

        return signDocuments(signingInfo, documentsToSign);
    }

    @Override
    public String sign(SignerProxy.MemberSigningInfoDto signingInfo, byte[] message, String attachmentDigest)
            throws XrdSignatureCreationException {
        List<DSSDocument> documentsToSign = new ArrayList<>();
        Optional.ofNullable(message)
                .ifPresent(messagePart -> documentsToSign.add(new InMemoryDocument(messagePart, "/message.xml")));

        Optional.ofNullable(attachmentDigest)
                .ifPresent(attachmentPart -> documentsToSign.add(new DigestDocument(DIGEST_ALGORITHM, attachmentPart, "/attachment1")));
        return signDocuments(signingInfo, documentsToSign);
    }

    public String signDocuments(SignerProxy.MemberSigningInfoDto signingInfo, List<DSSDocument> documentsToSign)
            throws XrdSignatureCreationException {
        XAdESSignatureParameters parameters = new XAdESSignatureParameters();
        parameters.setSignatureLevel(signatureLevel);
        parameters.setSignaturePackaging(DETACHED);
        parameters.setDigestAlgorithm(DIGEST_ALGORITHM);
        parameters.setXadesNamespace(new DSSNamespace(XAdESNamespace.XADES_132.getUri(), "xades"));

        X509Certificate cert = readCertificate(signingInfo.getCert().getCertificateBytes());
        parameters.setSigningCertificate(new CertificateToken(cert));

        XAdESService service = new XAdESService(new CommonCertificateVerifier());
        ToBeSigned toBeSigned = service.getDataToSign(documentsToSign, parameters);
        SignatureValue signatureValue = signer.sign(signingInfo.getKeyId(), parameters.getDigestAlgorithm(), toBeSigned);

        DSSDocument signedDocument = service.signDocument(documentsToSign, parameters, signatureValue);

        var extendedDoc = new OcspExtensionBuilder().addOcspToken(signedDocument, signingInfo.getCert().getOcspBytes());

        //zipping might save up to 50% of the size
        return Base64.getEncoder().encodeToString(DSSUtils.toByteArray(extendedDoc));
    }

    public static class OcspExtensionBuilder extends ExtensionBuilder {
        public DSSDocument addOcspToken(DSSDocument document, byte[] ocspBytes) {
            params = new XAdESSignatureParameters();
            documentValidator = new XMLDocumentValidator(document);

            documentDom = documentValidator.getRootElement();

            final String base64EncodedOCSP = Base64.getEncoder().encodeToString(ocspBytes);

            for (AdvancedSignature signature : documentValidator.getSignatures()) {
                initializeSignatureBuilder((XAdESSignature) signature);
                ensureUnsignedProperties();
                ensureUnsignedSignatureProperties();

                var revocationValuesDom = DomUtils.addElement(documentDom, unsignedSignaturePropertiesDom,
                        getXadesNamespace(), getCurrentXAdESElements().getElementRevocationValues());
                var ocspValuesDom = DomUtils.addElement(documentDom, revocationValuesDom,
                        getXadesNamespace(), getCurrentXAdESElements().getElementOCSPValues());
                DomUtils.addTextElement(documentDom, ocspValuesDom, getXadesNamespace(),
                        getCurrentXAdESElements().getElementEncapsulatedOCSPValue(), base64EncodedOCSP);
            }

            return createXmlDocument();
        }
    }

}
