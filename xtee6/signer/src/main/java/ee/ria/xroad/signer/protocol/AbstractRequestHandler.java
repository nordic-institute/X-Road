 package ee.ria.xroad.signer.protocol;

import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import lombok.extern.slf4j.Slf4j;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.signer.protocol.message.SuccessResponse;
import ee.ria.xroad.signer.tokenmanager.TokenManager;

import static ee.ria.xroad.common.ErrorCodes.*;
import static ee.ria.xroad.signer.tokenmanager.ServiceLocator.getToken;
import static ee.ria.xroad.signer.util.ExceptionHelper.tokenNotAvailable;

/**
 * An abstract request handler.
 * @param <T> the type of message this handler handles
 */
@SuppressWarnings("unchecked")
@Slf4j
public abstract class AbstractRequestHandler<T> extends UntypedActor {

    private static final Object SUCCESS = new SuccessResponse();
    private static final Object NOTHING = null;

    @Override
    public void onReceive(Object message) throws Exception {
        log.trace("onReceive({})", message);
        try {
            Object result = handle((T) message);
            if (result != nothing()) {
                if (result instanceof Exception) {
                    handleError(translateException((Exception) result));
                } else if (hasSender()) {
                    getSender().tell(result, getSelf());
                }
            }
        } catch (ClassCastException e) {
            handleError(new CodedException(X_INTERNAL_ERROR,
                    "Unexpected message: %s", message.getClass()));
        } catch (Exception e) {
            handleError(translateException(e));
        } finally {
            getContext().stop(getSelf());
        }
    }

    protected void tellToken(Object message, String tokenId) {
        tellToken(message, tokenId, getSender());
    }

    protected void tellToken(Object message, String tokenId,
            ActorRef sender) {
        if (!TokenManager.isTokenAvailable(tokenId)) {
            throw tokenNotAvailable(tokenId);
        }

        getToken(getContext(), tokenId).tell(message, sender);
    }

    protected abstract Object handle(T message) throws Exception;

    private void handleError(CodedException e) {
        log.error("Error in request handler", e);

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
