package ee.cyber.sdsb.proxy.conf;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import org.bouncycastle.cert.ocsp.OCSPResp;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.cert.CertChain;
import ee.cyber.sdsb.common.conf.GlobalConf;
import ee.cyber.sdsb.common.signature.SignatureBuilder;
import ee.cyber.sdsb.common.signature.SignatureData;
import ee.cyber.sdsb.common.util.CryptoUtils;
import ee.cyber.sdsb.proxy.signedmessage.SigningKey;

import static ee.cyber.sdsb.common.ErrorCodes.X_CANNOT_CREATE_SIGNATURE;

/**
 * Encapsulates security-related parameters of a given member,
 * such as currently used signing key and cert.
 */
public class SigningCtxImpl implements SigningCtx {

    /** Capsulates private key of the signer. */
    private final SigningKey key;

    /** The certificate of the signer. */
    private final X509Certificate cert;

    /**
     * Creates a new SigningCtx with provided signing key and certificate.
     * @param key the signing key
     * @param cert the certificate
     */
    public SigningCtxImpl(SigningKey key, X509Certificate cert) {
        if (key == null) {
            throw new IllegalArgumentException("Key must not be null");
        }

        if (cert == null) {
            throw new IllegalArgumentException("Cert must not be null");
        }

        this.key = key;
        this.cert = cert;
    }

    @Override
    public SignatureData buildSignature(SignatureBuilder builder)
            throws Exception {
        List<X509Certificate> extraCerts = getIntermediateCaCerts();
        List<OCSPResp> ocspResponses = getOcspResponses(extraCerts);

        builder.addExtraCertificates(extraCerts);
        builder.addOcspResponses(ocspResponses);
        builder.setSigningCert(cert);

        return builder.build(key, CryptoUtils.SHA512WITHRSA_ID);
    }

    private List<OCSPResp> getOcspResponses(List<X509Certificate> certs)
            throws Exception {
        List<X509Certificate> allCerts = new ArrayList<>(certs.size() + 1);
        allCerts.add(cert);
        allCerts.addAll(certs);

        return KeyConf.getAllOcspResponses(allCerts);
    }

    private List<X509Certificate> getIntermediateCaCerts() throws Exception {
        CertChain chain = GlobalConf.getCertChain(cert);
        if (chain == null) {
            throw new CodedException(X_CANNOT_CREATE_SIGNATURE,
                    "Got empty certificate chain for certificate %s",
                    cert.getSerialNumber());
        }

        return chain.getAdditionalCerts();
    }
}
