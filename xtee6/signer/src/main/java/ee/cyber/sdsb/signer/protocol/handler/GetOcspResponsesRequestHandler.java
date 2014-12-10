package ee.cyber.sdsb.signer.protocol.handler;

import ee.cyber.sdsb.signer.protocol.AbstractRequestHandler;
import ee.cyber.sdsb.signer.protocol.message.GetOcspResponses;

import static ee.cyber.sdsb.signer.tokenmanager.ServiceLocator.getOcspResponseManager;

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
