package ee.cyber.sdsb.signer.util;


import static ee.cyber.sdsb.common.ErrorCodes.SIGNER_X;
import static ee.cyber.sdsb.common.ErrorCodes.translateException;

/**
 * Represents an actor which handles update messages.
 */
public abstract class AbstractUpdateableActor extends AbstractSignerActor {

    @Override
    public final void onReceive(Object message) throws Exception {
        try {
            if (message instanceof Update) {
                onUpdate();
            } else {
                onMessage(message);
            }
        } catch (Throwable e) {
            sendResponse(translateException(e).withPrefix(SIGNER_X));
        }
    }

    protected abstract void onUpdate() throws Exception;
    protected abstract void onMessage(Object message) throws Exception;
}
