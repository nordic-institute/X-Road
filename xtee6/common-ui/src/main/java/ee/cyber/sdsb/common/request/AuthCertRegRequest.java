package ee.cyber.sdsb.common.request;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.eclipse.jetty.util.MultiPartOutputStream;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.message.SoapMessageImpl;
import ee.cyber.sdsb.common.util.CryptoUtils;
import ee.cyber.sdsb.common.util.MimeTypes;
import ee.cyber.sdsb.signer.protocol.SignerClient;
import ee.cyber.sdsb.signer.protocol.dto.MemberSigningInfo;
import ee.cyber.sdsb.signer.protocol.message.GetKeyIdForCertHash;
import ee.cyber.sdsb.signer.protocol.message.GetKeyIdForCertHashResponse;
import ee.cyber.sdsb.signer.protocol.message.GetMemberSigningInfo;
import ee.cyber.sdsb.signer.protocol.message.Sign;
import ee.cyber.sdsb.signer.protocol.message.SignResponse;

import static ee.cyber.sdsb.common.ErrorCodes.*;
import static ee.cyber.sdsb.common.util.CryptoUtils.*;
import static ee.cyber.sdsb.common.util.MimeUtils.*;

class AuthCertRegRequest implements ManagementRequest {

    private static final String SIG_AGLO_ID = SHA512WITHRSA_ID;

    private final byte[] authCert;
    private final ClientId owner;
    private final SoapMessageImpl requestMessage;

    private byte[] ownerCert;
    private byte[] dataToSign;

    private MultiPartOutputStream multipart;

    AuthCertRegRequest(byte[] authCert, ClientId owner, SoapMessageImpl request)
            throws Exception {
        this.authCert = authCert;
        this.owner = owner;
        this.requestMessage = request;

        this.dataToSign = request.getBytes();
    }

    @Override
    public SoapMessageImpl getRequestMessage() {
        return requestMessage;
    }

    @Override
    public String getResponseContentType() {
        return MimeTypes.TEXT_XML;
    }

    @Override
    public String getRequestContentType() {
        return mpRelatedContentType(multipart.getBoundary());
    }

    @Override
    public InputStream getRequestContent() throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        multipart = new MultiPartOutputStream(out);

        writeSoap();
        writeSignatures();
        writeCerts();

        multipart.close();
        return new ByteArrayInputStream(out.toByteArray());
    }

    private void writeCerts() throws Exception {
        multipart.startPart(MimeTypes.BINARY);
        multipart.write(authCert);

        multipart.startPart(MimeTypes.BINARY);
        multipart.write(ownerCert);
    }

    private void writeSignatures() throws Exception {
        String[] partHeader = {HEADER_SIG_ALGO_ID + ": " + SIG_AGLO_ID};

        multipart.startPart(MimeTypes.BINARY, partHeader);
        multipart.write(getAuthSignature());

        multipart.startPart(MimeTypes.BINARY, partHeader);
        multipart.write(getSecurityServerOwnerSignature());
    }

    private void writeSoap() throws IOException {
        multipart.startPart(TEXT_XML_UTF8);
        multipart.write(dataToSign);
    }

    byte[] getAuthSignature() throws Exception {
        String keyId;
        try {
            String certHash = CryptoUtils.calculateCertHexHash(authCert);
            GetKeyIdForCertHashResponse keyIdResponse = SignerClient.execute(
                    new GetKeyIdForCertHash(certHash));
            keyId = keyIdResponse.getKeyId();
        } catch (Exception e) {
            throw translateException(e);
        }

        return createSignature(keyId, dataToSign);
    }

    byte[] getSecurityServerOwnerSignature() throws Exception {
        MemberSigningInfo signingInfo;
        try {
            signingInfo = SignerClient.execute(new GetMemberSigningInfo(owner));
        } catch (Exception e) {
            throw translateException(e);
        }

        // Bundle the owner cert into the multipart as well
        ownerCert = signingInfo.getCert().getCertificateBytes();
        if (ownerCert == null) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "Could not get owner signing certificate");
        }

        return createSignature(signingInfo.getKeyId(), dataToSign);
    }

    private static byte[] createSignature(String keyId, byte[] dataToSign)
            throws Exception {
        String digestAlgoId = getDigestAlgorithmId(SIG_AGLO_ID);
        byte[] digest = calculateDigest(digestAlgoId, dataToSign);
        try {
            SignResponse response =
                    SignerClient.execute(new Sign(keyId, SIG_AGLO_ID, digest));
            return response.getSignature();
        } catch (Exception e) {
            throw translateWithPrefix(X_CANNOT_CREATE_SIGNATURE, e);
        }
    }
}
