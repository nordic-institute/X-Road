package ee.cyber.sdsb.signer.protocol;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;

import ee.cyber.sdsb.common.CodedException;

import static ee.cyber.sdsb.common.ErrorCodes.*;

/**
 * Request handler will handle all incoming requests...
 */
public class SignerRequestProcessor extends UntypedActor {

    private static final Logger LOG =
            LoggerFactory.getLogger(SignerRequestProcessor.class);

    private static final String HANDLER_PACKAGE_NAME =
            "ee.cyber.sdsb.signer.protocol.handler.";
    private static final String HANDLER_CLASS_SUFFIX = "RequestHandler";

    @Override
    public void onReceive(Object message) throws Exception {
        LOG.trace("onReceive({})", message);
        try {
            handle(message);
        } catch (Throwable e) {
            LOG.error("Error in request processor", e);
        }
    }

    private void handle(Object message) {
        try {
            // For handling the request, create a temporary actor, that will
            // stop itself, after it has finished handling the message
            Class<? extends AbstractRequestHandler<?>> handlerClass =
                    getRequestHandler(message);
            if (handlerClass != null) {
                ActorRef handlerActor =
                        getContext().actorOf(Props.create(handlerClass));
                handlerActor.tell(message, getSender());
            } else {
                throw new CodedException(X_INTERNAL_ERROR, "Unknown request");
            }
        } catch (Throwable e) { // We want to catch serious errors as well
            LOG.error("Error in request processor", e);

            if (getSender() != ActorRef.noSender()) {
                CodedException translated =
                        translateException(e).withPrefix(SIGNER_X);
                getSender().tell(translated, getSelf());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Class<? extends AbstractRequestHandler<?>> getRequestHandler(
            Object message) throws Exception {
        String handlerName = message.getClass().getSimpleName() +
                HANDLER_CLASS_SUFFIX;
        String handlerClass = HANDLER_PACKAGE_NAME + handlerName;

        LOG.debug("Looking for request processor '{}'", handlerClass);
        try {
            Class<?> clazz = Class.forName(handlerClass);
            if (AbstractRequestHandler.class.isAssignableFrom(clazz)) {
                return (Class<? extends AbstractRequestHandler<?>>) clazz;
            } else {
                LOG.error("Invalid request handler '{}'; must be subclass" +
                        " of {}", clazz, AbstractRequestHandler.class);
                return null;
            }
        } catch (Exception e) {
            LOG.error("Error while getting request handler", e);
            return null;
        }
    }
}
