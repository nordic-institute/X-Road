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
package org.niis.xroad.edc.sig;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.signer.SignerProxy;

import eu.europa.esig.dss.enumerations.JWSSerializationType;
import eu.europa.esig.dss.xml.common.SchemaFactoryBuilder;
import eu.europa.esig.dss.xml.common.XmlDefinerUtils;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.util.encoders.Base64;
import org.niis.xroad.edc.sig.jades.XrdJAdESSignatureCreator;
import org.niis.xroad.edc.sig.jades.XrdJAdESVerifier;
import org.niis.xroad.edc.sig.jws.XrdJWSSignatureCreator;
import org.niis.xroad.edc.sig.jws.XrdJwsVerifier;
import org.niis.xroad.edc.sig.xades.XrdXAdESSignatureCreator;
import org.niis.xroad.edc.sig.xades.XrdXAdESVerifier;

import javax.xml.XMLConstants;
import javax.xml.validation.SchemaFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static eu.europa.esig.dss.enumerations.SignatureLevel.JAdES_BASELINE_B;
import static eu.europa.esig.dss.enumerations.SignatureLevel.JAdES_BASELINE_LT;
import static eu.europa.esig.dss.enumerations.SignatureLevel.XAdES_BASELINE_B;
import static org.niis.xroad.edc.sig.PocConstants.HEADER_XRD_SIG;
import static org.niis.xroad.edc.sig.PocConstants.HEADER_XRD_SIG_OCSP;

@Slf4j
public class XrdSignatureService {
    private static final XrdSignatureMode MODE = XrdSignatureMode.XADES_B;

    //TODO these headers are disabled as they can be modified by jetty server or http-client.
    private static final Set<String> IGNORED_HEADERS = Set.of(HEADER_XRD_SIG,
            "Accept-Encoding",
            "Date",
            "Content-Type",
            "Content-Length");

    static {
        // force usage of internal xerces implementation in DSS. Otherwise, not compatible Apache Xerces will be used in proxy
        // can be removed once Apache Xerces is removed from classpath
        XmlDefinerUtils.getInstance().setSchemaFactoryBuilder(new JaxpSchemaFactoryBuilder());
    }

    public Map<String, String> sign(String assetId, byte[] messageBody, Map<String, String> messageHeaders)
            throws XrdSignatureCreationException {
        var serviceId = ServiceId.Conf.fromEncodedId(assetId);

        return sign(serviceId.getClientId(), messageBody, messageHeaders);
    }

    public Map<String, String> sign(ClientId signingClientId, byte[] messageBody, Map<String, String> messageHeaders)
            throws XrdSignatureCreationException {

        var signingInfo = getMemberSigningInfo(signingClientId);
        var signer = switch (MODE) {
            case JWS -> new XrdJWSSignatureCreator();
            case JADES_B -> new XrdJAdESSignatureCreator(JAdES_BASELINE_B, JWSSerializationType.COMPACT_SERIALIZATION);
            case JADES_B_LT -> new XrdJAdESSignatureCreator(JAdES_BASELINE_LT, JWSSerializationType.FLATTENED_JSON_SERIALIZATION);
            case XADES_B -> new XrdXAdESSignatureCreator(XAdES_BASELINE_B);
        };

        Map<String, String> headersToSign = new HashMap<>();
        headersToSign.put(HEADER_XRD_SIG_OCSP, Base64.toBase64String(signingInfo.getCert().getOcspBytes()));
        for (Map.Entry<String, String> entry : messageHeaders.entrySet()) {
            if (!IGNORED_HEADERS.contains(entry.getKey()))
                headersToSign.put(entry.getKey(), entry.getValue());
        }

        headersToSign.forEach((key, value) -> log.info("Will sign header: {}={}", key, value));
        var signature = signer.sign(signingInfo, messageBody, headersToSign);

        Map<String, String> signatureHeaders = new HashMap<>();
        signatureHeaders.put(HEADER_XRD_SIG, signature);
        signatureHeaders.put(HEADER_XRD_SIG_OCSP, Base64.toBase64String(signingInfo.getCert().getOcspBytes()));
        return signatureHeaders;
    }

    public void verify(Map<String, String> headers, byte[] detachedPayload, ClientId signerClientId)
            throws XrdSignatureVerificationException {

        var verifier = switch (MODE) {
            case JWS -> new XrdJwsVerifier();
            case JADES_B, JADES_B_LT -> new XrdJAdESVerifier();
            case XADES_B -> new XrdXAdESVerifier();
        };

        var signature = headers.get(HEADER_XRD_SIG); // currently only header is supported for signature.
        var filteredHeaders = headers.entrySet().stream()
                .filter(entry -> !IGNORED_HEADERS.contains(entry.getKey()))
                .peek(entry -> log.info("Will verify header: {}={}", entry.getKey(), entry.getValue()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        verifier.verifySignature(signature, detachedPayload, filteredHeaders, signerClientId);
    }

    private SignerProxy.MemberSigningInfoDto getMemberSigningInfo(ClientId clientId) throws XrdSignatureCreationException {
        try {
            return SignerProxy.getMemberSigningInfo(clientId);
        } catch (Exception e) {
            throw new XrdSignatureCreationException("Failed to get member sign cert info", e);
        }
    }

    @Getter
    public enum XrdSignatureMode {
        JWS,
        JADES_B,
        JADES_B_LT,
        XADES_B,
    }

    static class JaxpSchemaFactoryBuilder extends SchemaFactoryBuilder {
        @Override
        protected SchemaFactory instantiateFactory() {
            return SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI,
                    "com.sun.org.apache.xerces.internal.jaxp.validation.XMLSchemaFactory", null);
        }
    }
}
