package ee.cyber.sdsb.signer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import akka.actor.ActorSystem;
import akka.actor.Props;

import ee.cyber.sdsb.common.util.StartStop;
import ee.cyber.sdsb.signer.core.TokenManager;
import ee.cyber.sdsb.signer.core.token.TokenActorManager;
import ee.cyber.sdsb.signer.protocol.SignerRequestProcessor;

import static ee.cyber.sdsb.signer.protocol.ComponentNames.REQUEST_PROCESSOR;
import static ee.cyber.sdsb.signer.protocol.ComponentNames.TOKEN_ACTOR_MANAGER;

public class Signer implements StartStop {

    private static final Logger LOG =
            LoggerFactory.getLogger(Signer.class);

    private final ActorSystem actorSystem;

    public Signer(ActorSystem actorSystem) {
        this.actorSystem = actorSystem;
    }

    @Override
    public void start() throws Exception {
        LOG.trace("start()");

        // TODO: Start task for periodically reloading confs
        createComponent(TokenActorManager.class, TOKEN_ACTOR_MANAGER);
        createComponent(SignerRequestProcessor.class, REQUEST_PROCESSOR);
    }

    @Override
    public void stop() throws Exception {
        LOG.trace("stop()");

        TokenManager.saveToConf();
    }

    @Override
    public void join() throws InterruptedException {
        LOG.trace("join()");

    }

    private void createComponent(Class<?> clazz, String name) {
        actorSystem.actorOf(Props.create(clazz), name);
    }
}
