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

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.crypto.Signatures;
import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;
import ee.ria.xroad.common.crypto.identifier.SignAlgorithm;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.common.util.MultiPartOutputStream;

import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.signer.api.dto.CertificateInfo;
import org.niis.xroad.signer.client.SignerRpcClient;
import org.niis.xroad.signer.client.SignerRpcClient.MemberSigningInfoDto;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

import static ee.ria.xroad.common.ErrorCodes.X_CANNOT_CREATE_SIGNATURE;
import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.ErrorCodes.translateWithPrefix;
import static ee.ria.xroad.common.crypto.Digests.calculateDigest;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_SIG_ALGO_ID;
import static ee.ria.xroad.common.util.MimeUtils.mpRelatedContentType;

@Slf4j
abstract class GenericClientRequest implements ManagementRequest {
    private static final DigestAlgorithm SIGNATURE_DIGEST_ALGORITHM_ID =
            SystemProperties.getAuthCertRegSignatureDigestAlgorithmId();

    private final SignerRpcClient signerRpcClient;
    private final ClientId client;
    private final SoapMessageImpl requestMessage;

    private CertificateInfo clientCert;

    private final byte[] dataToSign;

    private MultiPartOutputStream multipart;

    GenericClientRequest(SignerRpcClient signerRpcClient, ClientId client, SoapMessageImpl request) {
        this.signerRpcClient = signerRpcClient;
        this.client = client;
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
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        multipart = new MultiPartOutputStream(out);

        writeSoap();
        writeSignature();
        writeCert();

        multipart.close();
        return new ByteArrayInputStream(out.toByteArray());
    }

    private void writeCert() throws Exception {
        // Write client certificate and corresponding OCSP response
        multipart.startPart(MimeTypes.BINARY);
        multipart.write(clientCert.getCertificateBytes());
        multipart.startPart(MimeTypes.BINARY);
        multipart.write(clientCert.getOcspBytes());
    }

    private void writeSignature() throws Exception {
        MemberSigningInfoDto memberSigningInfo = getMemberSigningInfo();

        var clientSignAlgoId = SignAlgorithm.ofDigestAndMechanism(SIGNATURE_DIGEST_ALGORITHM_ID,
                memberSigningInfo.signMechanismName());

        String[] clientSignaturePartHeaders = {HEADER_SIG_ALGO_ID + ": " + clientSignAlgoId.name()};

        byte[] digest = calculateDigest(SIGNATURE_DIGEST_ALGORITHM_ID, dataToSign);

        multipart.startPart(MimeTypes.BINARY, clientSignaturePartHeaders);
        multipart.write(createSignature(memberSigningInfo.keyId(), clientSignAlgoId, digest));
    }

    private void writeSoap() throws IOException {
        multipart.startPart(MimeTypes.TEXT_XML_UTF8);
        multipart.write(dataToSign);
    }

    private MemberSigningInfoDto getMemberSigningInfo() {
        try {
            MemberSigningInfoDto signingInfo = signerRpcClient.getMemberSigningInfo(client);

            clientCert = signingInfo.cert();

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
