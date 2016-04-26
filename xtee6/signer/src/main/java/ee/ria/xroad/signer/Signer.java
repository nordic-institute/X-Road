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
package ee.ria.xroad.signer;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.util.concurrent.TimeUnit;

import static ee.ria.xroad.signer.protocol.ComponentNames.*;

/**
 * Signer application.
 */
@Slf4j
@RequiredArgsConstructor
public class Signer implements StartStop {

    private static final String MODULE_MANAGER_IMPL_CLASS =
            SystemProperties.PREFIX + "signer.moduleManagerImpl";

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
        createComponent(OCSP_CLIENT_JOB, OcspClientJob.class);
        createComponent(OCSP_CLIENT_RELOAD, OcspClientReload.class);
    }

    /**
     * Executes polling immediately
     */
    public void execute() throws Exception {
        log.trace("sending cancel");
        actorSystem.actorSelection("/user/" + OCSP_CLIENT_JOB).tell(OcspClientJob.CANCEL, ActorRef.noSender());
        log.trace("sending execute");
        actorSystem.actorSelection("/user/" + OCSP_CLIENT_JOB).tell(OcspClientWorker.EXECUTE, ActorRef.noSender());
        log.trace("done");
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

        private static final FiniteDuration MODULE_MANAGER_INTERVAL =
                Duration.create(60, TimeUnit.SECONDS);

        ModuleManagerJob() {
            super(MODULE_MANAGER, new Update(), MODULE_MANAGER_INTERVAL);
        }

        @Override
        protected FiniteDuration getInitialDelay() {
            return Duration.create(1, TimeUnit.SECONDS);
        }
    }

}
