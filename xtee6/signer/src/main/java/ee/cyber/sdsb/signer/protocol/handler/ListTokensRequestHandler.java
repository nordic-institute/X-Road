package ee.cyber.sdsb.signer.protocol.handler;

import ee.cyber.sdsb.signer.protocol.AbstractRequestHandler;
import ee.cyber.sdsb.signer.protocol.message.ListTokens;
import ee.cyber.sdsb.signer.tokenmanager.TokenManager;

public class ListTokensRequestHandler
        extends AbstractRequestHandler<ListTokens> {

    @Override
    protected Object handle(ListTokens message) throws Exception {
        return TokenManager.listTokens();
    }

}
