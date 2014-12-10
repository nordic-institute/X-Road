package ee.cyber.sdsb.signer.protocol.handler;

import ee.cyber.sdsb.signer.protocol.AbstractRequestHandler;
import ee.cyber.sdsb.signer.protocol.message.GetTokenInfo;
import ee.cyber.sdsb.signer.tokenmanager.TokenManager;

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
