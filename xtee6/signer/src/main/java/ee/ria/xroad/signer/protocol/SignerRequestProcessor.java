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

import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.signer.protocol.message.ConnectionPing;
import ee.ria.xroad.signer.protocol.message.ConnectionPong;

import static ee.ria.xroad.common.ErrorCodes.*;

/**
 * Request handler will handle all incoming requests...
 */
@Slf4j
public class SignerRequestProcessor extends UntypedActor {

    private static final String HANDLER_PACKAGE_NAME =
            "ee.ria.xroad.signer.protocol.handler.";
    private static final String HANDLER_CLASS_SUFFIX = "RequestHandler";

    private static Map<String, Class<? extends AbstractRequestHandler<?>>>
            handlerClassCache = new HashMap<>();

    @Override
    public void onReceive(Object message) throws Exception {
        if (message instanceof ConnectionPing) {
            getSender().tell(new ConnectionPong(), getSelf());
            return;
        }

        log.trace("onReceive({})", message);
        try {
            handle(message);
        } catch (Exception e) {
            log.error("Error in request processor", e);
        }
    }

    private void handle(Object message) {
        try {
            // For handling the request, create a temporary actor, that will
            // stop itself, after it has finished handling the message
            Class<? extends AbstractRequestHandler<?>> handlerClass =
                    getRequestHandler(message);
            if (handlerClass != null) {
                ActorRef handlerActor =
                        getContext().actorOf(Props.create(handlerClass));
                handlerActor.tell(message, getSender());
            } else {
                throw new CodedException(X_INTERNAL_ERROR, "Unknown request");
            }
        } catch (Throwable e) { // We want to catch serious errors as well
            log.error("Error in request processor", e);

            if (getSender() != ActorRef.noSender()) {
                CodedException translated =
                        translateException(e).withPrefix(SIGNER_X);
                getSender().tell(translated, getSelf());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private Class<? extends AbstractRequestHandler<?>> getRequestHandler(
            Object message) throws Exception {
        String handlerName = message.getClass().getSimpleName()
                + HANDLER_CLASS_SUFFIX;
        String handlerClass = HANDLER_PACKAGE_NAME + handlerName;

        if (handlerClassCache.containsKey(handlerClass)) {
            return handlerClassCache.get(handlerClass);
        }

        log.trace("Looking for request processor '{}'", handlerClass);
        try {
            Class<?> clazz = Class.forName(handlerClass);

            if (AbstractRequestHandler.class.isAssignableFrom(clazz)) {
                Class<? extends AbstractRequestHandler<?>> h =
                        (Class<? extends AbstractRequestHandler<?>>) clazz;
                handlerClassCache.put(handlerClass, h);
                return h;
            } else {
                log.error("Invalid request handler '{}'; must be subclass"
                        + " of {}", clazz, AbstractRequestHandler.class);
                return null;
            }
        } catch (Exception e) {
            log.error("Error while getting request handler", e);
            return null;
        }
    }
}
