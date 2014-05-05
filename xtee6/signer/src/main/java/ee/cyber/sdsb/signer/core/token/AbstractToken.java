package ee.cyber.sdsb.signer.core.token;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorRef;
import akka.actor.Props;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.signer.core.SignerActor;
import ee.cyber.sdsb.signer.core.TokenManager;
import ee.cyber.sdsb.signer.protocol.dto.TokenInfo;
import ee.cyber.sdsb.signer.protocol.message.ActivateToken;
import ee.cyber.sdsb.signer.protocol.message.Sign;
import ee.cyber.sdsb.signer.util.Update;

import static ee.cyber.sdsb.common.ErrorCodes.*;
import static ee.cyber.sdsb.signer.protocol.ComponentNames.TOKEN_SIGNER;
import static ee.cyber.sdsb.signer.protocol.ComponentNames.TOKEN_WORKER;
import static ee.cyber.sdsb.signer.util.SignerUtil.getWorkerId;

public abstract class AbstractToken extends SignerActor {

    private static final Logger LOG =
            LoggerFactory.getLogger(AbstractToken.class);

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
        LOG.trace("onMessage()");

        if (!isTokenActive(message)) {
            sendErrorResponse(new CodedException(X_TOKEN_NOT_ACTIVE,
                    "Token '%s' not active", getWorkerId(tokenInfo)));
            return;
        }

        if (message instanceof Sign) {
            if (signer != null) {
                signer.tell(message, getSender());
            } else {
                sendErrorResponse(new CodedException(X_TOKEN_NOT_ACTIVE,
                        "Cannot sign, signing actor of token '%s' " +
                        "not initialized", getWorkerId(tokenInfo)));
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
        if (message instanceof Update || message instanceof ActivateToken) {
            return true;
        }

        return TokenManager.isTokenActive(tokenInfo.getId());
    }

    void sendErrorResponse(CodedException e) {
        LOG.error(e.getMessage());
        sendResponse(e);
    }
}
