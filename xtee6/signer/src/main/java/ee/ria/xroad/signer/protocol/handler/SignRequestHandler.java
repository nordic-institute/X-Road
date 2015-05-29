package ee.ria.xroad.signer.protocol.handler;

import ee.ria.xroad.signer.protocol.AbstractRequestHandler;
import ee.ria.xroad.signer.protocol.message.Sign;

import static ee.ria.xroad.signer.tokenmanager.TokenManager.findTokenIdForKeyId;

/**
 * Handles signing requests.
 */
public class SignRequestHandler extends AbstractRequestHandler<Sign> {

    @Override
    protected Object handle(Sign message) throws Exception {
        tellToken(message, findTokenIdForKeyId(message.getKeyId()));
        return nothing();
    }

}
