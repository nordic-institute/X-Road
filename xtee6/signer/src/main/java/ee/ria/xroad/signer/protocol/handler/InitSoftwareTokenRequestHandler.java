package ee.ria.xroad.signer.protocol.handler;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.signer.protocol.AbstractRequestHandler;
import ee.ria.xroad.signer.protocol.message.InitSoftwareToken;
import ee.ria.xroad.signer.tokenmanager.TokenManager;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;

/**
 * Handles requests for software token initialization.
 */
public class InitSoftwareTokenRequestHandler
        extends AbstractRequestHandler<InitSoftwareToken> {

    @Override
    protected Object handle(InitSoftwareToken message) throws Exception {
        String softwareTokenId = TokenManager.getSoftwareTokenId();
        if (softwareTokenId != null) {
            tellToken(message, softwareTokenId);
            return nothing();
        }

        throw new CodedException(X_INTERNAL_ERROR, "Software token not found");
    }

}
