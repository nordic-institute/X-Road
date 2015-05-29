package ee.ria.xroad.signer.protocol.handler;

import ee.ria.xroad.signer.protocol.AbstractRequestHandler;
import ee.ria.xroad.signer.protocol.message.SetTokenFriendlyName;
import ee.ria.xroad.signer.tokenmanager.TokenManager;

/**
 * Handles requests for setting the token friendly name.
 */
public class SetTokenFriendlyNameRequestHandler
        extends AbstractRequestHandler<SetTokenFriendlyName> {

    @Override
    protected Object handle(SetTokenFriendlyName message)
            throws Exception {
        TokenManager.setTokenFriendlyName(message.getTokenId(),
                message.getFriendlyName());
        return success();
    }

}
