package ee.ria.xroad.signer;

import java.util.concurrent.TimeUnit;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.util.PeriodicJob;
import ee.ria.xroad.common.util.StartStop;
import ee.ria.xroad.signer.certmanager.OcspClientWorker;
import ee.ria.xroad.signer.certmanager.OcspResponseManager;
import ee.ria.xroad.signer.protocol.SignerRequestProcessor;
import ee.ria.xroad.signer.tokenmanager.TokenManager;
import ee.ria.xroad.signer.tokenmanager.module.AbstractModuleManager;
import ee.ria.xroad.signer.tokenmanager.module.DefaultModuleManagerImpl;
import ee.ria.xroad.signer.util.Update;
import ee.ria.xroad.signer.util.VariableIntervalPeriodicJob;

import static ee.ria.xroad.signer.protocol.ComponentNames.*;

/**
 * Signer application.
 */
@Slf4j
@RequiredArgsConstructor
public class Signer implements StartStop {

    private static final String MODULE_MANAGER_IMPL_CLASS =
            SystemProperties.PREFIX + "signer.moduleManagerImpl";

    private static final FiniteDuration MODULE_MANAGER_UPDATE_INTERVAL =
            Duration.create(60, TimeUnit.SECONDS);

    private final ActorSystem actorSystem;

    @Override
    public void start() throws Exception {
        log.trace("start()");

        TokenManager.init();

        createComponent(MODULE_MANAGER, getModuleManagerImpl());
        createComponent(ModuleManagerJob.class);

        createComponent(REQUEST_PROCESSOR, SignerRequestProcessor.class);

        createComponent(OCSP_RESPONSE_MANAGER, OcspResponseManager.class);
        createComponent(OCSP_CLIENT, OcspClientWorker.class);
        createComponent(OcspClientJob.class);
    }

    @Override
    public void stop() throws Exception {
        log.trace("stop()");

        TokenManager.saveToConf();
    }

    @Override
    public void join() throws InterruptedException {
        log.trace("join()");
    }

    private ActorRef createComponent(Class<?> clazz, Object... arg) {
        return createComponent(clazz.getName(), clazz, arg);
    }

    private ActorRef createComponent(String name, Class<?> clazz,
            Object... arg) {
        return actorSystem.actorOf(Props.create(clazz, arg), name);
    }

    @SuppressWarnings("unchecked")
    private Class<? extends AbstractModuleManager> getModuleManagerImpl() {
        String moduleManagerImplClassName =
                System.getProperty(MODULE_MANAGER_IMPL_CLASS,
                        DefaultModuleManagerImpl.class.getName());
        log.debug("Using module manager implementation: {}",
                moduleManagerImplClassName);
        try {
            Class<?> clazz = Class.forName(moduleManagerImplClassName);
            return (Class<? extends AbstractModuleManager>) clazz;
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Could not load module manager impl: "
                    + moduleManagerImplClassName, e);
        }
    }

    /**
     * Periodically updates the ModuleManager
     */
    private static class ModuleManagerJob extends PeriodicJob {

        public ModuleManagerJob() {
            super(MODULE_MANAGER, new Update(), MODULE_MANAGER_UPDATE_INTERVAL);
        }

        @Override
        protected FiniteDuration getInitialDelay() {
            return Duration.create(1, TimeUnit.SECONDS);
        }
    }

    /**
     * Periodically executes the OcspClient
     */
    private static class OcspClientJob extends VariableIntervalPeriodicJob {

        private static final FiniteDuration INITIAL_DELAY =
                FiniteDuration.create(100, TimeUnit.MILLISECONDS);

        public OcspClientJob() {
            super(OCSP_CLIENT, OcspClientWorker.EXECUTE);
        }

        @Override
        protected FiniteDuration getInitialDelay() {
            return INITIAL_DELAY;
        }

        @Override
        protected FiniteDuration getNextDelay() {
            return FiniteDuration.create(
                    OcspClientWorker.getNextOcspFreshnessSeconds(),
                    TimeUnit.SECONDS);
        }
    }
}
