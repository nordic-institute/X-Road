package ee.cyber.sdsb.common.cert;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.conf.GlobalConf;

import static ee.cyber.sdsb.common.ErrorCodes.X_CANNOT_CREATE_CERT_PATH;
import static ee.cyber.sdsb.common.ErrorCodes.translateWithPrefix;

public class CertChain {

    /** Holds the end entity certificate. */
    private final X509Certificate cert;

    /** Holds the trusted root certificate. */
    private final X509Certificate trustedCert;

    /** Holds any additional certificates (intermediates). */
    private final List<X509Certificate> additionalCerts;

    /**
     * Builds certificate chain form the given array of certificates
     * (ordered with the user's certificate first and the root
     * certificate authority last).
     * @param chain the certificate chain
     */
    public static CertChain create(X509Certificate[] chain) {
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
            return new CertChain(chain[0], trustAnchor, additionalCerts);
        } catch (Exception ex) {
            throw translateWithPrefix(X_CANNOT_CREATE_CERT_PATH, ex);
        }
    }

    /**
     * Builds certificate chain from cert to trusted root.
     * @param additionalCerts additional certificates that can be used to
     *                   construct the cert chain.
     */
    public static CertChain create(X509Certificate cert,
            List<X509Certificate> additionalCerts) {
        try {
            X509Certificate trustAnchor = GlobalConf.getCaCert(cert);
            if (trustAnchor == null) {
                throw new Exception("Unable to find trust anchor");
            }

            return new CertChain(cert, trustAnchor, additionalCerts);
        } catch (Exception ex) {
            throw translateWithPrefix(X_CANNOT_CREATE_CERT_PATH, ex);
        }
    }

    /**
     * Builds the certificate path for the target certificate using a list
     * of trust anchors and a list of intermediate certificates.
     * @param cert the target certificate
     * @param trustedCerts the list of trust anchors
     * @param additionalCerts a list of intermediate certificates
     */
    CertChain(X509Certificate cert, X509Certificate trustedCert,
            List<X509Certificate> additionalCerts) throws Exception {
        this.cert = cert;
        this.trustedCert = trustedCert;
        this.additionalCerts = additionalCerts != null
                ? additionalCerts : new ArrayList<X509Certificate>();
    }

    /** Returns the end entity certificate. */
    public X509Certificate getEndEntityCert() {
        return cert;
    }

    /** Returns the trusted root certificate. */
    public X509Certificate getTrustedRootCert() {
        return trustedCert;
    }

    /** Returns the additional certificates (intermediates). */
    public List<X509Certificate> getAdditionalCerts() {
        return additionalCerts;
    }

    /** Returns the complete chain used to create this instance,
     * starting with the end entity and ending with trusted root. */
    public List<X509Certificate> getAllCerts() {
        List<X509Certificate> allCerts = new ArrayList<>();
        allCerts.add(getEndEntityCert());
        allCerts.addAll(getAdditionalCerts());
        allCerts.add(getTrustedRootCert());

        return allCerts;
    }

    /** Returns the chain used to create this instance withouth the trusted root,
     * starting with the end entity followed by any additional certs. */
    public List<X509Certificate> getAllCertsWithoutTrustedRoot() {
        List<X509Certificate> allCerts = new ArrayList<>();
        allCerts.add(getEndEntityCert());
        allCerts.addAll(getAdditionalCerts());

        return allCerts;
    }
}
