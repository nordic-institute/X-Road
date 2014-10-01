package ee.cyber.sdsb.common.util;

import lombok.RequiredArgsConstructor;
import scala.concurrent.duration.FiniteDuration;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.UntypedActor;

@RequiredArgsConstructor
public abstract class PeriodicJob extends UntypedActor {

    private final String actor;
    private final Object message;
    private final FiniteDuration interval;

    private Cancellable tick;

    @Override
    public void onReceive(Object message) throws Exception {
        if (message.equals(this.message)) {
            getContext().actorSelection("/user/" + actor).tell(message,
                    getSelf());
        } else {
            unhandled(message);
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
