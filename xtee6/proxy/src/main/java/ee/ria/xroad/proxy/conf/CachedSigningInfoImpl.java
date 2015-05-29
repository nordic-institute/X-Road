package ee.ria.xroad.proxy.conf;

import java.security.cert.CertificateExpiredException;
import java.security.cert.CertificateNotYetValidException;
import java.security.cert.X509Certificate;
import java.util.Date;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.bouncycastle.cert.ocsp.OCSPResp;

import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.ocsp.OcspVerifier;
import ee.ria.xroad.proxy.signedmessage.SignerSigningKey;

@Slf4j
@Getter
@RequiredArgsConstructor
final class CachedSigningInfoImpl extends AbstractCachedInfo {

    private final String keyId;
    private final ClientId clientId;
    private final X509Certificate cert;
    private final OCSPResp ocsp;

    // ------------------------------------------------------------------------

    @Override
    boolean verifyValidity(Date atDate) {
        try {
            verifyCert(atDate);
            verifyOcsp(atDate, clientId.getXRoadInstance());
            return true;
        } catch (Exception e) {
            log.warn("Cached signing info for member '{}' "
                    + "failed verification: {}", clientId, e.getMessage());
            return false;
        }
    }

    SigningCtx getSigningCtx() {
        return new SigningCtxImpl(clientId, new SignerSigningKey(keyId), cert);
    }

    // ------------------------------------------------------------------------

    private void verifyCert(Date atDate) throws CertificateExpiredException,
            CertificateNotYetValidException {
        cert.checkValidity(atDate);
    }

    private void verifyOcsp(Date atDate, String instanceIdentifier)
            throws Exception {
        X509Certificate issuer =
                GlobalConf.getCaCert(instanceIdentifier, cert);
        OcspVerifier verifier =
                new OcspVerifier(GlobalConf.getOcspFreshnessSeconds(false));
        verifier.verifyValidityAndStatus(ocsp, cert, issuer, atDate);
    }
}
