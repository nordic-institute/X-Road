 package ee.cyber.sdsb.signer.protocol;

import lombok.extern.slf4j.Slf4j;
import akka.actor.ActorRef;
import akka.actor.UntypedActor;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.signer.protocol.message.SuccessResponse;
import ee.cyber.sdsb.signer.tokenmanager.TokenManager;

import static ee.cyber.sdsb.common.ErrorCodes.*;
import static ee.cyber.sdsb.signer.tokenmanager.ServiceLocator.getTokenSigner;
import static ee.cyber.sdsb.signer.tokenmanager.ServiceLocator.getTokenWorker;
import static ee.cyber.sdsb.signer.util.ExceptionHelper.tokenNotInitialized;

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

    protected void tellTokenWorker(Object message, String tokenId) {
        tellTokenWorker(message, tokenId, getSender());
    }

    protected void tellTokenSigner(Object message, String tokenId) {
        if (!TokenManager.isTokenAvailable(tokenId)) {
            throw tokenNotInitialized(tokenId);
        }

        getTokenSigner(getContext(), tokenId).tell(message, getSender());
    }

    protected void tellTokenWorker(Object message, String tokenId,
            ActorRef sender) {
        if (!TokenManager.isTokenAvailable(tokenId)) {
            throw tokenNotInitialized(tokenId);
        }

        getTokenWorker(getContext(), tokenId).tell(message, sender);
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
