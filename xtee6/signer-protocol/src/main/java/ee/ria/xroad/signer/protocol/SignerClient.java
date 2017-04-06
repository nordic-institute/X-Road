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
package ee.ria.xroad.signer.protocol;

import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.actor.ActorSystem;
import akka.pattern.Patterns;
import akka.util.Timeout;
import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.SystemProperties;
import lombok.extern.slf4j.Slf4j;
import scala.concurrent.Await;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static ee.ria.xroad.common.ErrorCodes.X_HTTP_ERROR;
import static ee.ria.xroad.signer.protocol.ComponentNames.REQUEST_PROCESSOR;
import static ee.ria.xroad.signer.protocol.ComponentNames.SIGNER;

/**
 * Signer client is used to send messages to signer from other components
 * (running as separate JVM processes).
 */
@Slf4j
public final class SignerClient {

    private static final int TIMEOUT_MILLIS =
            SystemProperties.getSignerClientTimeout();

    private static ActorSystem actorSystem;
    private static ActorSelection requestProcessor;

    private SignerClient() {
    }

    /**
     * Initializes the client with the provided actor system.
     * @param system the actor system
     * @throws Exception if an error occurs
     */
    public static void init(ActorSystem system) throws Exception {
        log.trace("init()");

        if (SignerClient.actorSystem == null) {
            SignerClient.actorSystem = system;

            requestProcessor = system.actorSelection(
                    getSignerPath() + "/user/" + REQUEST_PROCESSOR);

        }
    }

    /**
     * Forwards a message to the signer.
     * @param message the message
     * @param receiver the receiver actor
     */
    public static void execute(Object message, ActorRef receiver) {
        verifyInitialized();
        requestProcessor.tell(message, receiver);
    }

    /**
     * Sends a message and waits for a response, returning it. If the response
     * is an exception, throws it.
     * @param <T> the type of result
     * @param message the message
     * @return the response
     * @throws Exception if the response is an exception
     */
    public static <T> T execute(Object message) throws Exception {
        verifyInitialized();

        final Timeout timeout = Timeout.apply(TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        try {
            return result(Await.result(Patterns.ask(requestProcessor, message, timeout), timeout.duration()));
        } catch (TimeoutException te) {
            throw connectionTimeoutException(te);
        }
    }

    /**
     * Returns the object as the instance or throws exception, if the object
     * is throwable.
     * @param <T> the type of result
     * @param result the result object
     * @return result
     * @throws Exception if the object is throwable
     */
    @SuppressWarnings("unchecked")
    public static <T> T result(Object result) throws Exception {
        if (result instanceof Throwable) {
            throw (Exception) result;
        } else {
            return (T) result;
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

    private static CodedException connectionTimeoutException(Exception e) {
        return new CodedException(X_HTTP_ERROR, e,
                "Connection to Signer (port %s) timed out",
                SystemProperties.getSignerPort());
    }

}
