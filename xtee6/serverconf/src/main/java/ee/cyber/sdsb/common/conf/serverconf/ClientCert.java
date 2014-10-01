package ee.cyber.sdsb.common.conf.serverconf;

import java.security.cert.X509Certificate;

import lombok.Data;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.util.CryptoUtils;

import static ee.cyber.sdsb.common.ErrorCodes.X_INTERNAL_ERROR;

@Data
public class ClientCert {

    private final X509Certificate cert;
    private final String verificationResult;

    public boolean verificationFailed() {
        return "FAILED".equalsIgnoreCase(verificationResult);
    }

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
