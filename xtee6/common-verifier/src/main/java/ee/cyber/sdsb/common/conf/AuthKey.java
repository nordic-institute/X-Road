package ee.cyber.sdsb.common.conf;

import java.security.PrivateKey;

import lombok.Value;

import ee.cyber.sdsb.common.cert.CertChain;

@Value
public class AuthKey {

    private final CertChain certChain;
    private final PrivateKey key;

}
