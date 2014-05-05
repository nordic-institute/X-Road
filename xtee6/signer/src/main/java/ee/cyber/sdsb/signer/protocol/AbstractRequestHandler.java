package ee.cyber.sdsb.signer.protocol;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorRef;
import akka.actor.UntypedActor;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.signer.protocol.message.SuccessResponse;

import static ee.cyber.sdsb.common.ErrorCodes.*;
import static ee.cyber.sdsb.signer.util.SignerUtil.getTokenManager;
import static ee.cyber.sdsb.signer.util.SignerUtil.getTokenWorker;

@SuppressWarnings("unchecked")
public abstract class AbstractRequestHandler<T> extends UntypedActor {

    public static final Object SUCCESS = new SuccessResponse();
    public static final Object NOTHING = null;

    protected static final Logger LOG =
            LoggerFactory.getLogger(AbstractRequestHandler.class);

    @Override
    public void onReceive(Object message) throws Exception {
        LOG.trace("onReceive({})", message);
        try {
            Object result = handle((T) message);
            if (result != AbstractRequestHandler.NOTHING) {
                if (result instanceof Exception) {
                    handleError(translateException((Exception) result));
                } else if (hasSender()) {
                    getSender().tell(result, getSelf());
                }
            }
        } catch (ClassCastException e) {
            handleError(new CodedException(X_INTERNAL_ERROR,
                    "Unexpected message '%s'", message));
        } catch (Exception e) {
            handleError(translateException(e));
        } finally {
            getContext().stop(getSelf());
        }
    }

    protected void tellTokenManager(Object message) {
        getTokenManager(getContext()).tell(message, getSender());
    }

    protected void tellTokenWorker(Object message, String tokenId) {
        tellTokenWorker(message, tokenId, getSender());
    }

    protected void tellTokenWorker(Object message, String tokenId,
            ActorRef sender) {
        getTokenWorker(getContext(), tokenId).tell(message, sender);
    }

    protected abstract Object handle(T message) throws Exception;

    private void handleError(CodedException e) {
        LOG.error("Error in request handler", e);

        if (hasSender()) {
            getSender().tell(e.withPrefix(SIGNER_X), getSelf());
        }
    }

    private boolean hasSender() {
        return getSender() != ActorRef.noSender();
    }

    protected static Object success() {
        return SUCCESS;
    }

    protected static Object nothing() {
        return NOTHING;
    }

}
