package ee.ria.xroad.signer.protocol.handler;

import ee.ria.xroad.signer.protocol.AbstractRequestHandler;
import ee.ria.xroad.signer.protocol.message.GetTokenBatchSigningEnabled;
import ee.ria.xroad.signer.tokenmanager.TokenManager;

/**
 * Handles queries for batch signing capabilities of a token.
 */
public class GetTokenBatchSigningEnabledRequestHandler
        extends AbstractRequestHandler<GetTokenBatchSigningEnabled> {

    @Override
    protected Object handle(GetTokenBatchSigningEnabled message)
            throws Exception {
        String tokenId = TokenManager.findTokenIdForKeyId(message.getKeyId());
        return new Boolean(TokenManager.isBatchSigningEnabled(tokenId));
    }

}
