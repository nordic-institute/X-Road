package ee.ria.xroad.signer.protocol.handler;

import ee.ria.xroad.signer.protocol.AbstractRequestHandler;
import ee.ria.xroad.signer.protocol.message.GetOcspResponses;

import static ee.ria.xroad.signer.tokenmanager.ServiceLocator.getOcspResponseManager;

/**
 * Handles OCSP requests.
 */
public class GetOcspResponsesRequestHandler
        extends AbstractRequestHandler<GetOcspResponses> {

    @Override
    protected Object handle(GetOcspResponses message) throws Exception {
        getOcspResponseManager(getContext()).tell(message, getSender());
        return nothing();
    }

}
