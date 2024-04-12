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
package org.niis.xroad.edc.sig.jades;

import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.signer.SignerProxy;

import com.nimbusds.jose.JOSEException;
import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.enumerations.JWSSerializationType;
import eu.europa.esig.dss.enumerations.SigDMechanism;
import eu.europa.esig.dss.enumerations.SignatureAlgorithm;
import eu.europa.esig.dss.enumerations.SignatureLevel;
import eu.europa.esig.dss.jades.HTTPHeader;
import eu.europa.esig.dss.jades.HTTPHeaderDigest;
import eu.europa.esig.dss.jades.JAdESSignatureParameters;
import eu.europa.esig.dss.jades.signature.JAdESService;
import eu.europa.esig.dss.model.DSSDocument;
import eu.europa.esig.dss.model.InMemoryDocument;
import eu.europa.esig.dss.model.SignatureValue;
import eu.europa.esig.dss.model.ToBeSigned;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.spi.DSSUtils;
import eu.europa.esig.dss.spi.x509.CommonTrustedCertificateSource;
import eu.europa.esig.dss.validation.CommonCertificateVerifier;
import eu.europa.esig.dss.xml.common.SchemaFactoryBuilder;
import eu.europa.esig.dss.xml.common.XmlDefinerUtils;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.edc.sig.PocConstants;
import org.niis.xroad.edc.sig.XrdSignatureCreationException;
import org.niis.xroad.edc.sig.XrdSignatureCreator;

import javax.xml.XMLConstants;
import javax.xml.validation.SchemaFactory;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static ee.ria.xroad.common.util.CryptoUtils.calculateDigest;
import static ee.ria.xroad.common.util.CryptoUtils.getDigestAlgorithmId;
import static ee.ria.xroad.common.util.CryptoUtils.readCertificate;
import static eu.europa.esig.dss.enumerations.SignatureLevel.JAdES_BASELINE_B;
import static eu.europa.esig.dss.enumerations.SignaturePackaging.DETACHED;
import static eu.europa.esig.dss.enumerations.SignaturePackaging.ENVELOPING;

@RequiredArgsConstructor
public class XrdJAdESSignatureCreator implements XrdSignatureCreator {
    private final XrdJAdESSigner signer = new XrdJAdESSigner();
    private final SignatureLevel signatureLevel;
    private final JWSSerializationType jwsSerializationType;

    static {
        // force usage of internal xerces implementation in DSS. Otherwise, not compatible Apache Xerces will be used in proxy
        // can be removed once Apache Xerces is removed from classpath
        XmlDefinerUtils.getInstance().setSchemaFactoryBuilder(new JaxpSchemaFactoryBuilder());
    }

    @Override
    public String sign(final SignerProxy.MemberSigningInfoDto signingInfo, final byte[] messageBody,
                       final Map<String, String> messageHeaders) throws XrdSignatureCreationException {
        JAdESSignatureParameters parameters = new JAdESSignatureParameters();
        parameters.setSignaturePackaging(signatureLevel == JAdES_BASELINE_B ? DETACHED : ENVELOPING);
        parameters.setSigDMechanism(SigDMechanism.HTTP_HEADERS);
        parameters.setBase64UrlEncodedPayload(false);

        List<DSSDocument> documentsToSign = new ArrayList<>();
        documentsToSign.add(new HTTPHeaderDigest(new InMemoryDocument(messageBody), DigestAlgorithm.SHA1)); //TODO sha1?

        if (messageHeaders != null && !messageHeaders.isEmpty()) {
            messageHeaders.forEach((k, v) -> documentsToSign.add(new HTTPHeader(k, v)));
        }

        X509Certificate cert = readCertificate(signingInfo.getCert().getCertificateBytes());
        var dssCert = new CertificateToken(cert);

        parameters.setSigningCertificate(dssCert);
        parameters.setCertificateChain(dssCert, DSSUtils.loadCertificateFromBase64EncodedString(PocConstants.TEST_CA_CERT));

        parameters.setDigestAlgorithm(DigestAlgorithm.SHA256);
        parameters.setSignatureLevel(signatureLevel);
        parameters.setJwsSerializationType(jwsSerializationType);

        final JAdESService service = getJAdESService(signingInfo);

        ToBeSigned dataToSign = service.getDataToSign(documentsToSign, parameters);

        SignatureValue signatureValue = signer.sign(signingInfo.getKeyId(), parameters.getDigestAlgorithm(), dataToSign);

        DSSDocument signedDocument = service.signDocument(documentsToSign, parameters, signatureValue);

        return new String(DSSUtils.toByteArray(signedDocument));
    }

    /**
     * Create temporary service for a single signing operation. For production use this should be made as a singleton.
     */
    private JAdESService getJAdESService(SignerProxy.MemberSigningInfoDto memberSigningInfoDto) {
        var commonCertificateVerifier = new CommonCertificateVerifier();
        var service = new JAdESService(commonCertificateVerifier);

        var trustedCertSource = new CommonTrustedCertificateSource();
        commonCertificateVerifier.setOcspSource(new DssOCSPSource(memberSigningInfoDto.getCert()));
        commonCertificateVerifier.addTrustedCertSources(trustedCertSource);

        trustedCertSource.addCertificate(DSSUtils.loadCertificateFromBase64EncodedString(PocConstants.TEST_CA_CERT));
        trustedCertSource.addCertificate(DSSUtils.loadCertificateFromBase64EncodedString(PocConstants.TEST_OCSP_CERT));

        service.setTspSource(new DssTspSource());

        return service;
    }

    private static final class XrdJAdESSigner {

        public SignatureValue sign(String keyId, DigestAlgorithm digestAlgorithm, final ToBeSigned toBeSigned)
                throws XrdSignatureCreationException {
            try {
                String signAlgoId = switch (digestAlgorithm.getName()) {
                    case "SHA256" -> CryptoUtils.SHA256WITHRSA_ID;
                    case "SHA384" -> CryptoUtils.SHA384WITHRSA_ID;
                    case "SHA512" -> CryptoUtils.SHA512WITHRSA_ID;
                    default -> throw new JOSEException("Unsupported signing algorithm");
                };

                String digAlgoId = getDigestAlgorithmId(signAlgoId);
                byte[] digest = calculateDigest(digAlgoId, toBeSigned.getBytes());

                byte[] sig = SignerProxy.sign(keyId, signAlgoId, digest);

                return new SignatureValue(SignatureAlgorithm.RSA_SHA256, sig);
            } catch (Exception e) {
                throw new XrdSignatureCreationException("Failed to sign", e);
            }
        }

    }

    static class JaxpSchemaFactoryBuilder extends SchemaFactoryBuilder {
        @Override
        protected SchemaFactory instantiateFactory() {
            return SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI,
                    "com.sun.org.apache.xerces.internal.jaxp.validation.XMLSchemaFactory", null);
        }
    }

}
