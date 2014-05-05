package ee.cyber.sdsb.signer.util;

import ee.cyber.sdsb.signer.core.SignerActor;

import static ee.cyber.sdsb.common.ErrorCodes.SIGNER_X;
import static ee.cyber.sdsb.common.ErrorCodes.translateException;

public abstract class UpdateableActor extends SignerActor {

    @Override
    public final void onReceive(Object message) throws Exception {
        try {
            if (message instanceof Update) {
                onUpdate();
            } else {
                onMessage(message);
            }
        } catch (Exception e) {
            sendResponse(translateException(e).withPrefix(SIGNER_X));
        }
    }

    protected abstract void onUpdate() throws Exception;
    protected abstract void onMessage(Object message) throws Exception;
}
