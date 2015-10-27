package ee.ria.xroad.signer.util;


import static ee.ria.xroad.common.ErrorCodes.SIGNER_X;
import static ee.ria.xroad.common.ErrorCodes.translateException;

/**
 * Represents an actor which handles update messages.
 */
public abstract class AbstractUpdateableActor extends AbstractSignerActor {

    @Override
    public final void onReceive(Object message) throws Exception {
        if (message instanceof Update) {
            onUpdate();
        } else {
            try {
                onMessage(message);
            } catch (Throwable e) {
                sendResponse(translateException(e).withPrefix(SIGNER_X));
            }
        }
    }

    protected abstract void onUpdate() throws Exception;
    protected abstract void onMessage(Object message) throws Exception;
}
