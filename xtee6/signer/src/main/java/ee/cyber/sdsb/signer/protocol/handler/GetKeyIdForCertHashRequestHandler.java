package ee.cyber.sdsb.signer.protocol.handler;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.signer.protocol.AbstractRequestHandler;
import ee.cyber.sdsb.signer.protocol.dto.KeyInfo;
import ee.cyber.sdsb.signer.protocol.message.GetKeyIdForCertHash;
import ee.cyber.sdsb.signer.protocol.message.GetKeyIdForCertHashResponse;
import ee.cyber.sdsb.signer.tokenmanager.TokenManager;

import static ee.cyber.sdsb.common.ErrorCodes.X_CERT_NOT_FOUND;

public class GetKeyIdForCertHashRequestHandler
        extends AbstractRequestHandler<GetKeyIdForCertHash> {

    @Override
    protected Object handle(GetKeyIdForCertHash message) throws Exception {
        KeyInfo keyInfo =
                TokenManager.getKeyInfoForCertHash(message.getCertHash());
        if (keyInfo == null) {
            throw CodedException.tr(X_CERT_NOT_FOUND,
                    "certificate_with_hash_not_found",
                    "Certificate with hash '%s' not found",
                    message.getCertHash());
        }

        return new GetKeyIdForCertHashResponse(keyInfo.getId());
    }

}
