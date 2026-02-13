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
package org.niis.xroad.globalconf.impl.signature;

import ee.ria.xroad.common.certificateprofile.impl.SignCertificateProfileInfoParameters;
import ee.ria.xroad.common.hashchain.DigestValue;
import ee.ria.xroad.common.hashchain.HashChainReferenceResolver;
import ee.ria.xroad.common.hashchain.HashChainVerifier;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.signature.IdResolver;
import ee.ria.xroad.common.signature.MessagePart;
import ee.ria.xroad.common.signature.Signature;
import ee.ria.xroad.common.signature.SignatureData;
import ee.ria.xroad.common.signature.SignatureResourceResolver;
import ee.ria.xroad.common.signature.SignatureSchemaValidator;
import ee.ria.xroad.common.util.CertUtils;
import ee.ria.xroad.common.util.MessageFileNames;

import lombok.extern.slf4j.Slf4j;
import org.apache.xml.security.exceptions.XMLSecurityException;
import org.apache.xml.security.keys.keyresolver.KeyResolverException;
import org.apache.xml.security.signature.Manifest;
import org.apache.xml.security.signature.MissingResourceFailureException;
import org.apache.xml.security.signature.XMLSignature;
import org.apache.xml.security.signature.XMLSignatureException;
import org.apache.xml.security.utils.resolver.ResourceResolverSpi;
import org.bouncycastle.cert.ocsp.OCSPException;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.operator.OperatorCreationException;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.cert.CertChain;
import org.niis.xroad.globalconf.impl.cert.CertChainFactory;
import org.niis.xroad.globalconf.impl.cert.CertChainVerifier;
import org.niis.xroad.globalconf.impl.cert.CertHelper;
import org.niis.xroad.globalconf.impl.ocsp.OcspVerifierFactory;
import org.w3c.dom.Node;

import javax.xml.transform.dom.DOMSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ee.ria.xroad.common.ErrorCodes.X_MALFORMED_SIGNATURE;
import static ee.ria.xroad.common.ErrorCodes.translateException;
import static org.niis.xroad.common.core.exception.ErrorCode.INCORRECT_CERTIFICATE;
import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_REFERENCE;
import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_SIGNATURE_VALUE;
import static org.niis.xroad.common.core.exception.ErrorCode.MALFORMED_SIGNATURE;

/**
 * Encapsulates the AsiC XAdES signature profile. This class verifies the
 * signature used in signing messages.
 */
@Slf4j
public class SignatureVerifier {

    private final GlobalConfProvider globalConfProvider;
    private final OcspVerifierFactory ocspVerifierFactory;

    /**
     * The signature object.
     */
    private final Signature signature;

    /**
     * The parts to be verified.
     */
    private final List<MessagePart> parts = new ArrayList<>();

    /**
     * The hash chain result.
     */
    private final String hashChainResult;

    /**
     * The hash chain.
     */
    private final String hashChain;

    /**
     * The instance of Asic resource resolver to use
     * during manifest verification.
     */
    private ResourceResolverSpi resourceResolver;

    /**
     * The instance of hash chain reference resolver.
     */
    private HashChainReferenceResolver hashChainReferenceResolver;

    /**
     * Indicates whether to verify against Xades schema or not.
     */
    private boolean verifySchema = true;

    /**
     * Constructs a new signature verifier using the specified string
     * containing the signature xml.
     *
     * @param globalConfProvider global conf provider
     * @param signatureData      the signature data
     */
    public SignatureVerifier(GlobalConfProvider globalConfProvider, OcspVerifierFactory ocspVerifierFactory, SignatureData signatureData) {
        this(globalConfProvider, ocspVerifierFactory, new Signature(signatureData.getSignatureXml()),
                signatureData.getHashChainResult(),
                signatureData.getHashChain());
    }

    /**
     * Constructs a new signature verifier for the specified signature.
     *
     * @param globalConfProvider global conf provider
     * @param signature          the signature
     */
    public SignatureVerifier(GlobalConfProvider globalConfProvider, OcspVerifierFactory ocspVerifierFactory, Signature signature) {
        this(globalConfProvider, ocspVerifierFactory, signature, null, null);
    }

    /**
     * Constructs a new signature verifier for the specified signature.
     *
     * @param globalConfProvider global conf provider
     * @param signature          the signature
     * @param hashChainResult    the hash chain result
     * @param hashChain          the hash chain
     */
    public SignatureVerifier(GlobalConfProvider globalConfProvider, OcspVerifierFactory ocspVerifierFactory,
                             Signature signature, String hashChainResult, String hashChain) {
        this.globalConfProvider = globalConfProvider;
        this.ocspVerifierFactory = ocspVerifierFactory;
        this.signature = signature;
        this.hashChainResult = hashChainResult;
        this.hashChain = hashChain;
    }

    /**
     * @return the signature object.
     */
    public Signature getSignature() {
        return signature;
    }

    /**
     * Adds parts to be signed or verifier.
     *
     * @param messageParts the parts to add
     */
    public void addParts(List<MessagePart> messageParts) {
        this.parts.addAll(messageParts);
    }

    /**
     * Adds part to be signed or verified.
     *
     * @param part the part to add
     */
    public void addPart(MessagePart part) {
        this.parts.add(part);
    }

    /**
     * Sets whether to use AsicResourceResolver when verifying signature.
     *
     * @param resolver the resource resolver
     */
    public void setSignatureResourceResolver(ResourceResolverSpi resolver) {
        this.resourceResolver = resolver;
    }

    /**
     * Sets the hash chain resource resolver to be used when verifying
     * hash chain.
     *
     * @param resolver the resource resolver
     */
    public void setHashChainResourceResolver(
            HashChainReferenceResolver resolver) {
        this.hashChainReferenceResolver = resolver;
    }

    /**
     * Sets whether to verify the signature XML against the Xades schema.
     *
     * @param shouldVerifySchema if true, the signature XML will be verified
     *                           against the schema
     */
    public void setVerifySchema(boolean shouldVerifySchema) {
        this.verifySchema = shouldVerifySchema;
    }

    /**
     * @return the signing certificate
     * @throws Exception if an error occurs
     */
    public X509Certificate getSigningCertificate() throws KeyResolverException {
        X509Certificate cert = signature.getSigningCertificate();
        if (cert == null) {
            throw XrdRuntimeException.systemException(MALFORMED_SIGNATURE,
                    "Signature does not contain signing certificate");
        }

        if (!CertUtils.isSigningCert(cert)) {
            throw XrdRuntimeException.systemException(MALFORMED_SIGNATURE,
                    "Certificate %s is not a signing certificate".formatted(
                    cert.getSubjectX500Principal().getName()));
        }

        return cert;
    }

    /**
     * Returns the OCSP response for the signing certificate
     *
     * @param instanceIdentifier the instance identifier
     * @return the OCSP response of the signing certificate
     */
    public OCSPResp getSigningOcspResponse(String instanceIdentifier)
            throws KeyResolverException, CertificateEncodingException, IOException, OCSPException, OperatorCreationException {
        X509Certificate cert = signature.getSigningCertificate();
        List<OCSPResp> responses = signature.getOcspResponses();
        X509Certificate issuer = globalConfProvider.getCaCert(instanceIdentifier, cert);

        return CertHelper.getOcspResponseForCert(cert, issuer, responses);
    }

    /**
     * Verifies the signature. The verification process is as follows:
     * <ol>
     * <li>Verify schema, if schema verification
     * is enabled.</li>
     * <li>If the signature contains hash chain, then the hash
     * chain is verified.</li>
     * <li>The signing certificate is retrieved from the signature and
     * signer name is verified -- that the name in certificate matches the
     * name in message.</li>
     * <li>The signature value is verified.</li>
     * <li>Verify timestamp manifests, if the message contains any.</li>
     * <li>Verify the certificate chain using the signer certificate, OCSP
     * responses and any extra certificates.</li>
     * </ol>
     * This method is not reentrant.
     *
     * @param signer names the subject that claims to have been signed
     *               the message.
     * @param atDate Date that is used to check validity of the certificates.
     */
    public void verify(ClientId signer, Date atDate) throws XMLSecurityException, IOException, CertificateEncodingException {
        // first, validate the signature against the Xades schema
        // our asic:XadesSignatures element contains only one Xades signature
        if (verifySchema) {
            verifySchema();
        }

        // if this is a batch signature, verify the hash chain
        if (hashChainResult != null
                && signature.references(MessageFileNames.SIG_HASH_CHAIN_RESULT)) {
            verifyHashChain();
        }

        // proceed with verifying the signature
        X509Certificate signingCert = getSigningCertificate();
        verifySignerName(signer, signingCert);

        verifySignatureValue(signingCert);
        verifyTimestampManifests();
        verifyCertificateChain(atDate, signer, signingCert);
    }

    private void verifySchema() throws IOException {
        Node signatureNode =
                signature.getDocument().getDocumentElement().getFirstChild();
        SignatureSchemaValidator.validate(new DOMSource(signatureNode));
    }

    private void verifyHashChain() {
        HashChainReferenceResolver resolver = hashChainReferenceResolver;
        if (resolver == null) {
            resolver = new HashChainReferenceResolverImpl();
        }

        try {
            HashChainVerifier.verify(is(hashChainResult), resolver,
                    getHashChainInputs());
        } catch (Exception e) {
            throw translateException(e).withPrefix(X_MALFORMED_SIGNATURE);
        }
    }

    private void verifySignerName(ClientId signer, X509Certificate signingCert) {
        ClientId cn = globalConfProvider.getSubjectName(
                new SignCertificateProfileInfoParameters(
                        signer, signer.getMemberCode()
                ),
                signingCert);
        if (!signer.memberEquals(cn)) {
            throw XrdRuntimeException.systemException(INCORRECT_CERTIFICATE,
                    "Name in certificate (%s) does not match name in message (%s)".formatted(cn, signer));
        }
    }

    private void verifySignatureValue(X509Certificate signingCert) throws XMLSignatureException {
        XMLSignature s = signature.getXmlSignature();

        s.addResourceResolver(new IdResolver(signature.getDocument()));

        if (resourceResolver == null) {
            s.addResourceResolver(new SignatureResourceResolver(parts, hashChainResult));
        } else {
            s.addResourceResolver(resourceResolver);
        }

        if (!s.checkSignatureValue(signingCert)) {
            throw XrdRuntimeException.systemException(INVALID_SIGNATURE_VALUE, "Signature is not valid");
        }
    }

    private void verifyTimestampManifests() throws XMLSecurityException {
        // Get the ts-root-manifest, and then iterate over its references
        // to find any existing ts-manifests and verify their digests.
        List<Manifest> tsManifests = signature.getTimestampManifests();
        for (Manifest manifest : tsManifests) {
            manifest.addResourceResolver(
                    new IdResolver(signature.getDocument()));
            try {
                if (!manifest.verifyReferences()) {
                    throw XrdRuntimeException.systemException(INVALID_REFERENCE,
                            "Timestamp manifest verification failed for "
                                    + manifest.getId());
                }
            } catch (MissingResourceFailureException e) {
                throw XrdRuntimeException.systemException(INVALID_REFERENCE,
                        "Could not find " + e.getReference().getURI());
            }
        }
    }

    private void verifyCertificateChain(Date atDate, ClientId signer, X509Certificate signingCert)
            throws CertificateEncodingException, IOException {
        CertChain certChain =
                CertChainFactory.create(signer.getXRoadInstance(),
                        globalConfProvider.getCaCert(signer.getXRoadInstance(), signingCert),
                        signingCert,
                        signature.getExtraCertificates());
        new CertChainVerifier(globalConfProvider, ocspVerifierFactory, certChain).verify(signature.getOcspResponses(),
                atDate);
    }

    private Map<String, DigestValue> getHashChainInputs() {
        Map<String, DigestValue> inputs = new HashMap<>();
        for (MessagePart part : parts) {
            inputs.put(part.getName(), getDigestValue(part));
        }

        return inputs;
    }

    private MessagePart getPart(String name) {
        for (MessagePart partHash : parts) {
            if (partHash.getName().equals(name)) {
                return partHash;
            }
        }

        return null;
    }

    private static DigestValue getDigestValue(MessagePart part) {
        if (part.getData() != null) {
            return new DigestValue(part.getHashAlgoId(), part.getData());
        }

        return null;
    }

    private final class HashChainReferenceResolverImpl
            implements HashChainReferenceResolver {

        @Override
        public InputStream resolve(String uri) {
            if (uri.equals(MessageFileNames.SIG_HASH_CHAIN) && (hashChain != null)) {
                return is(hashChain);
            }
            return null;
        }

        @Override
        public boolean shouldResolve(String uri, byte[] digestValue) {
            return true;
        }
    }

    private static InputStream is(String str) {
        return new ByteArrayInputStream(str.getBytes(StandardCharsets.UTF_8));
    }

}
