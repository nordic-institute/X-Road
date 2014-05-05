package ee.cyber.sdsb.proxy.conf;

import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.List;

import ee.cyber.sdsb.common.conf.AuthKey;
import ee.cyber.sdsb.common.conf.ConfProvider;
import ee.cyber.sdsb.common.identifier.ClientId;

public interface KeyConfProvider extends ConfProvider {

    /** Returns security (signing) context for given member. */
    SigningCtx getSigningCtx(ClientId memberId);

    /** Returns certificates of a member. */
    List<X509Certificate> getMemberCerts(ClientId memberId) throws Exception;

    /** Returns the current key and certificate for SSL authentication. */
    AuthKey getAuthKey();

    /** Returns the certificate used for signing OCSP requests. */
    X509Certificate getOcspSignerCert() throws Exception;

    /** Returns the key used for signing OCSP requests. */
    PrivateKey getOcspRequestKey(X509Certificate member) throws Exception;


}
