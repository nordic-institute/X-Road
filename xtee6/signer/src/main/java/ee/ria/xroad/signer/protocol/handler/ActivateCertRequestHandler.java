package ee.ria.xroad.signer.protocol.handler;

import ee.ria.xroad.signer.protocol.AbstractRequestHandler;
import ee.ria.xroad.signer.protocol.message.ActivateCert;
import ee.ria.xroad.signer.tokenmanager.TokenManager;

/**
 * Handles certificate activations and deactivations.
 */
public class ActivateCertRequestHandler
        extends AbstractRequestHandler<ActivateCert> {

    @Override
    protected Object handle(ActivateCert message) throws Exception {
        TokenManager.setCertActive(message.getCertIdOrHash(),
                message.isActive());
        return success();
    }

}
