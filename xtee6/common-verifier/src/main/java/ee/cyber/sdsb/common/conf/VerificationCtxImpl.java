package ee.cyber.sdsb.common.conf;

import java.security.cert.X509Certificate;
import java.util.Collection;
import java.util.Date;

import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.signature.SignatureVerifier;

class VerificationCtxImpl implements VerificationCtx {

    private final Collection<X509Certificate> caCerts;

    VerificationCtxImpl(Collection<X509Certificate> caCerts) {
        this.caCerts = caCerts;
    }

    @Override
    public void verifySslCert(X509Certificate cert) {
        // TODO If "cert" is null, is it success or failure?
        if (caCerts == null || cert == null) {
            return;
        }
        for (X509Certificate caCert : caCerts) {
            try {
                cert.verify(caCert.getPublicKey());
                return;
            } catch (Exception e) {
                // No luck, try next CA
            }
        }
        // TODO Failure
        throw new RuntimeException("Could not verify certificate");
    }

    @Override
    public String getOrganization(X509Certificate cert) {
        return "";
    }

    @Override
    public void verifySignature(ClientId sender, SignatureVerifier verifier)
            throws Exception {
        verifier.verify(sender, new Date());
    }
}
