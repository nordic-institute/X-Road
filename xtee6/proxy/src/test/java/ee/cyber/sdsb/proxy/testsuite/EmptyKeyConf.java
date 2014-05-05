package ee.cyber.sdsb.proxy.testsuite;

import java.io.OutputStream;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import java.util.Collections;
import java.util.List;

import ee.cyber.sdsb.common.conf.AuthKey;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.proxy.conf.KeyConfProvider;
import ee.cyber.sdsb.proxy.conf.SigningCtx;

public class EmptyKeyConf implements KeyConfProvider {

    @Override
    public boolean hasChanged() {
        return false;
    }

    @Override
    public SigningCtx getSigningCtx(ClientId memberId) {
        return null;
    }

    @Override
    public List<X509Certificate> getMemberCerts(ClientId memberId)
            throws Exception {
        return Collections.emptyList();
    }

    @Override
    public AuthKey getAuthKey() {
        return null;
    }

    @Override
    public X509Certificate getOcspSignerCert() throws Exception {
        return null;
    }

    @Override
    public PrivateKey getOcspRequestKey(X509Certificate org) throws Exception {
        return null;
    }

    @Override
    public void save() throws Exception {
    }

    @Override
    public void save(OutputStream out) throws Exception {
    }

    @Override
    public void load(String fileName) throws Exception {
    }
}
