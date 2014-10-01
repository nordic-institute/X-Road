package ee.cyber.sdsb.proxy.testsuite;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import org.bouncycastle.cert.ocsp.OCSPResp;

import ee.cyber.sdsb.common.conf.AuthKey;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.proxy.conf.KeyConfProvider;
import ee.cyber.sdsb.proxy.conf.SigningCtx;

public class EmptyKeyConf implements KeyConfProvider {

    @Override
    public SigningCtx getSigningCtx(ClientId memberId) {
        return null;
    }

    @Override
    public AuthKey getAuthKey() {
        return null;
    }

    @Override
    public void setOcspResponses(List<X509Certificate> certs,
            List<OCSPResp> response) throws Exception {
    }

    @Override
    public OCSPResp getOcspResponse(X509Certificate cert) throws Exception {
        return null;
    }

    @Override
    public OCSPResp getOcspResponse(String certHash) throws Exception {
        return null;
    }

    @Override
    public List<OCSPResp> getOcspResponses(List<X509Certificate> certs)
            throws Exception {
        List<OCSPResp> ocspResponses = new ArrayList<>();
        for (X509Certificate cert : certs) {
            ocspResponses.add(getOcspResponse(cert));
        }

        return ocspResponses;
    }

}
