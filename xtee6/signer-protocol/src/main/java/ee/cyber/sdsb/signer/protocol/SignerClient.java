package ee.cyber.sdsb.signer.protocol;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import lombok.AccessLevel;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.joda.time.DateTime;

import scala.concurrent.duration.FiniteDuration;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.actor.Cancellable;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.util.Timeout;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.signer.protocol.message.ConnectionPing;
import ee.cyber.sdsb.signer.protocol.message.ConnectionPong;

import static ee.cyber.sdsb.common.ErrorCodes.X_HTTP_ERROR;
import static ee.cyber.sdsb.signer.protocol.ComponentNames.REQUEST_PROCESSOR;
import static ee.cyber.sdsb.signer.protocol.ComponentNames.SIGNER;

@Slf4j
public final class SignerClient {

    private static final Timeout DEFAULT_TIMEOUT =
            new Timeout(SystemProperties.getSignerClientTimeout());

    private static ActorSystem actorSystem;
    private static ActorSelection requestProcessor;

    private static Boolean connected;

    private SignerClient() {
    }

    public static void init(ActorSystem actorSystem) throws Exception {
        log.debug("init()");

        if (SignerClient.actorSystem == null) {
            SignerClient.actorSystem = actorSystem;

            requestProcessor = actorSystem.actorSelection(
                    getSignerPath() + "/user/" + REQUEST_PROCESSOR);

            actorSystem.actorOf(Props.create(ConnectionPinger.class),
                    "ConnectionPinger");
        }
    }

    public static void execute(Object message, ActorRef receiver)
            throws Exception {
        verifyInitialized();
        verifyConnected();

        CountDownLatch latch = new CountDownLatch(1);

        ActorRef executionCtx = actorSystem.actorOf(
                Props.create(ReceiverExecutionCtx.class, latch, receiver));

        requestProcessor.tell(message, executionCtx);
        try {
            waitForResponse(latch);
        } finally {
            executionCtx.tell(PoisonPill.getInstance(), ActorRef.noSender());
        }
    }

    public static <T> T execute(Object message) throws Exception {
        verifyInitialized();
        verifyConnected();

        CountDownLatch latch = new CountDownLatch(1);
        Response response = new Response();

        ActorRef executionCtx = actorSystem.actorOf(
                Props.create(ResponseExecutionCtx.class, latch, response));

        requestProcessor.tell(message, executionCtx);
        try {
            waitForResponse(latch);
            return result(response.getValue());
        } finally {
            executionCtx.tell(PoisonPill.getInstance(), ActorRef.noSender());
        }
    }

    @SuppressWarnings("unchecked")
    public static <T> T result(Object result) throws Exception {
        if (result instanceof Throwable) {
            throw (Exception) result;
        } else {
            return (T) result;
        }
    }

    private static void waitForResponse(CountDownLatch latch) {
        try {
            if (!latch.await(DEFAULT_TIMEOUT.duration().length(),
                    TimeUnit.MILLISECONDS)) {
                throw new TimeoutException();
            }
        } catch (Exception e) {
            throw couldNotConnectException();
        }
    }

    private static String getSignerPath() {
        return "akka.tcp://" + SIGNER + "@127.0.0.1:"
                + SystemProperties.getSignerPort();
    }

    private static void verifyInitialized() {
        if (actorSystem == null) {
            throw new IllegalStateException("SignerClient is not initialized");
        }
    }

    private static void verifyConnected() {
        if (connected != null && !connected) {
            throw couldNotConnectException();
        }
    }

    private static CodedException couldNotConnectException() {
        return new CodedException(X_HTTP_ERROR,
                "Could not connect to Signer (port %s)",
                SystemProperties.getSignerPort());
    }

    @Data
    private static class Response {
        private Object value;
    }

    @RequiredArgsConstructor(access = AccessLevel.PACKAGE)
    private static class ReceiverExecutionCtx extends UntypedActor {

        private final CountDownLatch latch;
        private final ActorRef receiver;

        @Override
        public void onReceive(Object message) throws Exception {
            log.trace("onReceive({})", message);

            if (receiver != ActorRef.noSender()) {
                receiver.tell(message, getSender());
            }

            latch.countDown();
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PACKAGE)
    private static class ResponseExecutionCtx extends UntypedActor {

        private final CountDownLatch latch;
        private final Response response;

        @Override
        public void onReceive(Object message) throws Exception {
            log.trace("onReceive({})", message);

            response.setValue(message);
            latch.countDown();
        }
    }

    @RequiredArgsConstructor(access = AccessLevel.PACKAGE)
    private static class ConnectionPinger extends UntypedActor {

        private final FiniteDuration interval =
                FiniteDuration.create(5, TimeUnit.SECONDS);

        private Cancellable tick;
        private DateTime lastPong;

        @Override
        public void preStart() throws Exception {
            tick = start();
        }

        @Override
        public void postStop() {
            tick.cancel();
        }

        @Override
        public void onReceive(Object message) throws Exception {
            if (message instanceof ConnectionPing) {
                requestProcessor.tell(message, getSelf());
                checkLastPong();
            } else if (message instanceof ConnectionPong) {
                connected = true;
                lastPong = new DateTime();
            }
        }

        private void checkLastPong() {
            if (lastPong == null || hasTimedOut()) {
                connected = false;
            }
        }

        private boolean hasTimedOut() {
            long now = new DateTime().getMillis();
            long diff = now - lastPong.getMillis();
            return diff > DEFAULT_TIMEOUT.duration().toMillis();
        }

        private Cancellable start() {
            return getContext().system().scheduler().schedule(
                    FiniteDuration.create(100, TimeUnit.MILLISECONDS),
                    interval, getSelf(), new ConnectionPing(),
                    getContext().dispatcher(), ActorRef.noSender());
        }
    }
}
