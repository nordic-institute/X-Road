package ee.cyber.sdsb.signer.protocol.handler;

import java.security.cert.X509Certificate;

import lombok.extern.slf4j.Slf4j;

import org.bouncycastle.cert.ocsp.OCSPResp;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.conf.GlobalConf;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.ocsp.OcspVerifier;
import ee.cyber.sdsb.signer.protocol.AbstractRequestHandler;
import ee.cyber.sdsb.signer.protocol.dto.CertificateInfo;
import ee.cyber.sdsb.signer.protocol.dto.KeyInfo;
import ee.cyber.sdsb.signer.protocol.dto.MemberSigningInfo;
import ee.cyber.sdsb.signer.protocol.message.GetMemberSigningInfo;
import ee.cyber.sdsb.signer.tokenmanager.TokenManager;

import static ee.cyber.sdsb.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.cyber.sdsb.common.ErrorCodes.X_UNKNOWN_MEMBER;
import static ee.cyber.sdsb.common.util.CryptoUtils.readCertificate;

@Slf4j
public class GetMemberSigningInfoRequestHandler
        extends AbstractRequestHandler<GetMemberSigningInfo> {

    @Override
    protected Object handle(GetMemberSigningInfo message) throws Exception {
        KeyInfo memberKey = TokenManager.getKeyInfo(message.getMemberId());
        if (memberKey == null) {
            throw CodedException.tr(X_UNKNOWN_MEMBER,
                    "member_certs_not_found",
                    "Could not find any certificates for member '%s'",
                    message.getMemberId());
        }

        CertificateInfo memberCert =
                selectMemberCert(memberKey, message.getMemberId());
        if (memberCert == null) {
            throw CodedException.tr(X_INTERNAL_ERROR,
                    "member_has_no_suitable_certs",
                    "Member '%s' has no suitable certificates",
                    message.getMemberId());
        }

        return new MemberSigningInfo(memberKey.getId(), memberCert);
    }

    private CertificateInfo selectMemberCert(KeyInfo memberKey,
            ClientId memberId) {
        for (CertificateInfo certInfo : memberKey.getCerts()) {
            if (TokenManager.certBelongsToMember(certInfo, memberId)
                    && isSuitableCertificate(certInfo)) {
                log.info("Found suitable certificate for member '{}'",
                        memberId);
                return certInfo;
            }
        }

        return null;
    }

    // Checks that the certificate is active and valid at current time.
    private boolean isSuitableCertificate(CertificateInfo cert) {
        if (!cert.isActive()) {
            return false;
        }

        try {
            checkValidity(cert.getCertificateBytes(), cert.getOcspBytes());
            return true;
        } catch (Exception e) {
            log.error("Certificate not suitable: {}", e.getMessage());
            return false;
        }
    }

    private void checkValidity(byte[] certBytes, byte[] ocspBytes)
            throws Exception {
        X509Certificate subject = readCertificate(certBytes);
        subject.checkValidity();
        verifyOcspResponse(ocspBytes, subject);
    }

    private void verifyOcspResponse(byte[] ocspBytes, X509Certificate subject)
            throws Exception {
        if (ocspBytes == null) {
            throw new Exception("OCSP response for certificate "
                    + subject.getSubjectX500Principal().getName()
                    + " not found");
        }

        OCSPResp ocsp = new OCSPResp(ocspBytes);
        X509Certificate issuer = GlobalConf.getCaCert(subject);
        OcspVerifier.verifyValidityAndStatus(ocsp, subject, issuer);
    }

}
