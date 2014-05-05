package ee.cyber.sdsb.signer.protocol.handler;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.signer.core.TokenManager;
import ee.cyber.sdsb.signer.protocol.AbstractRequestHandler;
import ee.cyber.sdsb.signer.protocol.dto.CertificateInfo;
import ee.cyber.sdsb.signer.protocol.dto.KeyInfo;
import ee.cyber.sdsb.signer.protocol.dto.MemberSigningInfo;
import ee.cyber.sdsb.signer.protocol.message.GetMemberSigningInfo;

import static ee.cyber.sdsb.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.cyber.sdsb.common.ErrorCodes.X_UNKNOWN_MEMBER;

public class GetMemberSigningInfoRequestHandler
        extends AbstractRequestHandler<GetMemberSigningInfo> {

    @Override
    protected Object handle(GetMemberSigningInfo message) throws Exception {
        KeyInfo memberKey = TokenManager.getKeyInfo(message.getMemberId());
        if (memberKey == null) {
            throw new CodedException(X_UNKNOWN_MEMBER,
                    "Could not find certificates for client '%s'",
                    message.getMemberId());
        }

        CertificateInfo memberCert = selectMemberCert(memberKey,
                message.getMemberId());
        if (memberCert == null) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "Client '%s' has no suitable certificates",
                    message.getMemberId());
        }

        return new MemberSigningInfo(memberKey.getId(),
                memberCert.getCertificateBytes());
    }

    private CertificateInfo selectMemberCert(KeyInfo memberKey,
            ClientId memberId) {
        for (CertificateInfo certInfo : memberKey.getCerts()) {
            if (TokenManager.certBelongsToMember(certInfo, memberId)) {
                if (isSuitableCertificate(certInfo)) {
                    LOG.info("Found suitable certificate for member '{}'",
                            memberId);
                    return certInfo;
                }
            }
        }

        return null;
    }

    private boolean isSuitableCertificate(CertificateInfo certInfo) {
        // TODO: Logic for selecting the most suitable certificate
        return certInfo.isActive();
    }

}
