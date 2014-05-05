package ee.cyber.sdsb.signer.protocol.handler;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.signer.core.TokenManager;
import ee.cyber.sdsb.signer.protocol.AbstractRequestHandler;
import ee.cyber.sdsb.signer.protocol.dto.KeyInfo;
import ee.cyber.sdsb.signer.protocol.message.GetKeyIdForCertHash;
import ee.cyber.sdsb.signer.protocol.message.GetKeyIdForCertHashResponse;

import static ee.cyber.sdsb.common.ErrorCodes.X_INTERNAL_ERROR;

public class GetKeyIdForCertHashRequestHandler
        extends AbstractRequestHandler<GetKeyIdForCertHash> {

    @Override
    protected Object handle(GetKeyIdForCertHash message) throws Exception {
        KeyInfo keyInfo =
                TokenManager.getKeyInfoForCertHash(message.getCertHash());
        if (keyInfo == null) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "Certificate with hash '%s' not found",
                    message.getCertHash());
        }

        return new GetKeyIdForCertHashResponse(keyInfo.getId());
    }

}
