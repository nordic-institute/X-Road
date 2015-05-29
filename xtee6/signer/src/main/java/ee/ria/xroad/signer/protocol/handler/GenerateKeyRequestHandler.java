package ee.ria.xroad.signer.protocol.handler;

import ee.ria.xroad.signer.protocol.AbstractRequestHandler;
import ee.ria.xroad.signer.protocol.message.GenerateKey;

/**
 * Handles key generations.
 */
public class GenerateKeyRequestHandler
        extends AbstractRequestHandler<GenerateKey> {

    @Override
    protected Object handle(GenerateKey message) throws Exception {
        tellToken(message, message.getTokenId());
        return nothing();
    }

}
