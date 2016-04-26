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
package ee.ria.xroad.signer.tokenmanager.token;

import akka.actor.ActorRef;
import akka.actor.Props;
import lombok.extern.slf4j.Slf4j;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.signer.protocol.dto.TokenInfo;
import ee.ria.xroad.signer.protocol.message.ActivateToken;
import ee.ria.xroad.signer.protocol.message.InitSoftwareToken;
import ee.ria.xroad.signer.protocol.message.Sign;
import ee.ria.xroad.signer.tokenmanager.TokenManager;
import ee.ria.xroad.signer.util.AbstractSignerActor;
import ee.ria.xroad.signer.util.Update;

import static ee.ria.xroad.common.ErrorCodes.*;
import static ee.ria.xroad.signer.protocol.ComponentNames.TOKEN_SIGNER;
import static ee.ria.xroad.signer.protocol.ComponentNames.TOKEN_WORKER;
import static ee.ria.xroad.signer.util.ExceptionHelper.tokenNotActive;
import static ee.ria.xroad.signer.util.SignerUtil.getWorkerId;

/**
 * Token base class.
 */
@Slf4j
public abstract class AbstractToken extends AbstractSignerActor {

    protected final TokenInfo tokenInfo;

    protected ActorRef signer;
    protected ActorRef worker;

    AbstractToken(TokenInfo tokenInfo) {
        this.tokenInfo = tokenInfo;
    }

    @Override
    public void preStart() throws Exception {
        worker = createWatchedActor(createWorker(), TOKEN_WORKER);
        signer = createWatchedActor(createSigner(), TOKEN_SIGNER);
    }

    @Override
    public void postStop() throws Exception {
        stopWatchedActor(signer);
        stopWatchedActor(worker);
    }

    @Override
    public void onReceive(Object message) throws Exception {
        log.trace("onMessage()");

        if (!isTokenActive(message)) {
            sendErrorResponse(tokenNotActive(getWorkerId(tokenInfo)));
            return;
        }

        if (message instanceof Sign) {
            if (signer != null) {
                signer.tell(message, getSender());
            } else {
                sendErrorResponse(new CodedException(X_INTERNAL_ERROR,
                        "Cannot sign, signing actor of token '%s' "
                                + "not initialized", getWorkerId(tokenInfo)));
            }
        } else {
            if (worker != null) {
                worker.tell(message, getSender());
            } else {
                unhandled(message);
            }
        }
    }

    @Override
    protected CodedException translateError(Exception e) {
        return translateException(e).withPrefix(SIGNER_X);
    }

    protected abstract Props createSigner();
    protected abstract Props createWorker();

    ActorRef createWatchedActor(Props props, String name) {
        ActorRef actor = getContext().actorOf(props, name);

        getContext().watch(actor);

        return actor;
    }

    void stopWatchedActor(ActorRef actor) {
        getContext().unwatch(actor);
        getContext().stop(actor);
    }

    boolean isTokenActive(Object message) {
        if (message instanceof Update
                || message instanceof ActivateToken
                || message instanceof InitSoftwareToken) {
            return true;
        }

        return TokenManager.isTokenActive(tokenInfo.getId());
    }

    void sendErrorResponse(CodedException e) {
        log.error(e.getMessage());
        sendResponse(e);
    }
}
