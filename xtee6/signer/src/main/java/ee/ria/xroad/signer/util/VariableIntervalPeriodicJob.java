/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.signer.util;

import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.UntypedActor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import scala.concurrent.duration.FiniteDuration;

import java.util.concurrent.TimeUnit;

/**
 * Periodic job with potentially variable interval. The next interval is
 * calculated after each time the job is run.
 */
@Slf4j
@RequiredArgsConstructor
public abstract class VariableIntervalPeriodicJob extends UntypedActor {

    private final String actor;
    private final Object message;

    private Cancellable nextSend;

    @Override
    public void onReceive(Object incoming) throws Exception {
        if (incoming.equals(this.message)) {
            log.debug("received message {}", this.message);
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
        cancelNextSend();
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

    protected void cancelNextSend() {
        if (nextSend != null) {
            if (!nextSend.isCancelled()) {
                log.debug("cancelling nextSend");
                boolean result = nextSend.cancel();
                log.debug("cancel called, return value: {}", result);
            }
        }
    }
}
