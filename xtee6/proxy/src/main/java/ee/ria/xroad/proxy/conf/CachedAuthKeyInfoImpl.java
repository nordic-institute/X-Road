package ee.ria.xroad.proxy.conf;

import java.security.PrivateKey;
import java.util.Date;
import java.util.List;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.bouncycastle.cert.ocsp.OCSPResp;

import ee.ria.xroad.common.cert.CertChain;
import ee.ria.xroad.common.cert.CertChainVerifier;
import ee.ria.xroad.common.conf.globalconf.AuthKey;

@Slf4j
@Getter
@RequiredArgsConstructor
class CachedAuthKeyInfoImpl extends AbstractCachedInfo {

    private final PrivateKey pkey;
    private final CertChain certChain;
    private final List<OCSPResp> ocspResponses;

    AuthKey getAuthKey() {
        return new AuthKey(certChain, pkey);
    }

    @Override
    boolean verifyValidity(Date atDate) {
        try {
            CertChainVerifier verifier = new CertChainVerifier(certChain);
            verifier.verify(ocspResponses, atDate);
            return true;
        } catch (Exception e) {
            log.warn("Cached authentication info failed verification: {}",
                    e.getMessage());
            return false;
        }
    }

}
