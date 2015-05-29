package ee.ria.xroad.common.request;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.eclipse.jetty.util.MultiPartOutputStream;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.signer.protocol.SignerClient;
import ee.ria.xroad.signer.protocol.dto.CertificateInfo;
import ee.ria.xroad.signer.protocol.dto.MemberSigningInfo;
import ee.ria.xroad.signer.protocol.message.GetKeyIdForCertHash;
import ee.ria.xroad.signer.protocol.message.GetKeyIdForCertHashResponse;
import ee.ria.xroad.signer.protocol.message.GetMemberSigningInfo;
import ee.ria.xroad.signer.protocol.message.Sign;
import ee.ria.xroad.signer.protocol.message.SignResponse;

import static ee.ria.xroad.common.ErrorCodes.*;
import static ee.ria.xroad.common.util.CryptoUtils.*;
import static ee.ria.xroad.common.util.MimeUtils.*;

class AuthCertRegRequest implements ManagementRequest {

    private static final String SIG_AGLO_ID = SHA512WITHRSA_ID;

    private final byte[] authCert;
    private final ClientId owner;
    private final SoapMessageImpl requestMessage;

    private CertificateInfo ownerCert;

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
        verifyAuthCert();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        multipart = new MultiPartOutputStream(out);

        writeSoap();
        writeSignatures();
        writeCerts();

        multipart.close();
        return new ByteArrayInputStream(out.toByteArray());
    }

    private void verifyAuthCert() throws Exception {
        try {
            readCertificate(authCert).checkValidity();
        } catch (Exception e) {
            throw new CodedException(X_CERT_VALIDATION,
                    "Authentication certificate is invalid: %s",
                    e.getMessage());
        }
    }

    private void writeCerts() throws Exception {
        // Write authentication certificate
        multipart.startPart(MimeTypes.BINARY);
        multipart.write(authCert);

        // Write security server owner certificate and corresponding OCSP response
        multipart.startPart(MimeTypes.BINARY);
        multipart.write(ownerCert.getCertificateBytes());
        multipart.startPart(MimeTypes.BINARY);
        multipart.write(ownerCert.getOcspBytes());
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
        try {
            String certHash = CryptoUtils.calculateCertHexHash(authCert);

            GetKeyIdForCertHashResponse keyIdResponse = SignerClient.execute(
                    new GetKeyIdForCertHash(certHash));

            return createSignature(keyIdResponse.getKeyId(), dataToSign);
        } catch (Exception e) {
            throw translateException(e);
        }
    }

    byte[] getSecurityServerOwnerSignature() throws Exception {
        try {
            MemberSigningInfo signingInfo =
                    SignerClient.execute(new GetMemberSigningInfo(owner));

            ownerCert = signingInfo.getCert();

            return createSignature(signingInfo.getKeyId(), dataToSign);
        } catch (Exception e) {
            throw translateException(e);
        }
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
