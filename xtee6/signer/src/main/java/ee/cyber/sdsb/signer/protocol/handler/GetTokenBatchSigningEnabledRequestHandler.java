package ee.cyber.sdsb.signer.protocol.handler;

import ee.cyber.sdsb.signer.protocol.AbstractRequestHandler;
import ee.cyber.sdsb.signer.protocol.message.GetTokenBatchSigningEnabled;
import ee.cyber.sdsb.signer.tokenmanager.TokenManager;

public class GetTokenBatchSigningEnabledRequestHandler
        extends AbstractRequestHandler<GetTokenBatchSigningEnabled> {

    @Override
    protected Object handle(GetTokenBatchSigningEnabled message)
            throws Exception {
        String tokenId = TokenManager.findTokenIdForKeyId(message.getKeyId());
        return new Boolean(TokenManager.isBatchSigningEnabled(tokenId));
    }

}
