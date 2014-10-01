package ee.cyber.sdsb.signer.protocol.handler;

import ee.cyber.sdsb.signer.protocol.AbstractRequestHandler;
import ee.cyber.sdsb.signer.protocol.message.GenerateKey;

public class GenerateKeyRequestHandler
        extends AbstractRequestHandler<GenerateKey> {

    @Override
    protected Object handle(GenerateKey message) throws Exception {
        tellTokenWorker(message, message.getTokenId());
        return nothing();
    }

}
