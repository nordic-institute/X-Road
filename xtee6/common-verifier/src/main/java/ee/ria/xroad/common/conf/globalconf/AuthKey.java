package ee.ria.xroad.common.conf.globalconf;

import java.security.PrivateKey;

import lombok.Value;

import ee.ria.xroad.common.cert.CertChain;

/**
 * Value object representing the authentication key of the security server
 * It consists of the certificate chain and private key.
 */
@Value
public class AuthKey {

    private final CertChain certChain;
    private final PrivateKey key;

}
