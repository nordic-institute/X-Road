package ee.cyber.sdsb.signer.protocol.handler;

import ee.cyber.sdsb.signer.core.TokenManager;
import ee.cyber.sdsb.signer.protocol.AbstractRequestHandler;
import ee.cyber.sdsb.signer.protocol.message.SetTokenFriendlyName;

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
