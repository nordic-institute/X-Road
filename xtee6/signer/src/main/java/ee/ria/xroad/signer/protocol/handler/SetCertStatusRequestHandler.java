package ee.ria.xroad.signer.protocol.handler;

import ee.ria.xroad.signer.protocol.AbstractRequestHandler;
import ee.ria.xroad.signer.protocol.message.SetCertStatus;
import ee.ria.xroad.signer.tokenmanager.TokenManager;

/**
 * Handles requests for setting the certificate status.
 */
public class SetCertStatusRequestHandler
        extends AbstractRequestHandler<SetCertStatus> {

    @Override
    protected Object handle(SetCertStatus message) throws Exception {
        TokenManager.setCertStatus(message.getCertId(), message.getStatus());
        return success();
    }

}
