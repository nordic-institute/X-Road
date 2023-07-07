/**
 * The MIT License
 *
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
package org.niis.xroad.common.managementrequest.verify.decode;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.certificateprofile.impl.SignCertificateProfileInfoParameters;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.request.AuthCertRegRequestType;
import ee.ria.xroad.common.util.MimeUtils;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.apache.commons.io.IOUtils;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.niis.xroad.common.managementrequest.verify.ManagementRequestParser;
import org.niis.xroad.common.managementrequest.verify.ManagementRequestVerifier;

import java.io.IOException;
import java.io.InputStream;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

import static ee.ria.xroad.common.ErrorCodes.X_CERT_VALIDATION;
import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_REQUEST;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_SIGNATURE_VALUE;
import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.util.CryptoUtils.readCertificate;
import static org.niis.xroad.common.managementrequest.verify.decode.util.ManagementRequestVerificationUtils.assertAddress;
import static org.niis.xroad.common.managementrequest.verify.decode.util.ManagementRequestVerificationUtils.validateServerId;
import static org.niis.xroad.common.managementrequest.verify.decode.util.ManagementRequestVerificationUtils.verifyAuthCert;
import static org.niis.xroad.common.managementrequest.verify.decode.util.ManagementRequestVerificationUtils.verifyCertificate;
import static org.niis.xroad.common.managementrequest.verify.decode.util.ManagementRequestVerificationUtils.verifySignature;

@Getter
@RequiredArgsConstructor
public class AuthCertRegRequestDecoderCallback implements ManagementRequestDecoderCallback {
    private final ManagementRequestVerifier.DecoderCallback rootCallback;

    private byte[] authSignatureBytes;
    private String authSignatureAlgoId;

    private byte[] ownerSignatureBytes;
    private String ownerSignatureAlgoId;

    private byte[] authCertBytes;
    private byte[] ownerCertBytes;
    private byte[] ownerCertOcspBytes;

    private AuthCertRegRequestType authCertRegRequestType;

    @Override
    public void attachment(InputStream content, Map<String, String> additionalHeaders) throws IOException {
        if (authSignatureBytes == null) {
            authSignatureAlgoId = additionalHeaders.get(MimeUtils.HEADER_SIG_ALGO_ID);
            authSignatureBytes = IOUtils.toByteArray(content);
        } else if (ownerSignatureBytes == null) {
            ownerSignatureAlgoId = additionalHeaders.get(MimeUtils.HEADER_SIG_ALGO_ID);
            ownerSignatureBytes = IOUtils.toByteArray(content);
        } else if (authCertBytes == null) {
            authCertBytes = IOUtils.toByteArray(content);
        } else if (ownerCertBytes == null) {
            ownerCertBytes = IOUtils.toByteArray(content);
        } else if (ownerCertOcspBytes == null) {
            ownerCertOcspBytes = IOUtils.toByteArray(content);
        } else {
            throw new CodedException(X_INTERNAL_ERROR, "Unexpected content in multipart");
        }
    }

    @Override
    public void onCompleted() {
        verifyMessagePart(rootCallback.getSoapMessage(), "Request contains no SOAP message");
        verifyMessagePart(authSignatureAlgoId, "Auth signature algorithm id is missing");
        verifyMessagePart(authSignatureBytes, "Auth signature is missing");
        verifyMessagePart(ownerSignatureAlgoId, "Owner signature algorithm id is missing");
        verifyMessagePart(ownerSignatureBytes, "Owner signature is missing");
        verifyMessagePart(authCertBytes, "Auth certificate is missing");
        verifyMessagePart(ownerCertBytes, "Owner certificate is missing");
        verifyMessagePart(ownerCertOcspBytes, "Owner certificate OCSP is missing");

        try {
            authCertRegRequestType = ManagementRequestParser.parseAuthCertRegRequest(rootCallback.getSoapMessage());
            verifyMessage();
        } catch (Exception e) {
            throw translateException(e);
        }
    }

    @Override
    public Object getRequest() {
        return authCertRegRequestType;
    }

    public void verifyMessage() throws Exception {
        final SoapMessageImpl soap = rootCallback.getSoapMessage();

        final X509Certificate authCert = readCertificate(this.authCertBytes);
        if (!verifyAuthCert(authCert)) {
            throw new CodedException(X_CERT_VALIDATION, "Authentication certificate is not valid");
        }

        final byte[] dataToVerify = soap.getBytes();
        if (!verifySignature(authCert, authSignatureBytes, authSignatureAlgoId, dataToVerify)) {
            throw new CodedException(X_INVALID_SIGNATURE_VALUE, "Auth signature verification failed");
        }

        final X509Certificate ownerCert = readCertificate(this.ownerCertBytes);
        final OCSPResp ownerCertOcsp = new OCSPResp(this.ownerCertOcspBytes);
        verifyCertificate(ownerCert, ownerCertOcsp);

        if (!verifySignature(ownerCert, ownerSignatureBytes, ownerSignatureAlgoId, dataToVerify)) {
            throw new CodedException(X_INVALID_SIGNATURE_VALUE, "Owner signature verification failed.");
        }

        if (!GlobalConf.getManagementRequestService().equals(soap.getService().getClientId())
                || !GlobalConf.getInstanceIdentifier().equals(soap.getClient().getXRoadInstance())) {
            throw new CodedException(X_INVALID_REQUEST,
                    "Invalid management service address. Contact central server administrator.");
        }

        final SecurityServerId serverId = authCertRegRequestType.getServer();
        validateServerId(serverId);

        if (!Objects.equals(soap.getClient(), serverId.getOwner())) {
            throw new CodedException(X_INVALID_REQUEST, "Sender does not match server owner.");
        }

        // Verify that the attached authentication certificate matches the one in
        // the request. Note: reason for the redundancy is unclear.
        if (!Arrays.equals(this.authCertBytes, authCertRegRequestType.getAuthCert())) {
            throw new CodedException(X_CERT_VALIDATION, "Authentication certificates do not match.");
        }

        // verify that the subject id from the certificate matches the one
        // in the request (server id)
        ClientId idFromReq = serverId.getOwner();
        ClientId idFromCert = getClientIdFromCert(ownerCert, idFromReq);

        if (!idFromReq.equals(idFromCert)) {
            throw new CodedException(X_INVALID_REQUEST,
                    "Subject identifier (%s) in certificate does not match"
                            + " security server owner identifier (%s) in request",
                    idFromCert, idFromReq);
        }

        // verify that the server address (IP or FQDN) is valid
        assertAddress(authCertRegRequestType.getAddress());
    }


    private ClientId getClientIdFromCert(X509Certificate cert, ClientId clientId) throws Exception {
        return GlobalConf.getSubjectName(
                new SignCertificateProfileInfoParameters(
                        ClientId.Conf.create(
                                GlobalConf.getInstanceIdentifier(),
                                clientId.getMemberClass(),
                                clientId.getMemberCode()
                        ),
                        ""
                ),
                cert
        );
    }

}
