package ee.cyber.sdsb.signer.protocol.handler;

import ee.cyber.sdsb.signer.protocol.AbstractRequestHandler;
import ee.cyber.sdsb.signer.protocol.message.ActivateCert;
import ee.cyber.sdsb.signer.tokenmanager.TokenManager;

public class ActivateCertRequestHandler
        extends AbstractRequestHandler<ActivateCert> {

    @Override
    protected Object handle(ActivateCert message) throws Exception {
        TokenManager.setCertActive(message.getCertIdOrHash(),
                message.isActive());
        return success();
    }

}
