/**
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package ee.ria.xroad.signer.protocol;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.SystemProperties;

import akka.actor.ActorIdentity;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.actor.Identify;
import akka.actor.Props;
import akka.actor.Terminated;
import akka.actor.UntypedAbstractActor;
import akka.pattern.Patterns;
import akka.util.Timeout;
import lombok.extern.slf4j.Slf4j;
import scala.concurrent.Await;

import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.signer.protocol.ComponentNames.REQUEST_PROCESSOR;
import static ee.ria.xroad.signer.protocol.ComponentNames.SIGNER;
import static ee.ria.xroad.signer.protocol.SignerClient.SignerWatcher.signerRef;

/**
 * Signer client is used to send messages to signer from other components
 * (running as separate JVM processes).
 */
@Slf4j
public final class SignerClient {

    private static final Timeout TIMEOUT =
            Timeout.apply(SystemProperties.getSignerClientTimeout(), TimeUnit.MILLISECONDS);
    public static final String LOCALHOST_IP = "127.0.0.1";

    private SignerClient() {
    }

    /**
     * Initializes the client with the provided actor system.
     * @param system the actor system
     * @throws Exception if an error occurs
     */
    public static void init(ActorSystem system) {
        init(system, LOCALHOST_IP);
    }

    /**
     * Initializes the client with the provided actor system.
     * @param system          the actor system
     * @param signerIpAddress IP address for remote signer
     *                        or 127.0.0.1 for local signer
     * @throws Exception if an error occurs
     */
    public static void init(ActorSystem system, String signerIpAddress) {
        SignerWatcher.init(system, signerIpAddress);
    }

    /**
     * Forwards a message to the signer.
     * @param message  the message
     * @param receiver the receiver actor
     */
    public static void execute(Object message, ActorRef receiver) {
        signerRef().tell(message, receiver);
    }

    /**
     * Sends a message and waits for a response, returning it. If the response
     * is an exception, throws it.
     * @param <T>     the type of result
     * @param message the message
     * @return the response
     * @throws Exception if the response is an exception
     */
    public static <T> T execute(Object message) throws Exception {
        try {
            return result(Await.result(Patterns.ask(signerRef(), message, TIMEOUT), TIMEOUT.duration()));
        } catch (TimeoutException e) {
            throw new CodedException(X_INTERNAL_ERROR, e, "Request to Signer timed out");
        }
    }

    /**
     * Returns the object as the instance or throws exception, if the object
     * is throwable.
     * @param <T>    the type of result
     * @param result the result object
     * @return result
     * @throws Exception if the object is throwable
     */
    @SuppressWarnings("unchecked")
    public static <T> T result(Object result) throws Exception {
        if (result instanceof Throwable) {
            throw (Exception)result;
        } else {
            return (T)result;
        }
    }

    static class SignerWatcher extends UntypedAbstractActor {

        /*
         * Implementation notes.
         *
         * The requestProcessor future will be completed by the internally used
         * SignerWatcher actor, and replaced with a new one in case the Signer is restarted. The purpose is to avoid
         * long request timeouts when the signer is not (yet) available, and to detect restarts.
         *
         */
        private static volatile CompletableFuture<ActorRef> requestProcessor = null;
        private static final Duration WATCH_DELAY = Duration.ofSeconds(1);
        private static final int REF_GET_TIMEOUT = 7;

        static ActorRef signerRef() {
            final CompletableFuture<ActorRef> processor = requestProcessor;
            if (processor == null) {
                throw new IllegalStateException("SignerClient is not initialized");
            }
            try {
                return processor.get(REF_GET_TIMEOUT, TimeUnit.SECONDS);
            } catch (ExecutionException | TimeoutException | CancellationException e) {
                throw new CodedException(X_INTERNAL_ERROR, e, "Signer is unreachable");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new CodedException(X_INTERNAL_ERROR, e, "Request to signer was interrupted");
            }
        }

        static synchronized void init(ActorSystem system, String signerIpAddress) {
            if (requestProcessor == null) {
                requestProcessor = new CompletableFuture<>();
                system.actorOf(Props.create(SignerWatcher.class, signerIpAddress));
            }
        }

        private static synchronized void resetRequestProcessor(CompletableFuture<ActorRef> processor) {
            if (requestProcessor != null) {
                requestProcessor.cancel(true);
            }
            requestProcessor = processor;
        }

        private long correlationId = 0;
        private ActorRef signerRef;
        private ActorSelection signer;
        private final String signerIpAddress;

        interface Watch {
        }

        SignerWatcher(String signerIpAddress) {
            this.signerIpAddress = signerIpAddress;
        }

        @Override
        public void preStart() {
            signer = context().actorSelection(getSignerPath() + "/user/" + REQUEST_PROCESSOR);
            self().tell(Watch.class, self());
        }

        @Override
        public void postStop() {
            if (signerRef != null) {
                context().unwatch(signerRef);
            }
            resetRequestProcessor(null);
        }

        @Override
        public void onReceive(final Object message) {
            if (Watch.class == message) {
                if (signerRef == null) {
                    identifyAgent();
                    scheduleWatch();
                }
            } else if (message instanceof ActorIdentity) {
                attachSigner((ActorIdentity)message);
            } else if (message instanceof Terminated) {
                detachSigner((Terminated)message);
                scheduleWatch();
            } else {
                unhandled(message);
            }
        }

        private void scheduleWatch() {
            context().system().scheduler()
                    .scheduleOnce(WATCH_DELAY, self(), Watch.class, context().system().dispatcher(), self());
        }

        private void detachSigner(final Terminated message) {
            if (signerRef != null && signerRef.equals(message.getActor())) {
                log.warn("Signer detached");
                context().unwatch(signerRef);
                signerRef = null;
                resetRequestProcessor(new CompletableFuture<>());
            }
        }

        private void attachSigner(final ActorIdentity message) {
            if (message.correlationId().equals(correlationId)) {
                if (signerRef != null) {
                    context().unwatch(signerRef);
                }
                signerRef = message.getActorRef().orElse(null);
                if (signerRef != null) {
                    context().watch(signerRef);
                    if (!requestProcessor.complete(signerRef)) {
                        resetRequestProcessor(CompletableFuture.completedFuture(signerRef));
                    }
                    log.info("Signer attached");
                } else {
                    log.debug("Signer is unreachable");
                    resetRequestProcessor(new CompletableFuture<>());
                }
            }
        }

        private void identifyAgent() {
            correlationId++;
            signer.tell(new Identify(correlationId), self());
        }

        private String getSignerPath() {
            return "akka://" + SIGNER + "@" + signerIpAddress + ":" + SystemProperties.getSignerPort();
        }
    }
}
