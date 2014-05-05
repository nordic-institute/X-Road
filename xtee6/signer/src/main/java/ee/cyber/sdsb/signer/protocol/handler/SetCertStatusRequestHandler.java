package ee.cyber.sdsb.signer.protocol.handler;

import ee.cyber.sdsb.signer.core.TokenManager;
import ee.cyber.sdsb.signer.protocol.AbstractRequestHandler;
import ee.cyber.sdsb.signer.protocol.message.SetCertStatus;

public class SetCertStatusRequestHandler
        extends AbstractRequestHandler<SetCertStatus> {

    @Override
    protected Object handle(SetCertStatus message) throws Exception {
        TokenManager.setCertStatus(message.getCertId(), message.getStatus());
        return success();
    }

}
