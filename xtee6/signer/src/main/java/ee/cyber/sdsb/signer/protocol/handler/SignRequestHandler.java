package ee.cyber.sdsb.signer.protocol.handler;

import ee.cyber.sdsb.signer.protocol.AbstractRequestHandler;
import ee.cyber.sdsb.signer.protocol.message.Sign;

public class SignRequestHandler extends AbstractRequestHandler<Sign> {

    @Override
    protected Object handle(Sign message) throws Exception {
        tellTokenManager(message);
        return nothing();
    }

}
