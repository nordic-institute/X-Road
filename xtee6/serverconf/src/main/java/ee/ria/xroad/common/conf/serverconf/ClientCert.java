package ee.ria.xroad.common.conf.serverconf;

import java.security.cert.X509Certificate;

import lombok.Data;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.util.CryptoUtils;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;

/**
 * Encapsulates the client IS certificate.
 */
@Data
public class ClientCert {

    private final X509Certificate cert;
    private final String verificationResult;

    /**
     * @return true, if verification of the certificate failed
     */
    public boolean verificationFailed() {
        return "FAILED".equalsIgnoreCase(verificationResult);
    }

    /**
     * Creates the client certificate object.
     * @param certVerify the verification result
     * @param certBase64 the base64 encoded certificate
     * @return the client cert
     */
    public static ClientCert fromParameters(String certVerify,
            String certBase64) {
        X509Certificate cert = null;
        if (certBase64 != null && !certBase64.isEmpty()) {
            try {
                certBase64 = certBase64.replaceAll(
                        "-----(BEGIN|END) CERTIFICATE-----", "");
                cert = CryptoUtils.readCertificate(certBase64);
            } catch (Exception e) {
                throw new CodedException(X_INTERNAL_ERROR,
                        "Could not read client certificate from request: %s",
                        e.getMessage());
            }
        }

        return new ClientCert(cert, certVerify);
    }
}
