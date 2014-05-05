package ee.cyber.sdsb.signer.core.token;

import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.concurrent.duration.Duration;
import akka.actor.ActorRef;
import akka.actor.Cancellable;
import akka.actor.OneForOneStrategy;
import akka.actor.Props;
import akka.actor.SupervisorStrategy;
import akka.actor.SupervisorStrategy.Directive;
import akka.japi.Function;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.signer.core.SignerActor;
import ee.cyber.sdsb.signer.core.TokenManager;
import ee.cyber.sdsb.signer.core.device.SoftwareTokenType;
import ee.cyber.sdsb.signer.core.device.TokenType;
import ee.cyber.sdsb.signer.protocol.dto.TokenInfo;
import ee.cyber.sdsb.signer.protocol.message.ActivateToken;
import ee.cyber.sdsb.signer.protocol.message.GenerateKey;
import ee.cyber.sdsb.signer.protocol.message.Sign;
import ee.cyber.sdsb.signer.util.Update;

import static ee.cyber.sdsb.common.ErrorCodes.*;

public class TokenActorManager extends SignerActor {

    // TODO: Make configurable?
    private static final int UPDATE_INITIAL_DELAY = 1000;
    private static final int UPDATE_INTERVAL = 10000;

    private static final Logger LOG =
            LoggerFactory.getLogger(TokenActorManager.class);

    // Will call update periodically
    private final Cancellable tick = createTicker();

    // TODO:
    private static SupervisorStrategy strategy =
            new OneForOneStrategy(10, Duration.create("1 minute"),
                new Function<Throwable, Directive>() {
                    @Override
                    public Directive apply(Throwable t) {
                        if (t instanceof CodedException) {
                            return SupervisorStrategy.resume();
                        } else {
                            return SupervisorStrategy.escalate();
                        }
                    }
                });

    @Override
    public SupervisorStrategy supervisorStrategy() {
        return strategy;
    }

    @Override
    public void onReceive(Object message) throws Exception {
        LOG.trace("onReceive({})", message);
        try {
            if (message instanceof Update) {
                handleUpdate();
            } else if (message instanceof ActivateToken) {
                tellTokenActor(message,
                        ((ActivateToken) message).getTokenId());
            } else if (message instanceof GenerateKey) {
                tellTokenActor(message,
                        ((GenerateKey) message).getTokenId());
            } else if (message instanceof Sign) {
                tellTokenActor(message, TokenManager.findTokenIdForKeyId(
                        ((Sign) message).getKeyId()));
            } else {
                unhandled(message);
            }
        } catch (Exception e) {
            LOG.error(e.getMessage());
            sendResponse(translateException(e).withPrefix(SIGNER_X));
        }
    }

    @Override
    public void postStop() {
        tick.cancel();
    }

    private Cancellable createTicker() {
        return getContext().system().scheduler().schedule(
                Duration.create(UPDATE_INITIAL_DELAY, TimeUnit.MILLISECONDS),
                Duration.create(UPDATE_INTERVAL, TimeUnit.MILLISECONDS),
                getSelf(), new Update(),
                getContext().dispatcher(), ActorRef.noSender());
    }

    private void handleUpdate() {
        LOG.trace("handleUpdate()");
        try {
            TokenManager.update(updateCallback());
            TokenManager.saveToConf();
        } catch (Exception e) {
            LOG.error("Error updating tokens", e);
        }

        // Update all workers
        getContext().actorSelection("*").tell(new Update(), getSelf());
    }

    private void tellTokenActor(Object message, String tokenId) {
        ActorRef tokenActor = getTokenActor(tokenId);
        if (tokenActor != null) {
            tokenActor.tell(message, getSender());
        } else {
            throw new CodedException(X_TOKEN_NOT_FOUND,
                    "Token with id '%s' not found", tokenId);
        }
    }

    private boolean hasTokenActor(String id) {
        return getTokenActor(id) != null;
    }

    private ActorRef getTokenActor(String id) {
        if (id == null) {
            return null;
        }

        LOG.trace("getTokenActor({})", id);

        return getContext().getChild(id);
    }

    private ActorRef startTokenActor(Props props, String name) {
        ActorRef actor = getContext().actorOf(props, name);
        getContext().watch(actor);
        return actor;
    }

    private void stopTokenActor(String name) {
        ActorRef worker = getTokenActor(name);
        if (worker != null) {
            LOG.debug("Stopping token actor '{}'", name);

            getContext().unwatch(worker);
            getContext().stop(worker);
        } else {
            LOG.warn("Cannot stop token actor '{}', actor not found", name);
        }
    }

    private static Props props(TokenInfo tokenInfo, TokenType tokenType) {
        Class<?> clazz = (tokenType instanceof SoftwareTokenType)
                ? SoftwareToken.class : SscdToken.class;
        return Props.create(clazz, tokenInfo, tokenType);
    }

    private TokenManager.TokenUpdateCallback updateCallback() {
        return new TokenManager.TokenUpdateCallback() {
            @Override
            public void tokenRemoved(String tokenId) {
                stopTokenActor(tokenId);
            }

            @Override
            public void tokenAdded(TokenInfo tokenInfo, TokenType tokenType) {
                if (!hasTokenActor(tokenInfo.getId())) {
                    LOG.debug("Creating new token actor for token '{}#{}'",
                            tokenInfo.getType(), tokenInfo.getId());
                    startTokenActor(props(tokenInfo, tokenType),
                            tokenInfo.getId());
                }
            }
        };
    }
}
