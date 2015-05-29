package ee.ria.xroad.signer.protocol.handler;

import ee.ria.xroad.signer.protocol.AbstractRequestHandler;
import ee.ria.xroad.signer.protocol.message.GetTokenInfo;
import ee.ria.xroad.signer.tokenmanager.TokenManager;

/**
 * Handles requests for token info.
 */
public class GetTokenInfoRequestHandler
        extends AbstractRequestHandler<GetTokenInfo> {

    @Override
    protected Object handle(GetTokenInfo message) throws Exception {
        return TokenManager.getTokenInfo(message.getTokenId());
    }

}
