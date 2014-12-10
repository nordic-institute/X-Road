package ee.cyber.sdsb.signer.protocol.handler;

import ee.cyber.sdsb.signer.protocol.AbstractRequestHandler;
import ee.cyber.sdsb.signer.protocol.message.SetOcspResponses;

import static ee.cyber.sdsb.signer.tokenmanager.ServiceLocator.getOcspResponseManager;

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
