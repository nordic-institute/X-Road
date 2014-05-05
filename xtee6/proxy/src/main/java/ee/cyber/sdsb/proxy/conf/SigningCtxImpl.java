package ee.cyber.sdsb.proxy.conf;

import java.security.cert.X509Certificate;

import org.bouncycastle.cert.ocsp.OCSPResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.CodedException;
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

    private static final Logger LOG =
            LoggerFactory.getLogger(SigningCtxImpl.class);

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
        LOG.trace("buildSignature()");

        // TODO: Add any intermediate certificates and their corresponding OCSP responses
        // builder.addExtraCertificate(intermediateCaCert);
        // builder.addExtraOcspResponse(intermediateCaCertOcspResponse);

        OCSPResp ocspResponse = ServerConf.getOcspResponse(cert);
        if (ocspResponse == null) {
            throw new CodedException(X_CANNOT_CREATE_SIGNATURE,
                    "Cannot sign, OCSP response for certificate "
                            + cert.getSerialNumber() +  " is not available");
        }

        builder.setSigningCert(cert, ocspResponse);

        //return builder.build(cert, key, signerOcspResponse);
        String signatureAlgorithmId = CryptoUtils.SHA512WITHRSA_ID;
        return builder.build(key, signatureAlgorithmId);
    }

}
