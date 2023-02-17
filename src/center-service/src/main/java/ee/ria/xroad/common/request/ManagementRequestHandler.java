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
package ee.ria.xroad.common.request;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.certificateprofile.impl.SignCertificateProfileInfoParameters;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.globalconfextension.GlobalConfExtensions;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.message.SoapMessage;
import ee.ria.xroad.common.message.SoapMessageDecoder;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.ocsp.OcspVerifier;
import ee.ria.xroad.common.ocsp.OcspVerifierOptions;
import ee.ria.xroad.common.util.MimeUtils;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.bouncycastle.cert.ocsp.OCSPResp;

import java.io.InputStream;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.Map;

import static ee.ria.xroad.common.ErrorCodes.X_CERT_VALIDATION;
import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_REQUEST;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_SIGNATURE_VALUE;
import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.request.ManagementRequests.CLIENT_REG;
import static ee.ria.xroad.common.request.ManagementRequests.OWNER_CHANGE;
import static ee.ria.xroad.common.util.CryptoUtils.readCertificate;

/**
 * Reads management requests from input stream.
 * Verifies authentication certificate and client registration requests.
 */
@Slf4j
public final class ManagementRequestHandler {

    private static final String DUMMY_CLIENT_ID = "dummy";

    private ManagementRequestHandler() {
    }

    /**
     * Reads management requests from input stream.
     *
     * @param contentType expected content type of the stream
     * @param inputStream the input stream
     * @return management request SOAP message
     * @throws Exception in case of any errors
     */
    public static SoapMessageImpl readRequest(String contentType,
                                              InputStream inputStream) throws Exception {
        return readRequest(contentType, inputStream, new ClientRegRequestStatusWrapper());
    }

    /**
     * Reads management requests from input stream.
     *
     * @param contentType            expected content type of the stream
     * @param inputStream            the input stream
     * @param clientRegRequestStatusWrapper helper for processing clientRegRequests
     * @return management request SOAP message
     * @throws Exception in case of any errors
     */
    public static SoapMessageImpl readRequest(String contentType,
                                              InputStream inputStream,
                                              ClientRegRequestStatusWrapper clientRegRequestStatusWrapper)
            throws Exception {
        log.info("readRequest(contentType={})", contentType);

        DecoderCallback cb = new DecoderCallback();

        SoapMessageDecoder decoder = new SoapMessageDecoder(contentType, cb);
        decoder.parse(inputStream);

        clientRegRequestStatusWrapper.setClientRegRequestSignedAndVerified(cb.getClientRegRequestSignedAndVerified());

        return cb.getSoapMessage();
    }

    private static void verifyAuthCertRegRequest(DecoderCallback cb) throws Exception {
        log.info("verifyAuthCertRegRequest");

        SoapMessageImpl soap = cb.getSoapMessage();
        byte[] dataToVerify = soap.getBytes();

        log.info("Verifying auth signature");

        X509Certificate authCert = readCertificate(cb.getAuthCert());

        if (!verifySignature(authCert, cb.getAuthSignature(), cb.getAuthSignatureAlgoId(), dataToVerify)) {
            throw new CodedException(X_INVALID_SIGNATURE_VALUE, "Auth signature verification failed");
        }

        log.info("Verifying owner signature");

        X509Certificate ownerCert = readCertificate(cb.getOwnerCert());

        if (!verifySignature(ownerCert, cb.getOwnerSignature(), cb.getOwnerSignatureAlgoId(), dataToVerify)) {
            throw new CodedException(X_INVALID_SIGNATURE_VALUE, "Owner signature verification failed");
        }

        log.info("Verifying owner certificate");

        OCSPResp ownerCertOcsp = new OCSPResp(cb.getOwnerCertOcsp());
        verifyCertificate(ownerCert, ownerCertOcsp);

        // verify that the subject id from the certificate matches the one
        // in the request (server id)
        AuthCertRegRequestType req = ManagementRequestParser.parseAuthCertRegRequest(soap);

        ClientId idFromCert = getClientIdFromCert(ownerCert);

        ClientId idFromReq = req.getServer().getOwner();

        if (!idFromReq.equals(idFromCert)) {
            throw new CodedException(X_INVALID_REQUEST,
                    "Subject identifier (%s) in certificate does not match"
                            + " security server owner identifier (%s) in request",
                    idFromCert, idFromReq);
        }
    }

    private static void verifyClientRegRequest(DecoderCallback cb) throws Exception {
        log.info("verifyClientRegRequest");
        verifyClientRequestTypeRequest(cb, CLIENT_REG);
    }

    private static void verifyOwnerChangeRequest(DecoderCallback cb) throws Exception {
        log.info("verifyOwnerChangeRequest");
        verifyClientRequestTypeRequest(cb, OWNER_CHANGE);
    }

    private static void verifyClientRequestTypeRequest(DecoderCallback cb, String managementRequestName)
            throws Exception {
        SoapMessageImpl soap = cb.getSoapMessage();
        byte[] dataToVerify = soap.getBytes();

        log.info("Verifying client signature");

        X509Certificate clientCert = readCertificate(cb.getClientCert());

        if (!verifySignature(clientCert, cb.getClientSignature(), cb.getClientSignatureAlgoId(), dataToVerify)) {
            throw new CodedException(X_INVALID_SIGNATURE_VALUE, "Client signature verification failed");
        }

        log.info("Verifying client certificate");

        OCSPResp clientCertOcsp = new OCSPResp(cb.getClientCertOcsp());
        verifyCertificate(clientCert, clientCertOcsp);

        // Verify that the subject id from the certificate matches the one
        // in the request (client). The certificate must belong to the member
        // that is used as a client.
        ClientRequestType req = ManagementRequestParser.parseRequest(soap, managementRequestName);

        ClientId idFromCert = getClientIdFromCert(clientCert);

        ClientId idFromReq = req.getClient();

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
                        ClientId.create(
                                GlobalConf.getInstanceIdentifier(),
                                DUMMY_CLIENT_ID,
                                DUMMY_CLIENT_ID
                        ),
                        DUMMY_CLIENT_ID
                ),
                cert
        );
    }

    private static void verifyCertificate(X509Certificate memberCert, OCSPResp memberCertOcsp) throws Exception {
        try {
            memberCert.checkValidity();
        } catch (Exception e) {
            throw new CodedException(X_CERT_VALIDATION, "Member (owner/client) sign certificate is invalid: %s",
                    e.getMessage());
        }

        X509Certificate issuer = GlobalConf.getCaCert(GlobalConf.getInstanceIdentifier(), memberCert);
        new OcspVerifier(GlobalConf.getOcspFreshnessSeconds(),
                new OcspVerifierOptions(GlobalConfExtensions.getInstance().shouldVerifyOcspNextUpdate()))
                .verifyValidityAndStatus(memberCertOcsp, memberCert, issuer);
    }

    private static boolean verifySignature(X509Certificate cert,
                                           byte[] signatureData, String signatureAlgorithmId, byte[] dataToVerify)
            throws Exception {
        try {
            Signature signature = Signature.getInstance(signatureAlgorithmId, "BC");
            signature.initVerify(cert.getPublicKey());
            signature.update(dataToVerify);

            return signature.verify(signatureData);
        } catch (Exception e) {
            log.error("Failed to verify signature", e);

            throw translateException(e);
        }
    }

    @Getter
    static class DecoderCallback implements SoapMessageDecoder.Callback {

        private SoapMessageImpl soapMessage;
        private String serviceCode;

        private byte[] authSignature;
        private String authSignatureAlgoId;

        private byte[] ownerSignature;
        private String ownerSignatureAlgoId;

        private byte[] clientSignature;
        private String clientSignatureAlgoId;

        private byte[] authCert;
        private byte[] ownerCert;
        private byte[] ownerCertOcsp;

        private byte[] clientCert;
        private byte[] clientCertOcsp;

        private boolean clientRegRequestSignedAndVerified = false;

        @Override
        public void soap(SoapMessage message, Map<String, String> additionalHeaders) throws Exception {
            this.soapMessage = (SoapMessageImpl) message;
            this.serviceCode = soapMessage.getService().getServiceCode();
        }

        @Override
        public void attachment(String contentType, InputStream content, Map<String, String> additionalHeaders)
                throws Exception {
            if (ManagementRequests.AUTH_CERT_REG.equalsIgnoreCase(serviceCode)) {
                if (authSignature == null) {
                    log.info("Reading auth signature");

                    authSignatureAlgoId = additionalHeaders.get(MimeUtils.HEADER_SIG_ALGO_ID);
                    authSignature = IOUtils.toByteArray(content);
                } else if (ownerSignature == null) {
                    log.info("Reading security server owner signature");

                    ownerSignatureAlgoId = additionalHeaders.get(MimeUtils.HEADER_SIG_ALGO_ID);
                    ownerSignature = IOUtils.toByteArray(content);
                } else if (authCert == null) {
                    log.info("Reading auth cert");

                    authCert = IOUtils.toByteArray(content);
                } else if (ownerCert == null) {
                    log.info("Reading owner cert");

                    ownerCert = IOUtils.toByteArray(content);
                } else if (ownerCertOcsp == null) {
                    log.info("Reading owner cert OCSP");

                    ownerCertOcsp = IOUtils.toByteArray(content);
                } else {
                    throw new CodedException(X_INTERNAL_ERROR, "Unexpected content in multipart");
                }
            } else if (ManagementRequests.CLIENT_REG.equalsIgnoreCase(serviceCode)
                    || ManagementRequests.OWNER_CHANGE.equalsIgnoreCase(serviceCode)) {
                if (clientSignature == null) {
                    log.info("Reading client signature");

                    clientSignatureAlgoId = additionalHeaders.get(MimeUtils.HEADER_SIG_ALGO_ID);
                    clientSignature = IOUtils.toByteArray(content);
                } else if (clientCert == null) {
                    log.info("Reading client cert");

                    clientCert = IOUtils.toByteArray(content);
                } else if (clientCertOcsp == null) {
                    log.info("Reading client cert OCSP");

                    clientCertOcsp = IOUtils.toByteArray(content);
                } else {
                    throw new CodedException(X_INTERNAL_ERROR, "Unexpected content in multipart");
                }
            }
        }

        @Override
        public void fault(SoapFault fault) throws Exception {
            onError(fault.toCodedException());
        }

        @Override
        public void onCompleted() {
            verifyMessagePart(soapMessage, "Request contains no SOAP message");

            log.info("Service name: {}", serviceCode);

            if (ManagementRequests.AUTH_CERT_REG.equalsIgnoreCase(serviceCode)) {
                verifyMessagePart(authSignatureAlgoId, "Auth signature algorithm id is missing");

                verifyMessagePart(authSignature, "Auth signature is missing");

                verifyMessagePart(ownerSignatureAlgoId, "Owner signature algorithm id is missing");

                verifyMessagePart(ownerSignature, "Owner signature is missing");

                verifyMessagePart(authCert, "Auth certificate is missing");

                verifyMessagePart(ownerCert, "Owner certificate is missing");

                verifyMessagePart(ownerCertOcsp, "Owner certificate OCSP is missing");

                try {
                    verifyAuthCertRegRequest(this);
                } catch (Exception e) {
                    log.error("Failed to verify auth cert reg request", e);

                    throw translateException(e);
                }
            } else if (ManagementRequests.CLIENT_REG.equalsIgnoreCase(serviceCode) && mustVerifyClientRegRequest()) {
                verifyMessagePart(clientSignature, "Client signature is missing");

                verifyMessagePart(clientCert, "Client certificate is missing");

                verifyMessagePart(clientCertOcsp, "Client certificate OCSP is missing");

                try {
                    verifyClientRegRequest(this);
                    clientRegRequestSignedAndVerified = true;
                } catch (Exception e) {
                    log.error("Failed to verify client reg request", e);

                    throw translateException(e);
                }
            } else if (ManagementRequests.OWNER_CHANGE.equalsIgnoreCase(serviceCode)) {

                verifyMessagePart(clientSignature, "Client signature is missing");

                verifyMessagePart(clientCert, "Client certificate is missing");

                verifyMessagePart(clientCertOcsp, "Client certificate OCSP is missing");

                try {
                    verifyOwnerChangeRequest(this);
                } catch (Exception e) {
                    log.error("Failed to verify owner change request", e);

                    throw translateException(e);
                }
            }
        }

        @Override
        public void onError(Exception t) throws Exception {
            throw translateException(t);
        }

        public boolean getClientRegRequestSignedAndVerified() {
            return this.clientRegRequestSignedAndVerified;
        }

        private static void verifyMessagePart(Object value, String message) {
            if (value == null || value instanceof String && ((String) value).isEmpty()) {
                throw new CodedException(X_INVALID_REQUEST, message);
            }
        }

        /**
         * This helper method is used for detecting clientRegRequests generated by Security Server versions >=6.21.0.
         * If clientRegRequest is generated by Security Server version >=6.21.0, it contains client signature, cert
         * and OCSP response, and therefore, it must be verified. Requests generated by older Security Server versions
         * do not contain this information and for backwards compatibility verification must be skipped.
         *
         * @return if clientSignature, clientCert or clientCertOcsp have been set, true is returned; otherwise false
         */
        private boolean mustVerifyClientRegRequest() {
            return (clientSignature != null || clientCert != null || clientCertOcsp != null);
        }
    }
}
