package ee.ria.xroad.proxy.conf;

import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;

import org.bouncycastle.cert.ocsp.OCSPResp;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.cert.CertChain;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.signature.SignatureBuilder;
import ee.ria.xroad.common.signature.SignatureData;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.proxy.signedmessage.SigningKey;

import static ee.ria.xroad.common.ErrorCodes.X_CANNOT_CREATE_SIGNATURE;

/**
 * Encapsulates security-related parameters of a given member,
 * such as currently used signing key and cert.
 */
@RequiredArgsConstructor
public class SigningCtxImpl implements SigningCtx {

    /** The subject id of the signer. */
    private final ClientId subject;

    /** Encapsulates private key of the signer. */
    private final SigningKey key;

    /** The certificate of the signer. */
    private final X509Certificate cert;

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
        CertChain chain =
                GlobalConf.getCertChain(subject.getXRoadInstance(), cert);
        if (chain == null) {
            throw new CodedException(X_CANNOT_CREATE_SIGNATURE,
                    "Got empty certificate chain for certificate %s",
                    cert.getSerialNumber());
        }

        return chain.getAdditionalCerts();
    }
}
