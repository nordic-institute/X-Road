package ee.cyber.sdsb.signer.util;

import java.util.concurrent.TimeUnit;

import lombok.RequiredArgsConstructor;
import scala.concurrent.duration.FiniteDuration;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.UntypedActor;

/**
 * Periodic job with potentialli variable intereval. The next interval is
 * calculated after each time the job is run.
 */
@RequiredArgsConstructor
public abstract class VariableIntervalPeriodicJob extends UntypedActor {

    private final String actor;
    private final Object message;

    private Cancellable nextSend;

    @Override
    public void onReceive(Object incoming) throws Exception {
        if (incoming.equals(this.message)) {
            getContext().actorSelection("/user/" + actor).tell(incoming,
                    getSelf());
            scheduleNextSend(getNextDelay());
        } else {
            unhandled(incoming);
        }
    }

    @Override
    public void preStart() throws Exception {
        scheduleNextSend(getInitialDelay());
    }

    @Override
    public void postStop() {
        if (nextSend != null) {
            nextSend.cancel();
        }
    }

    protected void scheduleNextSend(FiniteDuration delay) {
        nextSend = getContext().system().scheduler().scheduleOnce(delay,
                this::sendMessage, getContext().dispatcher());
    }

    protected void sendMessage() {
        getSelf().tell(message, ActorRef.noSender());
    }

    protected FiniteDuration getInitialDelay() {
        return FiniteDuration.create(1, TimeUnit.SECONDS);
    }

    protected abstract FiniteDuration getNextDelay();
}
