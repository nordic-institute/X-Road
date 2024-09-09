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

import ee.ria.xroad.common.cert.CertChainFactory;
import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.signer.SignerProxy;

import eu.europa.esig.dss.enumerations.DigestAlgorithm;
import eu.europa.esig.dss.xml.common.SchemaFactoryBuilder;
import eu.europa.esig.dss.xml.common.XmlDefinerUtils;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.edc.sig.xades.XrdXAdESSignatureCreator;
import org.niis.xroad.edc.sig.xades.XrdXAdESVerifier;

import javax.xml.XMLConstants;
import javax.xml.validation.SchemaFactory;

@Slf4j
public class XrdSignatureService {

    private static final DigestAlgorithm DIGEST_ALGORITHM = DigestAlgorithm.forJavaName(CryptoUtils.DEFAULT_DIGEST_ALGORITHM_ID);

    private final XrdSignatureVerifier signatureVerifier;

    static {
        // force usage of internal xerces implementation in DSS. Otherwise, not compatible Apache Xerces will be used in proxy
        // can be removed once Apache Xerces is removed from classpath
        XmlDefinerUtils.getInstance().setSchemaFactoryBuilder(new JaxpSchemaFactoryBuilder());
    }

    public XrdSignatureService(GlobalConfProvider globalConfProvider, CertChainFactory certChainFactory) {
        this.signatureVerifier = new XrdXAdESVerifier(globalConfProvider, certChainFactory, DIGEST_ALGORITHM);
    }

    public SignatureResponse sign(ClientId signingClientId, byte[] message)
            throws XrdSignatureCreationException {

        var signingInfo = getMemberSigningInfo(signingClientId);
        var signer = new XrdXAdESSignatureCreator();
        var signature = signer.sign(signingInfo, message);
        return new SignatureResponse(signature);
    }

    public SignatureResponse sign(ClientId signingClientId, byte[] message, String attachmentDigest)
            throws XrdSignatureCreationException {

        var signingInfo = getMemberSigningInfo(signingClientId);
        var signer = new XrdXAdESSignatureCreator();
        var signature = signer.sign(signingInfo, message, attachmentDigest);
        return new SignatureResponse(signature);
    }

    public void verify(String signature, byte[] message, ClientId signerClientId)
            throws XrdSignatureVerificationException {
        verify(signature, message, (byte[]) null, signerClientId);
    }

    public void verify(String signature, byte[] message, byte[] attachment, ClientId signerClientId)
            throws XrdSignatureVerificationException {

        signatureVerifier.verifySignature(signature, message, attachment, signerClientId);
    }

    public void verify(String signature, byte[] message, String attachmentDigest, ClientId signerClientId)
            throws XrdSignatureVerificationException {

        signatureVerifier.verifySignature(signature, message, attachmentDigest, signerClientId);
    }

    private SignerProxy.MemberSigningInfoDto getMemberSigningInfo(ClientId clientId) throws XrdSignatureCreationException {
        try {
            return SignerProxy.getMemberSigningInfo(clientId);
        } catch (Exception e) {
            throw new XrdSignatureCreationException("Failed to get member sign cert info", e);
        }
    }

    public static class JaxpSchemaFactoryBuilder extends SchemaFactoryBuilder {
        @Override
        protected SchemaFactory instantiateFactory() {
            return SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI,
                    "com.sun.org.apache.xerces.internal.jaxp.validation.XMLSchemaFactory", null);
        }
    }
}
