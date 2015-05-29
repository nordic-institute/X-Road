package ee.ria.xroad.signer.protocol.handler;

import ee.ria.xroad.signer.protocol.AbstractRequestHandler;
import ee.ria.xroad.signer.protocol.message.ActivateToken;

/**
 * Handles token activations and deactivations.
 */
public class ActivateTokenRequestHandler
        extends AbstractRequestHandler<ActivateToken> {

    @Override
    protected Object handle(ActivateToken message) throws Exception {
        tellToken(message, message.getTokenId());
        return nothing();
    }

}
