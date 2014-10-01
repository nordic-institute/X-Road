package ee.cyber.sdsb.common.conf.serverconf;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;

import lombok.Data;

@Data
public class InternalSSLKey {

    public static final String KEY_FILE_NAME = "ssl/internal.p12";
    public static final String KEY_ALIAS = "internal";
    public static final char[] KEY_PASSWORD = "internal".toCharArray();

    private final PrivateKey key;
    private final X509Certificate cert;

}
