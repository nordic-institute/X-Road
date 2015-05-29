package ee.ria.xroad.signer.protocol.handler;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.signer.protocol.AbstractRequestHandler;
import ee.ria.xroad.signer.protocol.dto.KeyInfo;
import ee.ria.xroad.signer.protocol.message.GetKeyIdForCertHash;
import ee.ria.xroad.signer.protocol.message.GetKeyIdForCertHashResponse;
import ee.ria.xroad.signer.tokenmanager.TokenManager;

import static ee.ria.xroad.common.ErrorCodes.X_CERT_NOT_FOUND;

/**
 * Handles requests for key id based on certificate hashes.
 */
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
