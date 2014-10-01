package ee.cyber.sdsb.signer.util;

import akka.actor.ActorRef;
import akka.actor.UntypedActor;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.signer.protocol.message.SuccessResponse;

import static ee.cyber.sdsb.common.ErrorCodes.translateException;

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
