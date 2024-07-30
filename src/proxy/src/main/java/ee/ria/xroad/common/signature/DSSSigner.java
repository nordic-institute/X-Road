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

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.signer.SignerProxy;

import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.DigestDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.spi.DSSUtils;
import eu.europa.esig.dss.spi.x509.CommonTrustedCertificateSource;
import eu.europa.esig.dss.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.xades.XAdESSignatureParameters;
import eu.europa.esig.dss.xades.signature.XAdESService;
import eu.europa.esig.dss.xml.common.TransformerFactoryBuilder;
import eu.europa.esig.dss.xml.common.XmlDefinerUtils;
import eu.europa.esig.dss.xml.common.definition.DSSNamespace;
import eu.europa.esig.xades.definition.XAdESNamespace;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.edc.sig.XrdSignatureService;
import org.niis.xroad.edc.sig.xades.XrdXAdESSignatureCreator;

import javax.xml.XMLConstants;

import java.security.cert.CertificateEncodingException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static ee.ria.xroad.common.ErrorCodes.X_CANNOT_CREATE_SIGNATURE;
import static ee.ria.xroad.common.util.CryptoUtils.calculateDigest;
import static ee.ria.xroad.common.util.CryptoUtils.getDigestAlgorithmId;
import static eu.europa.esig.dss.enumerations.SignaturePackaging.DETACHED;

@Slf4j
public class DSSSigner implements MessageSigner {
    private static final DSSNamespace XADES_NAMESPACE = new DSSNamespace(XAdESNamespace.XADES_132.getUri(), "xades");

    static {
        XmlDefinerUtils.getInstance().setTransformerFactoryBuilder(
                TransformerFactoryBuilder.getSecureTransformerBuilder()
                        .removeAttribute(XMLConstants.ACCESS_EXTERNAL_DTD)
                        .removeAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET));

        XmlDefinerUtils.getInstance().setSchemaFactoryBuilder(new XrdSignatureService.JaxpSchemaFactoryBuilder().removeAttribute(XMLConstants.ACCESS_EXTERNAL_DTD)
                .removeAttribute(XMLConstants.ACCESS_EXTERNAL_STYLESHEET));
    }

    @Override
    public SignatureData sign(String keyId, String signatureAlgorithmId, SigningRequest request) throws Exception {
        var params = createParams(request);
        List<DSSDocument> documentsToSign = new ArrayList<>();
        request.getParts()
                .forEach(part -> documentsToSign.add(new DigestDocument(
                        DigestAlgorithm.forJavaName(part.getHashAlgoId()),
                        Base64.getEncoder().encodeToString(part.getData()),
                        part.getName())));

        var signatureValue = signRequest(keyId, signatureAlgorithmId, getDataToSign(params, documentsToSign));
        var sig = new SignatureValue(SignatureAlgorithm.forJAVA(signatureAlgorithmId), signatureValue);

        var verifier = new CommonCertificateVerifier();
        verifier.setAIASource(null);
        verifier.setTrustedCertSources(loadGlobalConfData());

        final XAdESService xAdESService = new XAdESService(verifier);

        DSSDocument signedDocument = xAdESService.signDocument(documentsToSign, params, sig);

        var extendedDoc = extendDocument(signedDocument, request.getOcspResponses().get(0).getEncoded());

        String signature = new String(DSSUtils.toByteArray(extendedDoc));

        return new SignatureData(signature, null, null);
    }

    @WithSpan
    private DSSDocument extendDocument(DSSDocument signedDocument, byte[] ocspResponse) {
        return new XrdXAdESSignatureCreator.OcspExtensionBuilder()
                .addOcspToken(signedDocument, ocspResponse);
    }

    @SneakyThrows
    private CommonTrustedCertificateSource loadGlobalConfData() {
        CommonTrustedCertificateSource trustedCertificateSource = new CommonTrustedCertificateSource();


        GlobalConf.getTspCertificates().forEach(cert -> {
            try {
                var certToken = DSSUtils.loadCertificate(cert.getEncoded());
                trustedCertificateSource.addCertificate(certToken);
                log.debug("Loaded TSP cert: {}", cert.getSubjectDN());
            } catch (CertificateEncodingException e) {
                throw new RuntimeException(e); //TODO
            }

        });
        GlobalConf.getAllCaCerts().forEach(cert -> {
            try {
                var certToken = DSSUtils.loadCertificate(cert.getEncoded());
                trustedCertificateSource.addCertificate(certToken);
                log.debug("Loaded CA cert: {}", cert.getSubjectDN());
            } catch (CertificateEncodingException e) {
                throw new RuntimeException(e); //TODO
            }

        });

        GlobalConf.getOcspResponderCertificates().forEach(cert -> {
            try {
                var certToken = DSSUtils.loadCertificate(cert.getEncoded());
                trustedCertificateSource.addCertificate(certToken);
                log.debug("Loaded OCSP cert: {}", cert.getSubjectDN());
            } catch (CertificateEncodingException e) {
                throw new RuntimeException(e); //TODO
            }
        });


        return trustedCertificateSource;
    }

    private byte[] signRequest(String keyId, String signatureAlgorithmId, byte[] dataToSign) {
        try {
            byte[] digest = calculateDigest(getDigestAlgorithmId(signatureAlgorithmId), dataToSign);
            return SignerProxy.sign(keyId, signatureAlgorithmId, digest);

        } catch (Exception exception) {
            throw new CodedException(X_CANNOT_CREATE_SIGNATURE, exception);
        }
    }

    private XAdESSignatureParameters createParams(SigningRequest request) {
        var parameters = new XAdESSignatureParameters();
        parameters.setSignatureLevel(SignatureLevel.XAdES_BASELINE_B);
        parameters.setSignaturePackaging(DETACHED);
        parameters.setDigestAlgorithm(DigestAlgorithm.forJavaName(CryptoUtils.DEFAULT_DIGEST_ALGORITHM_ID));
        parameters.setXadesNamespace(XADES_NAMESPACE);
        parameters.setSigningCertificate(new CertificateToken(request.getSigningCert()));

        //TODO missing extra certs, see SigantureXmlBuilder
        return parameters;
    }

    private byte[] getDataToSign(XAdESSignatureParameters parameters, List<DSSDocument> documentsToSign) {
        final XAdESService xAdESService = new XAdESService(new CommonCertificateVerifier());
        ToBeSigned toBeSigned = xAdESService.getDataToSign(documentsToSign, parameters);

        return toBeSigned.getBytes();
    }
}
