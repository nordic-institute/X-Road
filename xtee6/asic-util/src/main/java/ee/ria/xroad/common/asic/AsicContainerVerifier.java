/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package ee.ria.xroad.common.asic;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.hashchain.DigestValue;
import ee.ria.xroad.common.hashchain.HashChainReferenceResolver;
import ee.ria.xroad.common.hashchain.HashChainVerifier;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.message.Soap;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.message.SoapParserImpl;
import ee.ria.xroad.common.ocsp.OcspVerifier;
import ee.ria.xroad.common.signature.*;
import ee.ria.xroad.common.util.MessageFileNames;
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

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.security.cert.X509Certificate;
import java.util.*;

import static ee.ria.xroad.common.ErrorCodes.X_MALFORMED_SIGNATURE;
import static ee.ria.xroad.common.asic.AsicContainerEntries.ENTRY_TIMESTAMP;
import static ee.ria.xroad.common.asic.AsicContainerEntries.ENTRY_TS_HASH_CHAIN_RESULT;
import static ee.ria.xroad.common.util.CryptoUtils.decodeBase64;
import static ee.ria.xroad.common.util.CryptoUtils.encodeHex;
import static ee.ria.xroad.common.util.MessageFileNames.MESSAGE;
import static ee.ria.xroad.common.util.MessageFileNames.SIG_HASH_CHAIN_RESULT;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Controls the validity of ASiC containers.
 */
@Getter(AccessLevel.PUBLIC)
@RequiredArgsConstructor(access = AccessLevel.PUBLIC)
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

    /**
     * Constructs a new ASiC container verifier for the ZIP file with the
     * given filename. Attempts to verify it's contents.
     * @param filename name of the ASiC container ZIP file
     * @throws Exception if the file could not be read
     */
    public AsicContainerVerifier(String filename) throws Exception {
        try (FileInputStream in = new FileInputStream(filename)) {
            asic = AsicContainer.read(in);
        }
    }

    /**
     * Attempts to verify the ASiC container's signature and timestamp.
     * @throws Exception if verification was unsuccessful
     */
    public void verify() throws Exception {
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

        OCSPResp ocsp = signatureVerifier.getSigningOcspResponse(
                signerName.getXRoadInstance());
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
                    "Failed to verify time-stamp hash chain: %s", e);
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
        return msg.isRequest()
                ? msg.getClient() : msg.getService().getClientId();
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
