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

import ee.ria.xroad.signer.SignerProxy;

import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.spi.DSSUtils;
import eu.europa.esig.dss.spi.x509.CommonTrustedCertificateSource;
import eu.europa.esig.dss.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.xades.XAdESSignatureParameters;
import eu.europa.esig.dss.xades.signature.XAdESService;
import eu.europa.esig.dss.xml.common.definition.DSSNamespace;
import eu.europa.esig.xades.definition.XAdESNamespace;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.edc.sig.PocConstants;
import org.niis.xroad.edc.sig.XrdDssSigner;
import org.niis.xroad.edc.sig.XrdSignatureCreationException;
import org.niis.xroad.edc.sig.XrdSignatureCreator;
import org.niis.xroad.edc.sig.jades.DssOCSPSource;
import org.niis.xroad.edc.sig.jades.DssTspSource;

import javax.xml.crypto.dsig.XMLSignature;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static ee.ria.xroad.common.util.CryptoUtils.readCertificate;
import static eu.europa.esig.dss.enumerations.SignaturePackaging.DETACHED;
import static org.niis.xroad.edc.sig.xades.XrdXAdESUtils.DOCUMENT_NAME_HEADERS;
import static org.niis.xroad.edc.sig.xades.XrdXAdESUtils.DOCUMENT_NAME_PAYLOAD;

@RequiredArgsConstructor
public class XrdXAdESSignatureCreator implements XrdSignatureCreator {

    private final XrdDssSigner signer = new XrdDssSigner();
    private final SignatureLevel signatureLevel;

    @Override
    public String sign(SignerProxy.MemberSigningInfoDto signingInfo, byte[] messageBody, Map<String, String> messageHeaders)
            throws XrdSignatureCreationException {
        List<DSSDocument> documentsToSign = new ArrayList<>();
        if (messageHeaders != null && !messageHeaders.isEmpty()) {
            documentsToSign.add(new InMemoryDocument(XrdXAdESUtils.serializeHeaders(messageHeaders), DOCUMENT_NAME_HEADERS));
        }
        documentsToSign.add(new InMemoryDocument(messageBody, DOCUMENT_NAME_PAYLOAD));

        XAdESSignatureParameters parameters = new XAdESSignatureParameters();
        parameters.setXadesNamespace(XAdESNamespace.XADES_132);
        parameters.setXmldsigNamespace(new DSSNamespace(XMLSignature.XMLNS, "ds"));
        parameters.setSignatureLevel(signatureLevel);
        parameters.setSignaturePackaging(DETACHED);
        parameters.setDigestAlgorithm(DigestAlgorithm.SHA512);

        X509Certificate cert = readCertificate(signingInfo.getCert().getCertificateBytes());
        var dssCert = new CertificateToken(cert);

        parameters.setSigningCertificate(dssCert);
        parameters.setCertificateChain(dssCert, DSSUtils.loadCertificateFromBase64EncodedString(PocConstants.TEST_CA_CERT));

        XAdESService service = getXAdESService(signingInfo);
        ToBeSigned toBeSigned = service.getDataToSign(documentsToSign, parameters);
        SignatureValue signatureValue = signer.sign(signingInfo.getKeyId(), parameters.getDigestAlgorithm(), toBeSigned);

        DSSDocument signedDocument = service.signDocument(documentsToSign, parameters, signatureValue);

        //zipping might save up to 50% of the size
        return Base64.getEncoder().encodeToString(DSSUtils.toByteArray(signedDocument));
    }

    private XAdESService getXAdESService(SignerProxy.MemberSigningInfoDto memberSigningInfoDto) {
        var commonCertificateVerifier = new CommonCertificateVerifier();
        var service = new XAdESService(commonCertificateVerifier);

        var trustedCertSource = new CommonTrustedCertificateSource();
        commonCertificateVerifier.setOcspSource(new DssOCSPSource(memberSigningInfoDto.getCert()));
        commonCertificateVerifier.addTrustedCertSources(trustedCertSource);

        trustedCertSource.addCertificate(DSSUtils.loadCertificateFromBase64EncodedString(PocConstants.TEST_CA_CERT));
        trustedCertSource.addCertificate(DSSUtils.loadCertificateFromBase64EncodedString(PocConstants.TEST_OCSP_CERT));

        service.setTspSource(new DssTspSource());

        return service;
    }

}
