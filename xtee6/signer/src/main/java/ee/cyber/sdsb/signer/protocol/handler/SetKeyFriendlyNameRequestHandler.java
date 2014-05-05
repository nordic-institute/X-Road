package ee.cyber.sdsb.signer.protocol.handler;

import ee.cyber.sdsb.signer.core.TokenManager;
import ee.cyber.sdsb.signer.protocol.AbstractRequestHandler;
import ee.cyber.sdsb.signer.protocol.message.SetKeyFriendlyName;

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
