package ee.cyber.sdsb.common.request;

import java.io.InputStream;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.message.SoapFault;
import ee.cyber.sdsb.common.message.SoapMessage;
import ee.cyber.sdsb.common.message.SoapMessageDecoder;
import ee.cyber.sdsb.common.message.SoapMessageImpl;
import ee.cyber.sdsb.common.util.MimeUtils;

import static ee.cyber.sdsb.common.ErrorCodes.*;
import static ee.cyber.sdsb.common.util.CryptoUtils.readCertificate;

/**
 * Reads management requests from input stream.
 * Verifies authentication certificate registration requests.
 */
public class ManagementRequestHandler {

    private static final Logger LOG =
            LoggerFactory.getLogger(ManagementRequestHandler.class);

    public static SoapMessageImpl readRequest(String contentType,
            InputStream inputStream) throws Exception {
        LOG.info("readRequest(contentType={})", contentType);

        DecoderCallback cb = new DecoderCallback();

        SoapMessageDecoder decoder = new SoapMessageDecoder(contentType, cb);
        decoder.parse(inputStream);

        return cb.getMessage();
    }

    private static void verifyAuthCertRegRequest(DecoderCallback cb)
            throws Exception {
        LOG.info("verifyAuthCertRegRequest");

        SoapMessageImpl soap = cb.getMessage();
        byte[] dataToVerify = soap.getBytes();

        LOG.info("Verifying auth signature");

        X509Certificate authCert = readCertificate(cb.getAuthCert());
        if (!verifySignature(authCert, cb.getAuthSignature(),
                cb.getAuthSignatureAlgoId(), dataToVerify)) {
            throw new CodedException(X_INVALID_SIGNATURE_VALUE,
                    "Auth signature verification failed");
        }

        LOG.info("Verifying owner signature");

        X509Certificate ownerCert = readCertificate(cb.getOwnerCert());
        if (!verifySignature(ownerCert, cb.getOwnerSignature(),
                cb.getOwnerSignatureAlgoId(), dataToVerify)) {
            throw new CodedException(X_INVALID_SIGNATURE_VALUE,
                    "Owner signature verification failed");
        }
    }

    private static boolean verifySignature(X509Certificate cert,
            byte[] signatureData, String algorithmId, byte[] dataToVerify)
                    throws Exception {
        try {
            Signature signature = Signature.getInstance(algorithmId);
            signature.initVerify(cert.getPublicKey());
            signature.update(dataToVerify);

            return signature.verify(signatureData);
        } catch (Exception e) {
            LOG.error("Failed to verify signature", e);
            throw translateException(e);
        }
    }

    static class DecoderCallback implements SoapMessageDecoder.Callback {

        private SoapMessageImpl soapMessage;

        private byte[] authSignature;
        private String authSignatureAlgoId;

        private byte[] ownerSignature;
        private String ownerSignatureAlgoId;

        private byte[] authCert;
        private byte[] ownerCert;

        SoapMessageImpl getMessage() {
            return soapMessage;
        }

        public byte[] getAuthSignature() {
            return authSignature;
        }

        public String getAuthSignatureAlgoId() {
            return authSignatureAlgoId;
        }

        public byte[] getOwnerSignature() {
            return ownerSignature;
        }

        public String getOwnerSignatureAlgoId() {
            return ownerSignatureAlgoId;
        }

        public byte[] getAuthCert() {
            return authCert;
        }

        public byte[] getOwnerCert() {
            return ownerCert;
        }

        @Override
        public void soap(SoapMessage message) throws Exception {
            this.soapMessage = (SoapMessageImpl) message;
        }

        @Override
        public void attachment(String contentType, InputStream content,
                Map<String, String> additionalHeaders) throws Exception {
            if (authSignature == null) {
                LOG.info("Reading auth signature");

                authSignatureAlgoId =
                        additionalHeaders.get(MimeUtils.HEADER_SIG_ALGO_ID);
                authSignature = IOUtils.toByteArray(content);
            } else if (ownerSignature == null) {
                LOG.info("Reading security server owner signature");

                ownerSignatureAlgoId =
                        additionalHeaders.get(MimeUtils.HEADER_SIG_ALGO_ID);
                ownerSignature = IOUtils.toByteArray(content);
            } else if (authCert == null) {
                LOG.info("Reading auth cert");

                authCert = IOUtils.toByteArray(content);
            } else if (ownerCert == null) {
                LOG.info("Reading owner cert");

                ownerCert = IOUtils.toByteArray(content);
            } else {
                throw new CodedException(X_INTERNAL_ERROR,
                        "Unexpected content in multipart");
            }
        }

        @Override
        public void fault(SoapFault fault) throws Exception {
            onError(fault.toCodedException());
        }

        @Override
        public void onCompleted() {
            verifyMessagePart(soapMessage, "Request contains no SOAP message");

            String service = soapMessage.getService().getServiceCode();
            LOG.info("Service name: {}", service);

            if (ManagementRequests.AUTH_CERT_REG.equalsIgnoreCase(service)) {
                verifyMessagePart(authSignatureAlgoId,
                        "Auth signature algorightm id is missing");

                verifyMessagePart(authSignature,
                        "Auth signature is missing");

                verifyMessagePart(ownerSignatureAlgoId,
                        "Owner signature algorightm id is missing");

                verifyMessagePart(ownerSignature,
                        "Owner signature is missing");

                verifyMessagePart(authCert,
                        "Auth certificate is missing");

                verifyMessagePart(ownerCert,
                        "Owner certificate is missing");

                try {
                    verifyAuthCertRegRequest(this);
                } catch (Exception e) {
                    LOG.error("Failed to verify auth cert reg request", e);
                    throw translateException(e);
                }
            }
        }

        @Override
        public void onError(Exception t) throws Exception {
            throw translateException(t);
        }

        private static void verifyMessagePart(Object value, String message) {
            if (value == null
                    || (value instanceof String
                            && ((String) value).isEmpty())) {
                throw new CodedException(X_INVALID_REQUEST, message);
            }
        }
    }
}
