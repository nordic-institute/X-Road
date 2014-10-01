package ee.cyber.sdsb.asicverifier;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

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

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.asic.AsicContainer;
import ee.cyber.sdsb.common.conf.GlobalConf;
import ee.cyber.sdsb.common.hashchain.DigestValue;
import ee.cyber.sdsb.common.hashchain.HashChainReferenceResolver;
import ee.cyber.sdsb.common.hashchain.HashChainVerifier;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.message.Soap;
import ee.cyber.sdsb.common.message.SoapMessageImpl;
import ee.cyber.sdsb.common.message.SoapParserImpl;
import ee.cyber.sdsb.common.ocsp.OcspVerifier;
import ee.cyber.sdsb.common.signature.MessagePart;
import ee.cyber.sdsb.common.signature.Signature;
import ee.cyber.sdsb.common.signature.SignatureData;
import ee.cyber.sdsb.common.signature.SignatureVerifier;
import ee.cyber.sdsb.common.signature.TimestampVerifier;
import ee.cyber.sdsb.common.util.MessageFileNames;

import static ee.cyber.sdsb.common.ErrorCodes.X_MALFORMED_SIGNATURE;
import static ee.cyber.sdsb.common.asic.AsicContainerEntries.ENTRY_TIMESTAMP;
import static ee.cyber.sdsb.common.asic.AsicContainerEntries.ENTRY_TS_HASH_CHAIN_RESULT;
import static ee.cyber.sdsb.common.util.CryptoUtils.decodeBase64;
import static ee.cyber.sdsb.common.util.CryptoUtils.encodeHex;
import static ee.cyber.sdsb.common.util.MessageFileNames.MESSAGE;
import static ee.cyber.sdsb.common.util.MessageFileNames.SIG_HASH_CHAIN_RESULT;
import static java.nio.charset.StandardCharsets.UTF_8;

@Getter(AccessLevel.PACKAGE)
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class AsicContainerVerifier {

    static {
        Security.addProvider(new BouncyCastleProvider());
        org.apache.xml.security.Init.init();
    }

    private final List<String> attachmentHashes = new ArrayList<>();
    private final AsicContainer asic;

    private Signature signature;

    private ClientId signerName;
    private X509Certificate signerCert;

    private Date timestampDate;
    private X509Certificate timestampCert;

    private Date ocspDate;
    private X509Certificate ocspCert;

    static AsicContainerVerifier create(InputStream is) throws Exception {
        return new AsicContainerVerifier(AsicContainer.read(is));
    }

    void verify() throws Exception {
        String message = asic.getMessage();
        SignatureData signatureData = asic.getSignature();
        signature = new Signature(signatureData.getSignatureXml());
        signerName = getSigner(message);

        SignatureVerifier signatureVerifier =
                new SignatureVerifier(signature,
                        signatureData.getHashChainResult(),
                        signatureData.getHashChain());
        verifyRequiredReferencesExist();

        Date atDate = verifyTimestamp();

        configureResourceResolvers(signatureVerifier);

        // Do not verify the schema, since the signature in the ASiC container
        // may contain the XadesTimeStamp element, which is not standard.
        signatureVerifier.setVerifySchema(false);

        // Add required part "message" to the hash chain verifier.
        signatureVerifier.addPart(new MessagePart(MESSAGE, null, null));

        signatureVerifier.verify(signerName, atDate);
        signerCert = signatureVerifier.getSigningCertificate();

        OCSPResp ocsp = signatureVerifier.getSigningOcspResponse();
        ocspDate = ((BasicOCSPResp) ocsp.getResponseObject()).getProducedAt();
        ocspCert = OcspVerifier.getOcspCert(
                (BasicOCSPResp) ocsp.getResponseObject());
    }

    private void verifyRequiredReferencesExist() throws Exception {
        if (!signature.references(MESSAGE)
                && !signature.references(SIG_HASH_CHAIN_RESULT)) {
            throw new CodedException(X_MALFORMED_SIGNATURE,
                    "Signature does not reference '%s' or '%s'",
                    MESSAGE, SIG_HASH_CHAIN_RESULT);
        }
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

        verifier.setHashChainResourceResolver(
                new HashChainReferenceResolverImpl());
    }

    private void logUnresolvableHash(String uri, byte[] digestValue) {
        attachmentHashes.add(String.format("The digest for \"%s\" is: %s", uri,
                encodeHex(digestValue)));
    }

    private Date verifyTimestamp() throws Exception {
        TimeStampToken tsToken = getTimeStampToken();

        TimestampVerifier.verify(tsToken, getTimestampedData(),
                GlobalConf.getTspCertificates());

        timestampDate = tsToken.getTimeStampInfo().getGenTime();
        timestampCert = TimestampVerifier.getSignerCertificate(
                tsToken, GlobalConf.getTspCertificates());

        return tsToken.getTimeStampInfo().getGenTime();
    }

    private void verifyTimestampHashChain(byte[] tsHashChainResultBytes) {
        Map<String, DigestValue> inputs = new HashMap<>();
        inputs.put(MessageFileNames.SIGNATURE, null);

        InputStream in = new ByteArrayInputStream(tsHashChainResultBytes);
        try {
            HashChainVerifier.verify(in, new HashChainReferenceResolverImpl(),
                    inputs);
        } catch (Exception e) {
            throw new CodedException(X_MALFORMED_SIGNATURE,
                    "Failed to verify time-stamp hash chain: %s",
                    e.getMessage());
        }
    }

    private byte[] getTimestampedData() throws Exception {
        String tsHashChainResult =
                asic.getEntryAsString(ENTRY_TS_HASH_CHAIN_RESULT);
        if (tsHashChainResult != null) { // batch time-stamp
            byte[] tsHashChainResultBytes =
                    tsHashChainResult.getBytes(StandardCharsets.UTF_8);
            verifyTimestampHashChain(tsHashChainResultBytes);
            return tsHashChainResultBytes;
        } else {
            return signature.getXmlSignature().getSignatureValue();
        }
    }

    private TimeStampToken getTimeStampToken() throws Exception {
        String timestampDerBase64 = asic.getEntryAsString(ENTRY_TIMESTAMP);
        byte[] tsDerDecoded = decodeBase64(timestampDerBase64);
        return new TimeStampToken(new ContentInfo(
                (ASN1Sequence) ASN1Sequence.fromByteArray(tsDerDecoded)));
    }

    private static ClientId getSigner(String messageXml) {
        Soap soap = new SoapParserImpl().parse(
                new ByteArrayInputStream(messageXml.getBytes(UTF_8)));
        if (!(soap instanceof SoapMessageImpl)) {
            throw new RuntimeException("Unexpected SOAP: " + soap.getClass());
        }

        SoapMessageImpl msg = (SoapMessageImpl) soap;
        return msg.isRequest() ? msg.getClient()
                : GlobalConf.getServiceId(msg.getService()).getClientId();
    }

    private class HashChainReferenceResolverImpl
            implements HashChainReferenceResolver {

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
    }
}
