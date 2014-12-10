package ee.cyber.xroad.common.signature;

import java.security.cert.X509Certificate;
import java.util.List;

import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cms.SignerId;
import org.bouncycastle.cms.SignerInformation;
import org.bouncycastle.cms.SignerInformationVerifier;
import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.tsp.TimeStampToken;

import ee.cyber.xroad.common.CodedException;
import ee.cyber.xroad.common.ErrorCodes;
import ee.cyber.xroad.common.util.CryptoUtils;

public class TimestampVerifier {

    /**
     * Verifies that time-stamp applies to <code>stampedData</code>
     * and that it is signed by a trusted time-stamping authority
     */
    public static void verify(TimeStampToken tsToken,
            byte[] stampedData, List<X509Certificate> tspCerts)
                    throws Exception {
        String thatHash = CryptoUtils.encodeBase64(CryptoUtils.calculateDigest(
                tsToken.getTimeStampInfo().getHashAlgorithm(), stampedData));
        String thisHash = CryptoUtils.encodeBase64(
                tsToken.getTimeStampInfo().getMessageImprintDigest());
        if (!thisHash.equals(thatHash)) {
            throw new CodedException(ErrorCodes.X_MALFORMED_SIGNATURE,
                    "Timestamp hashes do not match");
        }

        verify(tsToken, tspCerts);
    }

    /**
     * Verifies that the time-stamp token is signed by a trusted
     * time-stamping authority.
     */
    public static void verify(TimeStampToken tsToken,
            List<X509Certificate> tspCerts) throws Exception {
        if (tspCerts.isEmpty()) {
            throw new CodedException(ErrorCodes.X_INTERNAL_ERROR,
                    "Could not get TSP certificates");
        }

        SignerId signerId = tsToken.getSID();

        X509Certificate cert = getTspCertificate(signerId, tspCerts);
        if (cert == null) {
            throw new CodedException(ErrorCodes.X_INTERNAL_ERROR,
                    "Could not find TSP certificate for timestamp");
        }

        SignerInformation signerInfo =
                tsToken.toCMSSignedData().getSignerInfos().get(signerId);
        if (signerInfo == null) {
            throw new CodedException(ErrorCodes.X_INTERNAL_ERROR,
                    "Could not get signer information for "
                            + signerId.getSerialNumber());
        }

        SignerInformationVerifier verifier = createVerifier(cert);
        if (!signerInfo.verify(verifier)) {
            throw new CodedException(ErrorCodes.X_TIMESTAMP_VALIDATION,
                    "Failed to verify timestamp");
        }
    }

    public static X509Certificate getSignerCertificate(
            TimeStampToken tsToken, List<X509Certificate> tspCerts)
            throws Exception {
        SignerId signerId = tsToken.getSID();

        return getTspCertificate(signerId, tspCerts);
    }

    private static X509Certificate getTspCertificate(SignerId signerId,
            List<X509Certificate> tspCerts) throws Exception {
        for (X509Certificate cert : tspCerts) {
            if (signerId.match(new X509CertificateHolder(cert.getEncoded()))) {
                return cert;
            }
        }

        return null;
    }

    private static SignerInformationVerifier createVerifier(
            X509Certificate cert) throws OperatorCreationException {
        JcaSimpleSignerInfoVerifierBuilder verifierBuilder =
                new JcaSimpleSignerInfoVerifierBuilder();
        verifierBuilder.setProvider("BC");

        return verifierBuilder.build(cert);
    }

}
