package ee.ria.xroad.common.request;

import java.io.InputStream;
import java.security.Signature;
import java.security.cert.X509Certificate;
import java.util.Map;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.bouncycastle.cert.ocsp.OCSPResp;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.message.SoapMessage;
import ee.ria.xroad.common.message.SoapMessageDecoder;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.ocsp.OcspVerifier;
import ee.ria.xroad.common.util.MimeUtils;

import static ee.ria.xroad.common.ErrorCodes.*;
import static ee.ria.xroad.common.util.CryptoUtils.readCertificate;

/**
 * Reads management requests from input stream.
 * Verifies authentication certificate registration requests.
 */
@Slf4j
public final class ManagementRequestHandler {

    private ManagementRequestHandler() {
    }

    /**
     * Reads management requests from input stream.
     * @param contentType expected content type of the stream
     * @param inputStream the input stream
     * @return management request SOAP message
     * @throws Exception in case of any errors
     */
    public static SoapMessageImpl readRequest(String contentType,
            InputStream inputStream) throws Exception {
        log.info("readRequest(contentType={})", contentType);

        DecoderCallback cb = new DecoderCallback();

        SoapMessageDecoder decoder = new SoapMessageDecoder(contentType, cb);
        decoder.parse(inputStream);

        return cb.getSoapMessage();
    }

    private static void verifyAuthCertRegRequest(DecoderCallback cb)
            throws Exception {
        log.info("verifyAuthCertRegRequest");

        SoapMessageImpl soap = cb.getSoapMessage();
        byte[] dataToVerify = soap.getBytes();

        log.info("Verifying auth signature");

        X509Certificate authCert = readCertificate(cb.getAuthCert());
        if (!verifySignature(authCert, cb.getAuthSignature(),
                cb.getAuthSignatureAlgoId(), dataToVerify)) {
            throw new CodedException(X_INVALID_SIGNATURE_VALUE,
                    "Auth signature verification failed");
        }

        log.info("Verifying owner signature");

        X509Certificate ownerCert = readCertificate(cb.getOwnerCert());
        if (!verifySignature(ownerCert, cb.getOwnerSignature(),
                cb.getOwnerSignatureAlgoId(), dataToVerify)) {
            throw new CodedException(X_INVALID_SIGNATURE_VALUE,
                    "Owner signature verification failed");
        }

        log.info("Verifying owner certificate");

        OCSPResp ownerCertOcsp = new OCSPResp(cb.getOwnerCertOcsp());
        verifyCertificate(ownerCert, ownerCertOcsp);
    }

    private static void verifyCertificate(X509Certificate ownerCert,
            OCSPResp ownerCertOcsp) throws Exception {
        try {
            ownerCert.checkValidity();
        } catch (Exception e) {
            throw new CodedException(X_CERT_VALIDATION,
                    "Owner certificate is invalid: %s", e.getMessage());
        }

        X509Certificate issuer =
                GlobalConf.getCaCert(GlobalConf.getInstanceIdentifier(),
                        ownerCert);
        new OcspVerifier(GlobalConf.getOcspFreshnessSeconds(false))
            .verifyValidityAndStatus(ownerCertOcsp, ownerCert, issuer);
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
            log.error("Failed to verify signature", e);
            throw translateException(e);
        }
    }

    @Getter
    static class DecoderCallback implements SoapMessageDecoder.Callback {

        private SoapMessageImpl soapMessage;

        private byte[] authSignature;
        private String authSignatureAlgoId;

        private byte[] ownerSignature;
        private String ownerSignatureAlgoId;

        private byte[] authCert;
        private byte[] ownerCert;
        private byte[] ownerCertOcsp;

        @Override
        public void soap(SoapMessage message) throws Exception {
            this.soapMessage = (SoapMessageImpl) message;
        }

        @Override
        public void attachment(String contentType, InputStream content,
                Map<String, String> additionalHeaders) throws Exception {
            if (authSignature == null) {
                log.info("Reading auth signature");

                authSignatureAlgoId =
                        additionalHeaders.get(MimeUtils.HEADER_SIG_ALGO_ID);
                authSignature = IOUtils.toByteArray(content);
            } else if (ownerSignature == null) {
                log.info("Reading security server owner signature");

                ownerSignatureAlgoId =
                        additionalHeaders.get(MimeUtils.HEADER_SIG_ALGO_ID);
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
            log.info("Service name: {}", service);

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

                verifyMessagePart(ownerCertOcsp,
                        "Owner certificate OCSP is missing");

                try {
                    verifyAuthCertRegRequest(this);
                } catch (Exception e) {
                    log.error("Failed to verify auth cert reg request", e);
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
                    || value instanceof String
                            && ((String) value).isEmpty()) {
                throw new CodedException(X_INVALID_REQUEST, message);
            }
        }
    }
}
