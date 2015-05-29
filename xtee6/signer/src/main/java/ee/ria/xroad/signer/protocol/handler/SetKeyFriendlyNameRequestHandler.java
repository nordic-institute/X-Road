package ee.ria.xroad.signer.protocol.handler;

import ee.ria.xroad.signer.protocol.AbstractRequestHandler;
import ee.ria.xroad.signer.protocol.message.SetKeyFriendlyName;
import ee.ria.xroad.signer.tokenmanager.TokenManager;

/**
 * Handles requests for setting the key friendly name.
 */
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
