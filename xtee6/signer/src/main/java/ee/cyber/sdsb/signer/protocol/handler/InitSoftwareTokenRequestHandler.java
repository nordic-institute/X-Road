package ee.cyber.sdsb.signer.protocol.handler;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.signer.protocol.AbstractRequestHandler;
import ee.cyber.sdsb.signer.protocol.message.InitSoftwareToken;
import ee.cyber.sdsb.signer.tokenmanager.TokenManager;

import static ee.cyber.sdsb.common.ErrorCodes.X_INTERNAL_ERROR;

public class InitSoftwareTokenRequestHandler
        extends AbstractRequestHandler<InitSoftwareToken> {

    @Override
    protected Object handle(InitSoftwareToken message) throws Exception {
        String softwareTokenId = TokenManager.getSoftwareTokenId();
        if (softwareTokenId != null) {
            tellTokenWorker(message, softwareTokenId);
            return nothing();
        }

        throw new CodedException(X_INTERNAL_ERROR, "Software token not found");
    }

}
