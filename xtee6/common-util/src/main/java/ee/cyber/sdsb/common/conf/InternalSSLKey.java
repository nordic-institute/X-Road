package ee.cyber.sdsb.common.conf;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import lombok.Data;

@Data
public class InternalSSLKey {

    public static final String KEY_FILE_NAME = "sslkey.p12";
    public static final String KEY_ALIAS = "sslkey";
    public static final char[] KEY_PASSWORD = "sslkey".toCharArray();

    private final PrivateKey key;
    private final X509Certificate cert;

}
