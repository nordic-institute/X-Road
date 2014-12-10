package ee.cyber.sdsb.signer.protocol.handler;

import ee.cyber.sdsb.signer.protocol.AbstractRequestHandler;
import ee.cyber.sdsb.signer.protocol.message.SetTokenFriendlyName;
import ee.cyber.sdsb.signer.tokenmanager.TokenManager;

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
