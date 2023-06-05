/**
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.common.managementrequest.model;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.SystemProperties;
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

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.util.MultiPartOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static ee.ria.xroad.common.ErrorCodes.X_CANNOT_CREATE_SIGNATURE;
import static ee.ria.xroad.common.ErrorCodes.X_CERT_VALIDATION;
import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.ErrorCodes.translateWithPrefix;
import static ee.ria.xroad.common.util.CryptoUtils.calculateDigest;
import static ee.ria.xroad.common.util.CryptoUtils.readCertificate;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_SIG_ALGO_ID;
import static ee.ria.xroad.common.util.MimeUtils.mpRelatedContentType;

@Slf4j
public class AuthCertRegRequest implements ManagementRequest {
    private static final String SIGNATURE_DIGEST_ALGORITHM_ID =
            SystemProperties.getAuthCertRegSignatureDigestAlgorithmId();

    private final byte[] authCert;
    private final ClientId owner;
    private final SoapMessageImpl requestMessage;

    private CertificateInfo ownerCert;

    private byte[] dataToSign;

    private MultiPartOutputStream multipart;

    public AuthCertRegRequest(byte[] authCert, ClientId owner, SoapMessageImpl request) throws Exception {
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
        return mpRelatedContentType(multipart.getBoundary(), MimeTypes.BINARY);
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
            throw new CodedException(X_CERT_VALIDATION, "Authentication certificate is invalid: %s", e.getMessage());
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
        GetKeyIdForCertHashResponse authKeyId = getAuthKeyId();
        MemberSigningInfo memberSigningInfo = getMemberSigningInfo();

        String authKeySignAlogId = CryptoUtils.getSignatureAlgorithmId(SIGNATURE_DIGEST_ALGORITHM_ID,
                authKeyId.getSignMechanismName());
        String ownerSignAlgoId = CryptoUtils.getSignatureAlgorithmId(SIGNATURE_DIGEST_ALGORITHM_ID,
                memberSigningInfo.getSignMechanismName());

        String[] authSignaturePartHeaders = {HEADER_SIG_ALGO_ID + ": " + authKeySignAlogId};
        String[] ownerSignaturePartHeaders = {HEADER_SIG_ALGO_ID + ": " + ownerSignAlgoId};

        byte[] digest = calculateDigest(SIGNATURE_DIGEST_ALGORITHM_ID, dataToSign);

        multipart.startPart(MimeTypes.BINARY, authSignaturePartHeaders);
        multipart.write(createSignature(authKeyId.getKeyId(), authKeySignAlogId, digest));

        multipart.startPart(MimeTypes.BINARY, ownerSignaturePartHeaders);
        multipart.write(createSignature(memberSigningInfo.getKeyId(), ownerSignAlgoId, digest));
    }

    private void writeSoap() throws IOException {
        multipart.startPart(MimeTypes.TEXT_XML_UTF8);
        multipart.write(dataToSign);
    }

    private GetKeyIdForCertHashResponse getAuthKeyId() {
        try {
            String certHash = CryptoUtils.calculateCertHexHash(authCert);

            return SignerClient.execute(new GetKeyIdForCertHash(certHash));
        } catch (Exception e) {
            throw translateException(e);
        }
    }

    private MemberSigningInfo getMemberSigningInfo() throws Exception {
        try {
            MemberSigningInfo signingInfo = SignerClient.execute(new GetMemberSigningInfo(owner));

            ownerCert = signingInfo.getCert();

            return signingInfo;
        } catch (Exception e) {
            throw translateException(e);
        }
    }

    private static byte[] createSignature(String keyId, String signAlgoId, byte[] digest) throws Exception {
        try {
            SignResponse response = SignerClient.execute(new Sign(keyId, signAlgoId, digest));

            return response.getSignature();
        } catch (Exception e) {
            throw translateWithPrefix(X_CANNOT_CREATE_SIGNATURE, e);
        }
    }
}
