package ee.cyber.sdsb.signer.protocol.handler;

import ee.cyber.sdsb.signer.protocol.AbstractRequestHandler;
import ee.cyber.sdsb.signer.protocol.message.SetKeyFriendlyName;
import ee.cyber.sdsb.signer.tokenmanager.TokenManager;

public class SetKeyFriendlyNameRequestHandler
        extends AbstractRequestHandler<SetKeyFriendlyName> {

    @Override
    protected Object handle(SetKeyFriendlyName message)
            throws Exception {
        TokenManager.setKeyFriendlyName(message.getKeyId(),
                message.getFriendlyName());
        return success();
    }

}
