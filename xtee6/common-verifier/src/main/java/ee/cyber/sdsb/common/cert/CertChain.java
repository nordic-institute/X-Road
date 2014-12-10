package ee.cyber.sdsb.common.cert;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.conf.globalconf.GlobalConf;

import static ee.cyber.sdsb.common.ErrorCodes.X_CANNOT_CREATE_CERT_PATH;
import static ee.cyber.sdsb.common.ErrorCodes.translateWithPrefix;

/**
 * Holds the certificate chain containing the trusted root certificate,
 * any intermediate certificates and end entity certificate.
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PACKAGE)
public class CertChain {

    /** Holds the instanceIdentifier. */
    private final String instanceIdentifier;

    /** Holds the end entity certificate. */
    private final X509Certificate endEntityCert;

    /** Holds the trusted root certificate. */
    private final X509Certificate trustedRootCert;

    /** Holds any additional certificates (intermediates). */
    private List<X509Certificate> additionalCerts = new ArrayList<>();

    /**
     * Builds certificate chain form the given array of certificates
     * (ordered with the user's certificate first and the root
     * certificate authority last).
     * @param instanceIdentifier the instance identifier
     * @param chain the certificate chain
     * @return the certificate chain
     */
    public static CertChain create(String instanceIdentifier,
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
     * @param instanceIdentifier the instance identifier
     * @param cert the end entity certificate
     * @param additionalCerts additional certificates that can be used to
     * construct the cert chain.
     * @return the certificate chain
     */
    public static CertChain create(String instanceIdentifier,
            X509Certificate cert, List<X509Certificate> additionalCerts) {
        try {
            X509Certificate trustAnchor =
                    GlobalConf.getCaCert(instanceIdentifier, cert);
            if (trustAnchor == null) {
                throw new Exception("Unable to find trust anchor");
            }

            return new CertChain(instanceIdentifier, cert, trustAnchor,
                    additionalCerts != null
                        ? additionalCerts : new ArrayList<X509Certificate>());
        } catch (Exception ex) {
            throw translateWithPrefix(X_CANNOT_CREATE_CERT_PATH, ex);
        }
    }

    /**
     * @return the complete chain used to create this instance,
     * starting with the end entity and ending with trusted root. */
    public List<X509Certificate> getAllCerts() {
        List<X509Certificate> allCerts = new ArrayList<>();
        allCerts.add(getEndEntityCert());
        allCerts.addAll(getAdditionalCerts());
        allCerts.add(getTrustedRootCert());

        return allCerts;
    }

    /**
     * @return the chain used to create this instance withouth the trusted root,
     * starting with the end entity followed by any additional certs. */
    public List<X509Certificate> getAllCertsWithoutTrustedRoot() {
        List<X509Certificate> allCerts = new ArrayList<>();
        allCerts.add(getEndEntityCert());
        allCerts.addAll(getAdditionalCerts());

        return allCerts;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Trusted root certificate:\n").append(trustedRootCert);
        sb.append("\n\n");
        sb.append("Intermediate certificates:\n");
        additionalCerts.forEach(c -> sb.append(c));
        sb.append("\n\n");
        sb.append("End entity certificate:\n").append(endEntityCert);
        return sb.toString();
    }
}
