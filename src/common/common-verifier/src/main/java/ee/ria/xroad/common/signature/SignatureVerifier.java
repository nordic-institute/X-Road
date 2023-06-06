/**
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
import ee.ria.xroad.common.cert.CertChain;
import ee.ria.xroad.common.cert.CertChainVerifier;
import ee.ria.xroad.common.cert.CertHelper;
import ee.ria.xroad.common.certificateprofile.impl.SignCertificateProfileInfoParameters;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.hashchain.DigestValue;
import ee.ria.xroad.common.hashchain.HashChainReferenceResolver;
import ee.ria.xroad.common.hashchain.HashChainVerifier;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.util.CertUtils;
import ee.ria.xroad.common.util.MessageFileNames;

import lombok.extern.slf4j.Slf4j;
import org.apache.xml.security.signature.Manifest;
import org.apache.xml.security.signature.MissingResourceFailureException;
import org.apache.xml.security.signature.XMLSignature;
import org.apache.xml.security.signature.XMLSignatureInput;
import org.apache.xml.security.utils.resolver.ResourceResolverContext;
import org.apache.xml.security.utils.resolver.ResourceResolverException;
import org.apache.xml.security.utils.resolver.ResourceResolverSpi;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.w3c.dom.Node;

import javax.xml.transform.dom.DOMSource;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static ee.ria.xroad.common.ErrorCodes.X_INCORRECT_CERTIFICATE;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_REFERENCE;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_SIGNATURE_VALUE;
import static ee.ria.xroad.common.ErrorCodes.X_MALFORMED_SIGNATURE;
import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.util.MessageFileNames.SIG_HASH_CHAIN_RESULT;

/**
 * Encapsulates the AsiC XAdES signature profile. This class verifies the
 * signature used in signing messages.
 */
@Slf4j
public class SignatureVerifier {

    /** The signature object. */
    private final Signature signature;

    /** The parts to be verified. */
    private final List<MessagePart> parts = new ArrayList<>();

    /** The hash chain result. */
    private final String hashChainResult;

    /** The hash chain. */
    private final String hashChain;

    /** The instance of Asic resource resolver to use
     * during manifest verification.*/
    private ResourceResolverSpi resourceResolver;

    /** The instance of hash chain reference resolver. */
    private HashChainReferenceResolver hashChainReferenceResolver;

    /** Indicates whether to verify against Xades schema or not. */
    private boolean verifySchema = true;

    /**
     * Constructs a new signature verifier using the specified string
     * containing the signature xml.
     * @param signatureData the signature data
     */
    public SignatureVerifier(SignatureData signatureData) {
        this(new Signature(signatureData.getSignatureXml()),
                signatureData.getHashChainResult(),
                signatureData.getHashChain());
    }

    /**
     * Constructs a new signature verifier for the specified signature.
     * @param signature the signature
     */
    public SignatureVerifier(Signature signature) {
        this(signature, null, null);
    }

    /**
     * Constructs a new signature verifier for the specified signature.
     * @param signature the signature
     * @param hashChainResult the hash chain result
     * @param hashChain the hash chain
     */
    public SignatureVerifier(Signature signature, String hashChainResult,
            String hashChain) {
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
     * @param messageParts the parts to add
     */
    public void addParts(List<MessagePart> messageParts) {
        this.parts.addAll(messageParts);
    }

    /**
     * Adds part to be signed or verified.
     * @param part the part to add
     */
    public void addPart(MessagePart part) {
        this.parts.add(part);
    }

    /** Sets whether to use AsicResourceResolver when verifying signature.
     * @param resolver the resource resolver */
    public void setSignatureResourceResolver(ResourceResolverSpi resolver) {
        this.resourceResolver = resolver;
    }

    /**
     * Sets the hash chain resource resolver to be used when verifying
     * hash chain.
     * @param resolver the resource resolver
     */
    public void setHashChainResourceResolver(
            HashChainReferenceResolver resolver) {
        this.hashChainReferenceResolver = resolver;
    }

    /**
     * Sets whether to verify the signature XML against the Xades schema.
     * @param shouldVerifySchema if true, the signature XML will be verified
     * against the schema */
    public void setVerifySchema(boolean shouldVerifySchema) {
        this.verifySchema = shouldVerifySchema;
    }

    /**
     * @return the signing certificate
     * @throws Exception if an error occurs
     */
    public X509Certificate getSigningCertificate() throws Exception {
        X509Certificate cert = signature.getSigningCertificate();
        if (cert == null) {
            throw new CodedException(X_MALFORMED_SIGNATURE,
                    "Signature does not contain signing certificate");
        }

        if (!CertUtils.isSigningCert(cert)) {
            throw new CodedException(X_MALFORMED_SIGNATURE,
                    "Certificate %s is not a signing certificate",
                    cert.getSubjectX500Principal().getName());
        }

        return cert;
    }

    /**
     * Returns the OCSP response for the signing certificate
     * @param instanceIdentifier the instance identifier
     * @return the OCSP response of the signing certificate
     * @throws Exception if an error occurs
     */
    public OCSPResp getSigningOcspResponse(String instanceIdentifier)
            throws Exception {
        X509Certificate cert = signature.getSigningCertificate();
        List<OCSPResp> responses = signature.getOcspResponses();
        X509Certificate issuer = GlobalConf.getCaCert(instanceIdentifier, cert);

        return CertHelper.getOcspResponseForCert(cert, issuer, responses);
    }

    /** Verifies the signature. The verification process is as follows:
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
     * @param signer names the subject that claims to have been signed
     *                   the message.
     * @param atDate Date that is used to check validity of the certificates.
     * @throws Exception if verification fails
     */
    public void verify(ClientId signer, Date atDate) throws Exception {
        // first, validate the signature against the Xades schema
        // our asic:XadesSignatures element contains only one Xades signature
        if (verifySchema) {
            verifySchema();
        }

        // if this is a batch signature, verify the hash chain
        if (hashChainResult != null
                && signature.references(SIG_HASH_CHAIN_RESULT)) {
            verifyHashChain();
        }

        // proceed with verifying the signature
        X509Certificate signingCert = getSigningCertificate();
        verifySignerName(signer, signingCert);

        verifySignatureValue(signingCert);
        verifyTimestampManifests();
        verifyCertificateChain(atDate, signer, signingCert);
    }

    private void verifySchema() throws Exception {
        Node signatureNode =
                signature.getDocument().getDocumentElement().getFirstChild();
        SignatureSchemaValidator.validate(new DOMSource(signatureNode));
    }

    private void verifyHashChain() throws Exception {
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

    private static void verifySignerName(ClientId signer,
            X509Certificate signingCert) throws Exception {
        ClientId cn = GlobalConf.getSubjectName(
                new SignCertificateProfileInfoParameters(
                    signer, signer.getMemberCode()
                ),
                signingCert);
        if (!signer.memberEquals(cn)) {
            throw new CodedException(X_INCORRECT_CERTIFICATE,
                    "Name in certificate (%s) does not match "
                            + "name in message (%s)", cn, signer);
        }
    }

    private void verifySignatureValue(X509Certificate signingCert)
            throws Exception {
        XMLSignature s = signature.getXmlSignature();

        s.addResourceResolver(new IdResolver(signature.getDocument()));

        if (resourceResolver == null) {
            s.addResourceResolver(new SignatureResourceResolverImpl());
        } else {
            s.addResourceResolver(resourceResolver);
        }

        if (!s.checkSignatureValue(signingCert)) {
            throw new CodedException(X_INVALID_SIGNATURE_VALUE,
                    "Signature is not valid");
        }
    }

    private void verifyTimestampManifests() throws Exception {
        // Get the ts-root-manifest, and then iterate over its references
        // to find any existing ts-manifests and verify their digests.
        List<Manifest> tsManifests = signature.getTimestampManifests();
        for (Manifest manifest : tsManifests) {
            manifest.addResourceResolver(
                    new IdResolver(signature.getDocument()));
            try {
                if (!manifest.verifyReferences()) {
                    throw new CodedException(X_INVALID_REFERENCE,
                            "Timestamp manifest verification failed for "
                                    + manifest.getId());
                }
            } catch (MissingResourceFailureException e) {
                throw new CodedException(X_INVALID_REFERENCE,
                        "Could not find " + e.getReference().getURI());
            }
        }
    }

    private void verifyCertificateChain(Date atDate, ClientId signer, X509Certificate signingCert) {
        CertChain certChain =
                CertChain.create(signer.getXRoadInstance(), signingCert,
                        signature.getExtraCertificates());
        new CertChainVerifier(certChain).verify(signature.getOcspResponses(),
                atDate);
    }

    private Map<String, DigestValue> getHashChainInputs() throws Exception {
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

    private static DigestValue getDigestValue(MessagePart part)
            throws Exception {
        if (part.getData() != null) {
            return new DigestValue(part.getHashAlgorithmURI(), part.getData());
        }

        return null;
    }

    private class SignatureResourceResolverImpl extends ResourceResolverSpi {

        @Override
        public boolean engineCanResolveURI(ResourceResolverContext context) {
            switch (context.attr.getValue()) {
                case MessageFileNames.MESSAGE:
                case MessageFileNames.SIG_HASH_CHAIN_RESULT:
                    return true;
                default:
                    return false;
            }
        }

        @Override
        public XMLSignatureInput engineResolveURI(ResourceResolverContext context) throws ResourceResolverException {
            switch (context.attr.getValue()) {
                case MessageFileNames.MESSAGE:
                    MessagePart part = getPart(MessageFileNames.MESSAGE);

                    if (part != null && part.getMessage() != null) {
                        return new XMLSignatureInput(part.getMessage());
                    }

                    break;
                case MessageFileNames.SIG_HASH_CHAIN_RESULT:
                    return new XMLSignatureInput(is(hashChainResult));
                default: // do nothing
            }

            return null;
        }
    }

    private class HashChainReferenceResolverImpl
            implements HashChainReferenceResolver {

        @Override
        public InputStream resolve(String uri) throws IOException {
            switch (uri) {
                case MessageFileNames.SIG_HASH_CHAIN:
                    if (hashChain != null) {
                        return is(hashChain);
                    } // $FALL-THROUGH$
                default:
                    return null;
            }
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
