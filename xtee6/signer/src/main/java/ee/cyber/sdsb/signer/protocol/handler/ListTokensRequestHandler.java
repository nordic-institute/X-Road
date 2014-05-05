package ee.cyber.sdsb.signer.protocol.handler;

import ee.cyber.sdsb.signer.core.TokenManager;
import ee.cyber.sdsb.signer.protocol.AbstractRequestHandler;
import ee.cyber.sdsb.signer.protocol.message.ListTokens;

public class ListTokensRequestHandler
        extends AbstractRequestHandler<ListTokens> {

    @Override
    protected Object handle(ListTokens message) throws Exception {
        return TokenManager.listTokens();
    }

}
