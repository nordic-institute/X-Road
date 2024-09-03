package ee.ria.xroad.common.cert;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;

import lombok.RequiredArgsConstructor;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static ee.ria.xroad.common.ErrorCodes.X_CANNOT_CREATE_CERT_PATH;
import static ee.ria.xroad.common.ErrorCodes.translateWithPrefix;

@RequiredArgsConstructor
public class CertChainFactory {
    private final GlobalConfProvider globalConfProvider;

    /**
     * Builds certificate chain form the given array of certificates
     * (ordered with the user's certificate first and the root
     * certificate authority last).
     *
     * @param instanceIdentifier the instance identifier
     * @param chain              the certificate chain
     * @return the certificate chain
     */
    public CertChain create(String instanceIdentifier,
                            X509Certificate[] chain) {
        if (chain.length < 2) {
            throw new CodedException(X_CANNOT_CREATE_CERT_PATH,
                    "Chain must have at least user's certificate "
                            + "and root certificate authority");
        }

        X509Certificate trustAnchor = chain[chain.length - 1];
        List<X509Certificate> additionalCerts = new ArrayList<>();
        if (chain.length > 2) {
            additionalCerts.addAll(Arrays.asList(
                    Arrays.copyOfRange(chain, 1, chain.length - 1)));
        }

        try {
            return new CertChain(instanceIdentifier, chain[0], trustAnchor,
                    additionalCerts);
        } catch (Exception ex) {
            throw translateWithPrefix(X_CANNOT_CREATE_CERT_PATH, ex);
        }
    }

    /**
     * Builds certificate chain from cert to trusted root.
     *
     * @param instanceIdentifier the instance identifier
     * @param cert               the end entity certificate
     * @param additionalCerts    additional certificates that can be used to
     *                           construct the cert chain.
     * @return the certificate chain
     */
    public CertChain create(String instanceIdentifier,
                            X509Certificate cert, List<X509Certificate> additionalCerts) {
        try {
            X509Certificate trustAnchor =
                    globalConfProvider.getCaCert(instanceIdentifier, cert);
            return new CertChain(instanceIdentifier, cert, trustAnchor,
                    additionalCerts != null
                            ? additionalCerts : new ArrayList<X509Certificate>());
        } catch (Exception ex) {
            throw translateWithPrefix(X_CANNOT_CREATE_CERT_PATH, ex);
        }
    }
}
