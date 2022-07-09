/**
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
package org.niis.xroad.centralserver.registrationservice.request;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.certificateprofile.impl.SignCertificateProfileInfoParameters;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.globalconfextension.GlobalConfExtensions;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.message.SoapMessage;
import ee.ria.xroad.common.message.SoapMessageDecoder;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.ocsp.OcspVerifier;
import ee.ria.xroad.common.ocsp.OcspVerifierOptions;
import ee.ria.xroad.common.request.AuthCertRegRequestType;
import ee.ria.xroad.common.request.ManagementRequests;
import ee.ria.xroad.common.util.CertUtils;
import ee.ria.xroad.common.util.MimeUtils;

import com.google.common.base.CharMatcher;
import com.google.common.net.InetAddresses;
import com.google.common.net.InternetDomainName;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.bouncycastle.cert.ocsp.OCSPResp;

import java.io.InputStream;
import java.net.IDN;
import java.security.GeneralSecurityException;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

import static ee.ria.xroad.common.ErrorCodes.X_CERT_VALIDATION;
import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_CLIENT_IDENTIFIER;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_REQUEST;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_SIGNATURE_VALUE;
import static ee.ria.xroad.common.ErrorCodes.X_OUTDATED_GLOBALCONF;
import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.util.CryptoUtils.readCertificate;

/**
 * Reads and verifies management requests.
 */
@Slf4j
public final class ManagementRequestVerifier {

    @RequiredArgsConstructor
    @Getter
    public static class Result {
        private final SoapMessageImpl soapMessage;
        private final AuthCertRegRequestType authCertRegRequest;
    }

    private ManagementRequestVerifier() {
    }

    /**
     * Reads management requests from input stream.
     * @param contentType expected content type of the stream
     * @param inputStream the input stream
     * @return management request message
     * @throws Exception in case of any errors
     */
    public static Result readRequest(String contentType, InputStream inputStream) throws Exception {

        if (!GlobalConf.isValid()) {
            throw new CodedException(X_OUTDATED_GLOBALCONF, "Global configuration is not valid");
        }

        DecoderCallback cb = new DecoderCallback();

        SoapMessageDecoder decoder = new SoapMessageDecoder(contentType, cb);
        decoder.parse(inputStream);

        return new Result(cb.getSoapMessage(), verifyAuthCertRegRequest(cb));
    }

    private static AuthCertRegRequestType verifyAuthCertRegRequest(DecoderCallback cb) throws Exception {

        X509Certificate authCert = readCertificate(cb.getAuthCert());

        if (!verifyAuthCert(authCert)) {
            throw new CodedException(X_CERT_VALIDATION, "Authentication certificate is not valid");
        }

        SoapMessageImpl soap = cb.getSoapMessage();
        byte[] dataToVerify = soap.getBytes();
        if (!verifySignature(authCert, cb.getAuthSignature(), cb.getAuthSignatureAlgoId(), dataToVerify)) {
            throw new CodedException(X_INVALID_SIGNATURE_VALUE, "Auth signature verification failed");
        }

        X509Certificate ownerCert = readCertificate(cb.getOwnerCert());
        OCSPResp ownerCertOcsp = new OCSPResp(cb.getOwnerCertOcsp());
        verifyCertificate(ownerCert, ownerCertOcsp);

        if (!verifySignature(ownerCert, cb.getOwnerSignature(), cb.getOwnerSignatureAlgoId(), dataToVerify)) {
            throw new CodedException(X_INVALID_SIGNATURE_VALUE, "Owner signature verification failed.");
        }

        if (!GlobalConf.getManagementRequestService().equals(soap.getService().getClientId())
                || !GlobalConf.getInstanceIdentifier().equals(soap.getClient().getXRoadInstance())) {
            throw new CodedException(X_INVALID_REQUEST,
                    "Invalid management service address. Contact central server administrator.");
        }

        AuthCertRegRequestType requestType = ManagementRequestParser.parseAuthCertRegRequest(soap);

        final SecurityServerId serverId = requestType.getServer();
        validateServerId(serverId);

        if (!Objects.equals(soap.getClient(), serverId.getOwner())) {
            throw new CodedException(X_INVALID_REQUEST, "Sender does not match server owner.");
        }

        // Verify that the attached authentication certificate matches the one in
        // the request. Note: reason for the redundancy is unclear.
        if (!Arrays.equals(cb.authCert, requestType.getAuthCert())) {
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
        validateAddress(requestType.getAddress());

        return requestType;
    }

    private static void validateAddress(String address) {
        boolean valid;
        try {
            valid = (address != null
                    && (InetAddresses.isInetAddress(address) || InternetDomainName.isValid(IDN.toASCII(address))));
        } catch (IllegalArgumentException e) {
            valid = false;
        }
        if (!valid) throw new CodedException(X_INVALID_REQUEST, "Invalid server address");
    }

    private static boolean verifyAuthCert(X509Certificate authCert) throws Exception {

        var instanceId = GlobalConf.getInstanceIdentifier();
        var caCert = GlobalConf.getCaCert(instanceId, authCert);
        authCert.verify(caCert.getPublicKey());
        authCert.checkValidity();

        return CertUtils.isAuthCert(authCert);
    }

    private static ClientId getClientIdFromCert(X509Certificate cert, ClientId clientId) throws Exception {
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

    private static void verifyCertificate(X509Certificate memberCert, OCSPResp memberCertOcsp) throws Exception {

        X509Certificate issuer = GlobalConf.getCaCert(GlobalConf.getInstanceIdentifier(), memberCert);

        try {
            memberCert.verify(issuer.getPublicKey());
            memberCert.checkValidity();
        } catch (GeneralSecurityException e) {
            throw new CodedException(X_CERT_VALIDATION,
                    "Member (owner/client) sign certificate is invalid: %s", e.getMessage());
        }

        if (!CertUtils.isSigningCert(memberCert)) {
            throw new CodedException(X_CERT_VALIDATION, "Member (owner/client) sign certificate is invalid");
        }

        new OcspVerifier(GlobalConf.getOcspFreshnessSeconds(false),
                new OcspVerifierOptions(GlobalConfExtensions.getInstance().shouldVerifyOcspNextUpdate()))
                .verifyValidityAndStatus(memberCertOcsp, memberCert, issuer);
    }

    private static boolean verifySignature(X509Certificate cert, byte[] signatureData,
            String signatureAlgorithmId, byte[] dataToVerify) {

        try {
            Signature signature = Signature.getInstance(signatureAlgorithmId, "BC");
            signature.initVerify(cert.getPublicKey());
            signature.update(dataToVerify);

            return signature.verify(signatureData);
        } catch (Exception e) {
            throw translateException(e);
        }
    }

    private static void validateServerId(SecurityServerId serverId) {
        if (isValidPart(serverId.getXRoadInstance())
                && isValidPart(serverId.getMemberClass())
                && isValidPart(serverId.getMemberCode())
                && isValidPart(serverId.getServerCode())) {
            return;
        }
        throw new CodedException(X_INVALID_CLIENT_IDENTIFIER, "The management request contains an invalid identifier.");
    }

    private static final CharMatcher MATCHER = CharMatcher.javaIsoControl()
            .or(CharMatcher.anyOf(":;%/\\\ufeff\u200b"));

    @SuppressWarnings("checkstyle:MagicNumber")
    private static boolean isValidPart(String part) {
        return part != null
                && !part.isEmpty()
                && part.length() <= 255
                && !MATCHER.matchesAnyOf(part);
    }

    @Getter
    static class DecoderCallback implements SoapMessageDecoder.Callback {

        private SoapMessageImpl soapMessage;
        private String serviceCode;

        private byte[] authSignature;
        private String authSignatureAlgoId;

        private byte[] ownerSignature;
        private String ownerSignatureAlgoId;

        private byte[] authCert;
        private byte[] ownerCert;
        private byte[] ownerCertOcsp;

        @Override
        public void soap(SoapMessage message, Map<String, String> additionalHeaders) {
            this.soapMessage = (SoapMessageImpl) message;
            this.serviceCode = soapMessage.getService().getServiceCode();

            if (!ManagementRequests.AUTH_CERT_REG.equalsIgnoreCase(serviceCode)) {
                throw new CodedException(X_INVALID_REQUEST, "Unknown service code '%.20s'", serviceCode);
            }
        }

        @Override
        public void attachment(String contentType, InputStream content, Map<String, String> additionalHeaders)
                throws Exception {
            if (authSignature == null) {
                authSignatureAlgoId = additionalHeaders.get(MimeUtils.HEADER_SIG_ALGO_ID);
                authSignature = IOUtils.toByteArray(content);
            } else if (ownerSignature == null) {
                ownerSignatureAlgoId = additionalHeaders.get(MimeUtils.HEADER_SIG_ALGO_ID);
                ownerSignature = IOUtils.toByteArray(content);
            } else if (authCert == null) {
                authCert = IOUtils.toByteArray(content);
            } else if (ownerCert == null) {
                ownerCert = IOUtils.toByteArray(content);
            } else if (ownerCertOcsp == null) {
                ownerCertOcsp = IOUtils.toByteArray(content);
            } else {
                throw new CodedException(X_INTERNAL_ERROR, "Unexpected content in multipart");
            }
        }

        @Override
        public void fault(SoapFault fault) {
            onError(fault.toCodedException());
        }

        @Override
        public void onCompleted() {
            verifyMessagePart(soapMessage, "Request contains no SOAP message");

            verifyMessagePart(authSignatureAlgoId, "Auth signature algorithm id is missing");
            verifyMessagePart(authSignature, "Auth signature is missing");
            verifyMessagePart(ownerSignatureAlgoId, "Owner signature algorithm id is missing");
            verifyMessagePart(ownerSignature, "Owner signature is missing");
            verifyMessagePart(authCert, "Auth certificate is missing");
            verifyMessagePart(ownerCert, "Owner certificate is missing");
            verifyMessagePart(ownerCertOcsp, "Owner certificate OCSP is missing");

        }

        @Override
        public void onError(Exception t) {
            throw translateException(t);
        }

        private static void verifyMessagePart(Object value, String message) {
            if (value == null || value instanceof String && ((String) value).isEmpty()) {
                throw new CodedException(X_INVALID_REQUEST, message);
            }
        }

    }
}
