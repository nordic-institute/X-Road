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
package ee.ria.xroad.signer.tokenmanager.module;

import java.util.List;

import lombok.extern.slf4j.Slf4j;
import scala.concurrent.duration.Duration;
import akka.actor.ActorRef;
import akka.actor.OneForOneStrategy;
import akka.actor.Props;
import akka.actor.SupervisorStrategy;

import ee.ria.xroad.signer.protocol.dto.TokenInfo;
import ee.ria.xroad.signer.tokenmanager.TokenManager;
import ee.ria.xroad.signer.tokenmanager.token.TokenType;
import ee.ria.xroad.signer.util.AbstractUpdateableActor;
import ee.ria.xroad.signer.util.Update;

/**
 * Module worker base class.
 */
@Slf4j
public abstract class AbstractModuleWorker extends AbstractUpdateableActor {

    @Override
    public SupervisorStrategy supervisorStrategy() {
        return new OneForOneStrategy(-1, Duration.create("1 minute"),
                (t) -> {
                    return SupervisorStrategy.resume();
                });
    }

    @Override
    public void preStart() throws Exception {
        try {
            initializeModule();
        } catch (Exception e) {
            log.error("Failed to initialize module", e);
            getContext().stop(getSelf());
        }
    }

    @Override
    public void postStop() throws Exception {
        try {
            deinitializeModule();
        } catch (Exception e) {
            log.error("Failed to deinitialize module", e);
        }
    }

    @Override
    protected void onUpdate() throws Exception {
        List<TokenType> tokens = listTokens();

        log.trace("Got {} tokens from module '{}'", tokens.size(),
                getSelf().path().name());

        updateTokens(tokens);
    }

    @Override
    protected void onMessage(Object message) throws Exception {
        unhandled(message);
    }

    protected abstract void initializeModule() throws Exception;
    protected abstract void deinitializeModule() throws Exception;

    protected abstract List<TokenType> listTokens() throws Exception;

    protected abstract Props props(TokenInfo tokenInfo, TokenType tokenType);

    private void updateTokens(List<TokenType> tokens) {
        // create new tokens
        for (TokenType tokenType : tokens) {
            if (!hasToken(tokenType)) {
                createToken(getTokenInfo(tokenType), tokenType);
            }
        }

        // cleanup lost tokens, update existing tokens
        for (ActorRef token : getContext().getChildren()) {
            if (!hasToken(tokens, token)) {
                destroyToken(token);
            } else {
                token.tell(new Update(), getSelf());
            }
        }
    }

    private boolean hasToken(List<TokenType> tokens, ActorRef token) {
        String tokenId = token.path().name();
        return tokens.stream().filter(t -> t.getId().equals(tokenId))
                .findFirst().isPresent();
    }

    private boolean hasToken(TokenType tokenType) {
        return getToken(tokenType) != null;
    }

    private ActorRef getToken(TokenType tokenType) {
        return getContext().getChild(tokenType.getId());
    }

    private ActorRef createToken(TokenInfo tokenInfo, TokenType tokenType) {
        log.debug("Adding new token '{}#{}'", tokenType.getModuleType(),
                tokenInfo.getId());

        ActorRef token = getContext().actorOf(props(tokenInfo, tokenType),
                tokenType.getId());
        getContext().watch(token);
        return token;
    }

    private void destroyToken(ActorRef token) {
        log.debug("Lost token '{}'", token.path().name());

        getContext().unwatch(token);
        getContext().stop(token);
    }

    private static TokenInfo getTokenInfo(TokenType tokenType) {
        TokenInfo info = TokenManager.getTokenInfo(tokenType.getId());
        if (info != null) {
            TokenManager.setTokenAvailable(tokenType, true);
            return info;
        } else {
            return TokenManager.createToken(tokenType);
        }
    }
}
