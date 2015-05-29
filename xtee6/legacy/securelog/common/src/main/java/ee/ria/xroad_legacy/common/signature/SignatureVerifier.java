package ee.ria.xroad_legacy.common.signature;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.transform.dom.DOMSource;

import org.apache.xml.security.signature.Manifest;
import org.apache.xml.security.signature.MissingResourceFailureException;
import org.apache.xml.security.signature.XMLSignature;
import org.apache.xml.security.signature.XMLSignatureInput;
import org.apache.xml.security.utils.resolver.ResourceResolverException;
import org.apache.xml.security.utils.resolver.ResourceResolverSpi;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.w3c.dom.Attr;
import org.w3c.dom.Node;

import ee.ria.xroad.common.cert.CertHelper;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.hashchain.DigestValue;
import ee.ria.xroad.common.hashchain.HashChainReferenceResolver;
import ee.ria.xroad.common.hashchain.HashChainVerifier;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad_legacy.common.CodedException;
import ee.ria.xroad_legacy.common.cert.CertChain;
import ee.ria.xroad_legacy.common.util.MessageFileNames;

import static ee.ria.xroad_legacy.common.ErrorCodes.*;
import static ee.ria.xroad_legacy.common.util.CryptoUtils.calculateDigest;

/**
 * Encapsulates the AsiC XAdES signature profile. This class verifies the
 * signature used in signing messages.
 */
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

    /** Constructs a new signature verifier using the specified string
     * containing the signature xml. */
    public SignatureVerifier(SignatureData signatureData) throws Exception {
        this.signature = new Signature(signatureData.getSignatureXml());
        this.hashChainResult = signatureData.getHashChainResult();
        this.hashChain = signatureData.getHashChain();
    }

    /** Constructs a new signature verifier for the specified signature. */
    public SignatureVerifier(Signature signature) {
        this(signature, null, null);
    }

    /** Constructs a new signature verifier for the specified signature. */
    public SignatureVerifier(Signature signature, String hashChainResult,
            String hashChain) {
        this.signature = signature;
        this.hashChainResult = hashChainResult;
        this.hashChain = hashChain;
    }

    /** Returns the signature object. */
    public Signature getSignature() {
        return signature;
    }

    /** Adds parts to be signed or verifier. */
    public void addParts(List<MessagePart> parts) {
        this.parts.addAll(parts);
    }

    /** Adds part to be signed or verified. */
    public void addPart(MessagePart part) {
        this.parts.add(part);
    }

    /** Sets whether to use AsicResourceResolver when verifying signature.
     * @param resolver the resource resolver */
    public void setSignatureResourceResolver(ResourceResolverSpi resolver) {
        this.resourceResolver = resolver;
    }

    /** Sets the hash chain resource resolver to be used when verifying
     * hash chain.
     * @param resourceResolver the resource resolver */
    public void setHashChainResourceResolver(
            HashChainReferenceResolver resolver) {
        this.hashChainReferenceResolver = resolver;
    }

    /** Sets whether to verify the signature XML against the Xades schema.
     * @param verifySchema if true, the signature XML will be verified against
     *        the schema */
    public void setVerifySchema(boolean verifySchema) {
        this.verifySchema = verifySchema;
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
     */
    public void verify(ClientId signer, Date atDate) throws Exception {
        // first, validate the signature against the Xades schema
        // our asic:XadesSignatures element contains only one Xades signature
        if (verifySchema) {
            verifySchema();
        }

        // if this is a batch signature, verify the hash chain
        if (hashChainResult != null
                && signature.references(MessageFileNames.HASH_CHAIN_RESULT)) {
            verifyHashChain();
        }

        // proceed with verifying the signature
        X509Certificate signingCert = getSigningCertificate();
        verifySignerName(signer, signingCert);

        verifySignatureValue(signingCert);
        verifyTimestampManifests();
        verifyCertificateChain(atDate, signingCert);
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

    public X509Certificate getSigningCertificate() throws Exception {
        X509Certificate cert = signature.getSigningCertificate();
        if (cert == null) {
            throw new CodedException(X_MALFORMED_SIGNATURE,
                    "Signature does not contain signing certificate");
        }

        return cert;
    }

    public OCSPResp getSigningOcspResponse() throws Exception {
        X509Certificate cert = signature.getSigningCertificate();
        List<OCSPResp> responses = signature.getOcspResponses();
        X509Certificate issuer =
                GlobalConf.getCaCert(GlobalConf.getInstanceIdentifier(), cert);

        return CertHelper.getOcspResponseForCert(cert, issuer, responses);
    }

    private static void verifySignerName(ClientId signer,
            X509Certificate signingCert) throws Exception {
        ClientId cn = GlobalConf.getSubjectName(
                GlobalConf.getInstanceIdentifier(), signingCert);
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

    private void verifyCertificateChain(Date atDate,
            X509Certificate signingCert) throws Exception {
        CertChain certChain = new CertChain(signingCert,
                Arrays.asList(GlobalConf.getCaCert(
                        GlobalConf.getInstanceIdentifier(), signingCert)),
                        signature.getExtraCertificates());
        certChain.verify(signature.getOcspResponses(), atDate);
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
        if (part.getBase64Data() != null) {
            byte[] data = null;
            // We assume message is not hashed, so we hash it here
            if (MessageFileNames.MESSAGE.equals(part.getName())) {
                data = calculateDigest(part.getHashAlgoId(), part.getData());
            } else {
                data = part.getData(); // attachment hash
            }

            return new DigestValue(part.getHashAlgorithmURI(), data);
        }

        return null;
    }

    private class SignatureResourceResolverImpl extends ResourceResolverSpi {

        @Override
        public boolean engineCanResolve(Attr uri, String baseUri) {
            switch (uri.getValue()) {
                case MessageFileNames.MESSAGE:
                case MessageFileNames.HASH_CHAIN_RESULT:
                    return true;
            }

            return false;
        }

        @Override
        public XMLSignatureInput engineResolve(Attr uri, String baseUri)
                throws ResourceResolverException {
            switch (uri.getValue()) {
                case MessageFileNames.MESSAGE:
                    MessagePart part = getPart(MessageFileNames.MESSAGE);
                    if (part != null && part.getBase64Data() != null) {
                        return new XMLSignatureInput(part.getData());
                    }
                    break;
                case MessageFileNames.HASH_CHAIN_RESULT:
                    return new XMLSignatureInput(is(hashChainResult));
            }

            return null;
        }
    }

    private class HashChainReferenceResolverImpl
            implements HashChainReferenceResolver {

        @Override
        public InputStream resolve(String uri) throws IOException {
            switch (uri) {
                case MessageFileNames.HASH_CHAIN:
                    if (hashChain != null) {
                        return is(hashChain);
                    }
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
