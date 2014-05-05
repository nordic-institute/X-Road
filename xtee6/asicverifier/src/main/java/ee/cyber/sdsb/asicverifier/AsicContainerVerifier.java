package ee.cyber.sdsb.asicverifier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.xml.security.c14n.Canonicalizer;
import org.apache.xml.security.signature.XMLSignatureInput;
import org.apache.xml.security.utils.resolver.ResourceResolverException;
import org.apache.xml.security.utils.resolver.ResourceResolverSpi;
import org.bouncycastle.asn1.ASN1Sequence;
import org.bouncycastle.asn1.cms.ContentInfo;
import org.bouncycastle.cert.ocsp.BasicOCSPResp;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.tsp.TimeStampToken;
import org.w3c.dom.Attr;
import org.w3c.dom.Element;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.asic.AsicContainer;
import ee.cyber.sdsb.common.conf.GlobalConf;
import ee.cyber.sdsb.common.hashchain.HashChainReferenceResolver;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.message.Soap;
import ee.cyber.sdsb.common.message.SoapMessageImpl;
import ee.cyber.sdsb.common.message.SoapParserImpl;
import ee.cyber.sdsb.common.ocsp.OcspVerifier;
import ee.cyber.sdsb.common.signature.Signature;
import ee.cyber.sdsb.common.signature.SignatureData;
import ee.cyber.sdsb.common.signature.SignatureVerifier;
import ee.cyber.sdsb.common.signature.TimestampVerifier;
import ee.cyber.sdsb.common.util.CryptoUtils;
import ee.cyber.sdsb.common.util.XmlUtils;

import static ee.cyber.sdsb.common.ErrorCodes.X_MALFORMED_SIGNATURE;

public class AsicContainerVerifier {

    static {
        Security.addProvider(new BouncyCastleProvider());
        org.apache.xml.security.Init.init();
    }

    private final List<String> attachmentHashes = new ArrayList<>();

    private final AsicContainer asic;
    private ClientId signerName;
    X509Certificate signerCert;
    Date timestampDate;
    X509Certificate timestampCert;
    Date ocspDate;
    X509Certificate ocspCert;

    AsicContainerVerifier(AsicContainer asic) {
        this.asic = asic;
    }

    AsicContainer getContainer() {
        return asic;
    }

    ClientId getSignerName() {
        return signerName;
    }

    void verify() throws Exception {
        String message = asic.getMessage();
        SignatureData signature = asic.getSignature();
        signerName = getSigner(message);

        SignatureVerifier signatureVerifier = new SignatureVerifier(signature);

        Date atDate = verifyTimestamp(signatureVerifier);

        configureResourceResolvers(signatureVerifier);

        // Do not verify the schema, since the signature in the ASiC container
        // may contain the XadesTimeStamp element, which is not standard.
        signatureVerifier.setVerifySchema(false);

        signatureVerifier.verify(signerName, atDate);
        signerCert = signatureVerifier.getSigningCertificate();
        OCSPResp ocsp = signatureVerifier.getSigningOcspResponse();
        ocspDate = ((BasicOCSPResp) ocsp.getResponseObject()).getProducedAt();
        ocspCert = OcspVerifier.getOcspCert(
                (BasicOCSPResp) ocsp.getResponseObject());
    }

    List<String> getAttachmentHashes() {
        return attachmentHashes;
    }

    static AsicContainerVerifier create(InputStream is) throws Exception {
        return new AsicContainerVerifier(AsicContainer.read(is));
    }

    private void configureResourceResolvers(SignatureVerifier verifier) {
        attachmentHashes.clear();

        verifier.setSignatureResourceResolver(new ResourceResolverSpi() {
            @Override
            public boolean engineCanResolve(Attr uri, String baseURI) {
                return asic.hasEntry(uri.getValue());
            }

            @Override
            public XMLSignatureInput engineResolve(Attr uri, String baseURI)
                    throws ResourceResolverException {
                return new XMLSignatureInput(asic.getEntry(uri.getValue()));
            }
        });

        verifier.setHashChainResourceResolver(new HashChainReferenceResolver() {
            @Override
            public boolean shouldResolve(String uri, byte[] digestValue) {
                if (asic.hasEntry(uri)) {
                    return true;
                } else {
                    logUnresolvableHash(uri, digestValue);
                    return false;
                }
            }

            @Override
            public InputStream resolve(String uri) throws IOException {
                return asic.getEntry(uri);
            }
        });
    }

    private void logUnresolvableHash(String uri, byte[] digestValue) {
        String hexDigest = CryptoUtils.encodeHex(digestValue);
        attachmentHashes.add(String.format("The digest for \"%s\" is: %s", uri,
                hexDigest));
    }

    private Date verifyTimestamp(SignatureVerifier signatureVerifier)
            throws Exception {
        Element xadesTimeStampElement =
                getXadesTimeStampElement(signatureVerifier.getSignature());
        TimeStampToken tsToken = getTimeStampToken(xadesTimeStampElement);

        TimestampVerifier.verify(tsToken, getCanonicalizedTsRootManifest(
                xadesTimeStampElement), GlobalConf.getTspCertificates());

        timestampDate = tsToken.getTimeStampInfo().getGenTime();
        timestampCert = TimestampVerifier.getSignerCertificate(
                tsToken, GlobalConf.getTspCertificates());

        return tsToken.getTimeStampInfo().getGenTime();
    }

    private static byte[] getCanonicalizedTsRootManifest(Element tsElement)
            throws Exception {
        String includeUriValue =
                ((Element) tsElement.getFirstChild()).getAttribute("URI");

        Element tsRootManifest = XmlUtils.getElementById(
                tsElement.getOwnerDocument(), includeUriValue);
        if (tsRootManifest == null) {
            throw new CodedException(X_MALFORMED_SIGNATURE,
                    "Could not find element " + includeUriValue);
        }

        return XmlUtils.canonicalize(
                Canonicalizer.ALGO_ID_C14N11_OMIT_COMMENTS, tsRootManifest);
    }

    private static Element getXadesTimeStampElement(Signature signature)
            throws Exception {
        Element tsElement = signature.getXadesTimestamp();
        if (tsElement == null) {
            throw new CodedException(X_MALFORMED_SIGNATURE,
                    "Missing XAdESTimeStamp element");
        }

        return tsElement;
    }

    private static TimeStampToken getTimeStampToken(Element tsElement)
            throws Exception {
        String tsDerBase64 = tsElement.getLastChild().getTextContent();
        byte[] tsDerDecoded = CryptoUtils.decodeBase64(tsDerBase64);
        return new TimeStampToken(new ContentInfo(
                (ASN1Sequence) ASN1Sequence.fromByteArray(tsDerDecoded)));
    }

    private static ClientId getSigner(String messageXml) {
        Soap soap = new SoapParserImpl().parse(
                new ByteArrayInputStream(messageXml.getBytes(
                        StandardCharsets.UTF_8))); // FIXME: Charset
        if (!(soap instanceof SoapMessageImpl)) {
            throw new RuntimeException("Unexpected SOAP: " + soap.getClass());
        }

        SoapMessageImpl msg = (SoapMessageImpl) soap;
        return msg.isRequest() ? msg.getClient()
                : GlobalConf.getServiceId(msg.getService()).getClientId();
    }

}
