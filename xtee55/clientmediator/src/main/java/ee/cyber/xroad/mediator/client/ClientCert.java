package ee.cyber.xroad.mediator.client;

import java.security.cert.X509Certificate;

import lombok.Data;

@Data
class ClientCert {

    private final X509Certificate cert;
    private final String verificationResult;

    public boolean verificationFailed() {
        return "FAILED".equalsIgnoreCase(verificationResult);
    }

}
