package ee.cyber.sdsb.signer.protocol;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import lombok.Data;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.actor.PoisonPill;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.util.Timeout;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.SystemProperties;

import static ee.cyber.sdsb.common.ErrorCodes.X_HTTP_ERROR;
import static ee.cyber.sdsb.signer.protocol.ComponentNames.REQUEST_PROCESSOR;
import static ee.cyber.sdsb.signer.protocol.ComponentNames.SIGNER;

public final class SignerClient {

    private static final Logger LOG =
            LoggerFactory.getLogger(SignerClient.class);

    private static final Timeout DEFAULT_TIMEOUT =
            new Timeout(SystemProperties.getSignerClientTimeout());

    private static ActorSystem actorSystem;

    public SignerClient() {
    }

    public static void init(ActorSystem actorSystem) throws Exception {
        LOG.debug("init()");

        if (SignerClient.actorSystem == null) {
            SignerClient.actorSystem = actorSystem;
        }
    }

    public static void execute(Object message, ActorRef receiver)
            throws Exception {
        verifyInitialized();

        CountDownLatch latch = new CountDownLatch(1);

        ActorRef executionCtx = actorSystem.actorOf(
                Props.create(ReceiverExecutionCtx.class, latch, receiver));

        getRequestProcessor().tell(message, executionCtx);
        try {
            waitForResponse(latch);
        } finally {
            executionCtx.tell(PoisonPill.getInstance(), ActorRef.noSender());
        }
    }

    public static <T> T execute(Object message) throws Exception {
        verifyInitialized();

        CountDownLatch latch = new CountDownLatch(1);
        Response response = new Response();

        ActorRef executionCtx = actorSystem.actorOf(
                Props.create(ResponseExecutionCtx.class, latch, response));

        getRequestProcessor().tell(message, executionCtx);
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
            throw new CodedException(X_HTTP_ERROR,
                    "Could not connect to Signer (port %s)",
                    SystemProperties.getSignerPort());
        }
    }

    private static String getSignerPath() {
        return "akka.tcp://" + SIGNER + "@127.0.0.1:"
                + SystemProperties.getSignerPort();
    }

    private static ActorSelection getRequestProcessor() {
        return actorSystem.actorSelection(
                getSignerPath() + "/user/" + REQUEST_PROCESSOR);
    }

    private static void verifyInitialized() {
        if (actorSystem == null) {
            throw new IllegalStateException("SignerClient is not initialized");
        }
    }

    @Data
    private static class Response {
        private Object value;
    }

    private static class ReceiverExecutionCtx extends UntypedActor {

        private final CountDownLatch latch;
        private final ActorRef receiver;

        @SuppressWarnings("unused")
        ReceiverExecutionCtx(CountDownLatch latch, ActorRef receiver) {
            this.latch = latch;
            this.receiver = receiver;
        }

        @Override
        public void onReceive(Object message) throws Exception {
            LOG.trace("onReceive({})", message);

            if (receiver != ActorRef.noSender()) {
                receiver.tell(message, getSender());
            }

            latch.countDown();
        }
    }

    private static class ResponseExecutionCtx extends UntypedActor {

        private final CountDownLatch latch;
        private final Response response;

        @SuppressWarnings("unused")
        ResponseExecutionCtx(CountDownLatch latch, Response response) {
            this.latch = latch;
            this.response = response;
        }

        @Override
        public void onReceive(Object message) throws Exception {
            LOG.trace("onReceive({})", message);

            response.setValue(message);
            latch.countDown();
        }
    }
}
