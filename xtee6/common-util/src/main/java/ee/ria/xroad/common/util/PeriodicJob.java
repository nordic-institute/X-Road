package ee.ria.xroad.common.util;

import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.UntypedActor;
import lombok.RequiredArgsConstructor;
import scala.concurrent.duration.FiniteDuration;

/**
 * Actor that periodically sends a message to another actor.
 */
@RequiredArgsConstructor
public abstract class PeriodicJob extends UntypedActor {

    private final String actor;
    private final Object message;
    private final FiniteDuration interval;

    private Cancellable tick;

    @Override
    public void onReceive(Object incomingMessage) throws Exception {
        if (incomingMessage.equals(this.message)) {
            getContext().actorSelection("/user/" + actor).tell(incomingMessage,
                    getSelf());
        } else {
            unhandled(incomingMessage);
        }
    }

    @Override
    public void preStart() throws Exception {
        tick = start();
    }

    @Override
    public void postStop() {
        tick.cancel();
    }

    protected FiniteDuration getInitialDelay() {
        return interval;
    }

    private Cancellable start() {
        return getContext().system().scheduler().schedule(getInitialDelay(),
                interval, getSelf(), message, getContext().dispatcher(),
                ActorRef.noSender());
    }
}
