package ee.ria.xroad.signer.protocol.handler;

import ee.ria.xroad.signer.protocol.AbstractRequestHandler;
import ee.ria.xroad.signer.protocol.message.SetOcspResponses;

import static ee.ria.xroad.signer.tokenmanager.ServiceLocator.getOcspResponseManager;

/**
 * Handles requests for setting the OCSP responses for certificates.
 */
public class SetOcspResponsesRequestHandler
        extends AbstractRequestHandler<SetOcspResponses> {

    @Override
    protected Object handle(SetOcspResponses message) throws Exception {
        getOcspResponseManager(getContext()).tell(message, getSender());
        return success();
    }

}
