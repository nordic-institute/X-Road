/**
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.util.PeriodicJob;
import ee.ria.xroad.common.util.StartStop;
import ee.ria.xroad.common.util.filewatcher.FileWatcherRunner;
import ee.ria.xroad.signer.certmanager.OcspClientWorker;
import ee.ria.xroad.signer.certmanager.OcspResponseManager;
import ee.ria.xroad.signer.protocol.SignerRequestProcessor;
import ee.ria.xroad.signer.tokenmanager.TokenManager;
import ee.ria.xroad.signer.tokenmanager.module.AbstractModuleManager;
import ee.ria.xroad.signer.tokenmanager.module.DefaultModuleManagerImpl;
import ee.ria.xroad.signer.util.Update;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;

import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

import static ee.ria.xroad.common.SystemProperties.NodeType.SLAVE;
import static ee.ria.xroad.signer.protocol.ComponentNames.MODULE_MANAGER;
import static ee.ria.xroad.signer.protocol.ComponentNames.OCSP_CLIENT;
import static ee.ria.xroad.signer.protocol.ComponentNames.OCSP_CLIENT_JOB;
import static ee.ria.xroad.signer.protocol.ComponentNames.OCSP_CLIENT_RELOAD;
import static ee.ria.xroad.signer.protocol.ComponentNames.OCSP_RESPONSE_MANAGER;
import static ee.ria.xroad.signer.protocol.ComponentNames.REQUEST_PROCESSOR;

/**
 * Signer application.
 */
@Slf4j
@RequiredArgsConstructor
public class Signer implements StartStop {

    private static final String MODULE_MANAGER_IMPL_CLASS =
            SystemProperties.PREFIX + "signer.moduleManagerImpl";

    private static final int MODULE_MANAGER_UPDATE_INTERVAL_SECONDS = SystemProperties.getModuleManagerUpdateInterval();

    private static final FiniteDuration MODULE_MANAGER_UPDATE_INTERVAL =
            Duration.create(MODULE_MANAGER_UPDATE_INTERVAL_SECONDS, TimeUnit.SECONDS);

    private final ActorSystem actorSystem;

    private FileWatcherRunner keyConfFileWatcherRunner;

    @Override
    public void start() throws Exception {
        log.trace("start()");

        TokenManager.init();

        ActorRef moduleManager = createComponent(MODULE_MANAGER, getModuleManagerImpl());

        if (SLAVE.equals(SystemProperties.getServerNodeType())) {
            // when the key conf file is changed from outside this system (i.e. a new copy from master),
            // send an update event to the module manager so it knows to load the new config
            this.keyConfFileWatcherRunner = FileWatcherRunner.create()
                    .watchForChangesIn(Paths.get(SystemProperties.getKeyConfFile()))
                    .listenToCreate().listenToModify()
                    .andOnChangeNotify(() -> moduleManager.tell(new Update(), ActorRef.noSender()))
                    .buildAndStartWatcher();
        }

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

        if (!SLAVE.equals(SystemProperties.getServerNodeType())) {
            TokenManager.saveToConf();
        }

        if (this.keyConfFileWatcherRunner != null) {
            this.keyConfFileWatcherRunner.stop();
        }

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

        ModuleManagerJob() {
            super(MODULE_MANAGER, new Update(), MODULE_MANAGER_UPDATE_INTERVAL);
        }

        @Override
        protected FiniteDuration getInitialDelay() {
            return Duration.create(1, TimeUnit.SECONDS);
        }
    }

}
