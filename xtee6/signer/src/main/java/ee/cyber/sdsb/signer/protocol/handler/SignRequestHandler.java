package ee.cyber.sdsb.signer.protocol.handler;

import ee.cyber.sdsb.signer.protocol.AbstractRequestHandler;
import ee.cyber.sdsb.signer.protocol.message.Sign;

import static ee.cyber.sdsb.signer.tokenmanager.TokenManager.findTokenIdForKeyId;

/**
 * Handles signing requests.
 */
public class SignRequestHandler extends AbstractRequestHandler<Sign> {

    @Override
    protected Object handle(Sign message) throws Exception {
        tellTokenSigner(message, findTokenIdForKeyId(message.getKeyId()));
        return nothing();
    }

}
