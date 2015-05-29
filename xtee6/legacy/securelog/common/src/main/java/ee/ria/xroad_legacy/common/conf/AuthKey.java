package ee.ria.xroad_legacy.common.conf;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;

public class AuthKey {

    private final X509Certificate authCert;
    private final PrivateKey authKey;

    public AuthKey(X509Certificate cert, PrivateKey key) {
        authCert = cert;
        authKey = key;
    }

    public X509Certificate getCert() {
        return authCert;
    }

    public PrivateKey getKey() {
        return authKey;
    }

}
