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
package org.niis.xroad.common.managementrequest.model;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.crypto.Signatures;
import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.message.MultipartOutputStream;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.MimeTypes;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.niis.xroad.signer.client.SignerRpcClient.KeyIdInfo;
import org.niis.xroad.signer.client.SignerRpcClient.MemberSigningInfoDto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static ee.ria.xroad.common.ErrorCodes.X_CANNOT_CREATE_SIGNATURE;
import static ee.ria.xroad.common.ErrorCodes.X_CERT_VALIDATION;
import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.ErrorCodes.translateWithPrefix;
import static ee.ria.xroad.common.crypto.Digests.calculateDigest;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_SIG_ALGO_ID;
import static ee.ria.xroad.common.util.MimeUtils.mpRelatedContentType;

@Slf4j
public class AuthCertRegRequest implements ManagementRequest {
    private static final DigestAlgorithm SIGNATURE_DIGEST_ALGORITHM_ID =
            SystemProperties.getAuthCertRegSignatureDigestAlgorithmId();

    private final SignerRpcClient signerRpcClient;
    private final byte[] authCert;
    private final ClientId owner;
    private final SoapMessageImpl requestMessage;

    private CertificateInfo ownerCert;

    private byte[] dataToSign;

    private MultipartOutputStream multipart;

    public AuthCertRegRequest(SignerRpcClient signerRpcClient, byte[] authCert, ClientId owner, SoapMessageImpl request) throws Exception {
        this.signerRpcClient = signerRpcClient;
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
        multipart = new MultipartOutputStream(out);

        writeSoap();
        writeSignatures();
        writeCerts();

        multipart.close();
        return new ByteArrayInputStream(out.toByteArray());
    }

    private void verifyAuthCert() {
        try {
            CryptoUtils.readCertificate(authCert).checkValidity();
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
        KeyIdInfo authKeyId = getAuthKeyId();
        MemberSigningInfoDto memberSigningInfo = getMemberSigningInfo();

        var authKeySignAlogId = SignAlgorithm.ofDigestAndMechanism(SIGNATURE_DIGEST_ALGORITHM_ID,
                authKeyId.signMechanismName());
        var ownerSignAlgoId = SignAlgorithm.ofDigestAndMechanism(SIGNATURE_DIGEST_ALGORITHM_ID,
                memberSigningInfo.signMechanismName());

        String[] authSignaturePartHeaders = {HEADER_SIG_ALGO_ID + ": " + authKeySignAlogId.name()};
        String[] ownerSignaturePartHeaders = {HEADER_SIG_ALGO_ID + ": " + ownerSignAlgoId.name()};

        byte[] digest = calculateDigest(SIGNATURE_DIGEST_ALGORITHM_ID, dataToSign);

        multipart.startPart(MimeTypes.BINARY, authSignaturePartHeaders);
        multipart.write(createSignature(authKeyId.keyId(), authKeySignAlogId, digest));

        multipart.startPart(MimeTypes.BINARY, ownerSignaturePartHeaders);
        multipart.write(createSignature(memberSigningInfo.keyId(), ownerSignAlgoId, digest));
    }

    private void writeSoap() throws IOException {
        multipart.startPart(MimeTypes.TEXT_XML_UTF8);
        multipart.write(dataToSign);
    }

    private KeyIdInfo getAuthKeyId() {
        try {
            String certHash = CryptoUtils.calculateCertHexHash(authCert);
            return signerRpcClient.getKeyIdForCertHash(certHash);
        } catch (Exception e) {
            throw translateException(e);
        }
    }

    private MemberSigningInfoDto getMemberSigningInfo() {
        try {
            MemberSigningInfoDto signingInfo = signerRpcClient.getMemberSigningInfo(owner);

            ownerCert = signingInfo.cert();

            return signingInfo;
        } catch (Exception e) {
            throw translateException(e);
        }
    }

    private byte[] createSignature(String keyId, SignAlgorithm signAlgoId, byte[] digest) {
        try {
            return Signatures.useAsn1DerFormat(signAlgoId, signerRpcClient.sign(keyId, signAlgoId, digest));
        } catch (Exception e) {
            throw translateWithPrefix(X_CANNOT_CREATE_SIGNATURE, e);
        }
    }
}
