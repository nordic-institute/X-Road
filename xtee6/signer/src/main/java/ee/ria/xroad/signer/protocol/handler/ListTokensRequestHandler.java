package ee.ria.xroad.signer.protocol.handler;

import ee.ria.xroad.signer.protocol.AbstractRequestHandler;
import ee.ria.xroad.signer.protocol.message.ListTokens;
import ee.ria.xroad.signer.tokenmanager.TokenManager;

/**
 * Handles requests for token list.
 */
public class ListTokensRequestHandler
        extends AbstractRequestHandler<ListTokens> {

    @Override
    protected Object handle(ListTokens message) throws Exception {
        return TokenManager.listTokens();
    }

}
