package ee.ria.xroad.signer.util;

import akka.actor.ActorRef;
import akka.actor.UntypedActor;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.signer.protocol.message.SuccessResponse;

import static ee.ria.xroad.common.ErrorCodes.translateException;

/**
 * A generic actor base.
 */
public abstract class AbstractSignerActor extends UntypedActor {

    protected void sendResponse(Object message) {
        if (getSender() != ActorRef.noSender()) {
            if (message instanceof Exception) {
                getSender().tell(
                        translateError((Exception) message), getSelf());
            } else {
                getSender().tell(message, getSelf());
            }
        }
    }

    protected void sendSuccessResponse() {
        sendResponse(new SuccessResponse());
    }

    protected CodedException translateError(Exception e) {
        return translateException(e);
    }
}
