package ee.cyber.sdsb.signer.protocol.handler;

import ee.cyber.sdsb.signer.protocol.AbstractRequestHandler;
import ee.cyber.sdsb.signer.protocol.message.ActivateToken;

/**
 * Handles token activations and deactivations.
 */
public class ActivateTokenRequestHandler
        extends AbstractRequestHandler<ActivateToken> {

    @Override
    protected Object handle(ActivateToken message) throws Exception {
        tellTokenWorker(message, message.getTokenId());
        return nothing();
    }

}
