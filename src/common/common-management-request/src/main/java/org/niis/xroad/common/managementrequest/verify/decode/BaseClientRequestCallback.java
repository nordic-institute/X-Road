/**
 * The MIT License
 * <p>
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
package org.niis.xroad.common.managementrequest.verify.decode;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.certificateprofile.impl.SignCertificateProfileInfoParameters;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.request.ClientRequestType;
import ee.ria.xroad.common.util.MimeUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.niis.xroad.common.managementrequest.model.ManagementRequestType;
import org.niis.xroad.common.managementrequest.verify.ManagementRequestParser;
import org.niis.xroad.common.managementrequest.verify.ManagementRequestVerifier;

import java.io.IOException;
import java.io.InputStream;
import java.security.cert.X509Certificate;
import java.util.Map;
import java.util.Objects;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_REQUEST;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_SIGNATURE_VALUE;
import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.util.CryptoUtils.readCertificate;
import static org.niis.xroad.common.managementrequest.verify.decode.util.ManagementRequestVerificationUtils.validateServerId;
import static org.niis.xroad.common.managementrequest.verify.decode.util.ManagementRequestVerificationUtils.verifyCertificate;
import static org.niis.xroad.common.managementrequest.verify.decode.util.ManagementRequestVerificationUtils.verifySignature;

@Slf4j
@RequiredArgsConstructor
public abstract class BaseClientRequestCallback implements ManagementRequestDecoderCallback {
    private static final String DUMMY_CLIENT_ID = "dummy";

    private final ManagementRequestVerifier.DecoderCallback rootCallback;
    private final ManagementRequestType requestType;

    private byte[] clientSignatureBytes;
    private String clientSignatureAlgoId;

    private byte[] clientCertBytes;
    private byte[] clientCertOcspBytes;

    private ClientRequestType clientRequestType;

    @Override
    public void attachment(InputStream content, Map<String, String> additionalHeaders) throws IOException {
        if (clientSignatureBytes == null) {
            log.info("Reading client signature");

            clientSignatureAlgoId = additionalHeaders.get(MimeUtils.HEADER_SIG_ALGO_ID);
            clientSignatureBytes = IOUtils.toByteArray(content);
        } else if (clientCertBytes == null) {
            log.info("Reading client cert");

            clientCertBytes = IOUtils.toByteArray(content);
        } else if (clientCertOcspBytes == null) {
            log.info("Reading client cert OCSP");

            clientCertOcspBytes = IOUtils.toByteArray(content);
        } else {
            throw new CodedException(X_INTERNAL_ERROR, "Unexpected content in multipart while reading request of type %s", requestType);
        }
    }

    @Override
    public void onCompleted() {
        verifyMessagePart(clientSignatureBytes, "Client signature is missing");
        verifyMessagePart(clientCertBytes, "Client certificate is missing");
        verifyMessagePart(clientCertOcspBytes, "Client certificate OCSP is missing");

        try {
            clientRequestType = ManagementRequestParser.parseRequest(rootCallback.getSoapMessage(), requestType.getServiceCode());
            verifyMessage();
        } catch (Exception e) {
            log.error("Failed to verify management request of type {}", requestType, e);
            throw translateException(e);
        }
    }

    @Override
    public Object getRequest() {
        return clientRequestType;
    }

    private void verifyMessage() throws Exception {
        final SoapMessageImpl soap = rootCallback.getSoapMessage();
        byte[] dataToVerify = soap.getBytes();

        log.info("Verifying client signature");

        X509Certificate x509ClientCert = readCertificate(this.clientCertBytes);

        if (!verifySignature(x509ClientCert, clientSignatureBytes, clientSignatureAlgoId, dataToVerify)) {
            throw new CodedException(X_INVALID_SIGNATURE_VALUE, "Client signature verification failed");
        }

        log.info("Verifying client certificate");

        OCSPResp clientCertOcsp = new OCSPResp(this.clientCertOcspBytes);
        verifyCertificate(x509ClientCert, clientCertOcsp);

        final SecurityServerId serverId = clientRequestType.getServer();
        validateServerId(serverId);
        if (!Objects.equals(soap.getClient(), serverId.getOwner())) {
            throw new CodedException(X_INVALID_REQUEST, "Sender does not match server owner.");
        }

        // Verify that the subject id from the certificate matches the one
        // in the request (client). The certificate must belong to the member
        // that is used as a client.
        ClientId idFromCert = getClientIdFromCert(x509ClientCert);

        ClientId idFromReq = clientRequestType.getClient();

        // Separate conditions are needed when the client is 1) subsystem and 2) member:
        //
        // 1. When client is a subsystem, idFromReq is the subsystem code of the client
        // and idFromCert is the member code from the sign cert. The subsystem must
        // be owned by the member that signed the request.
        // 2. When client is a member, idFromReq is the member code of the client
        // and idFromCert is the member code from the sign cert. The member codes must match.
        if (!idFromReq.subsystemContainsMember(idFromCert) && !idFromReq.equals(idFromCert)) {
            throw new CodedException(X_INVALID_REQUEST,
                    "Subject identifier (%s) in certificate does not match"
                            + " client's member identifier (%s) in request",
                    idFromCert, idFromReq);
        }
    }

    private static ClientId getClientIdFromCert(X509Certificate cert) throws Exception {
        return GlobalConf.getSubjectName(
                new SignCertificateProfileInfoParameters(
                        ClientId.Conf.create(
                                GlobalConf.getInstanceIdentifier(),
                                DUMMY_CLIENT_ID,
                                DUMMY_CLIENT_ID
                        ),
                        DUMMY_CLIENT_ID
                ),
                cert
        );
    }


}
