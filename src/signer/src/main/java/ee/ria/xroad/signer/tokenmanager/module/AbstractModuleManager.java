/**
 * The MIT License
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
package ee.ria.xroad.signer.tokenmanager.module;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.signer.model.Cert;
import ee.ria.xroad.signer.protocol.message.GetOcspResponses;
import ee.ria.xroad.signer.tokenmanager.ServiceLocator;
import ee.ria.xroad.signer.tokenmanager.TokenManager;
import ee.ria.xroad.signer.util.AbstractUpdateableActor;

import akka.actor.ActorRef;
import akka.actor.OneForOneStrategy;
import akka.actor.Props;
import akka.actor.SupervisorStrategy;
import akka.pattern.Backoff;
import akka.pattern.BackoffSupervisor;
import iaik.pkcs.pkcs11.wrapper.PKCS11Exception;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import scala.concurrent.duration.Duration;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static ee.ria.xroad.common.SystemProperties.NodeType.SLAVE;
import static java.util.Objects.requireNonNull;

/**
 * Module manager base class.
 */
@Slf4j
public abstract class AbstractModuleManager extends AbstractUpdateableActor {

    private final SystemProperties.NodeType serverNodeType = SystemProperties.getServerNodeType();

    private static final Long BACKOFF_MIN_SECONDS = 10L;
    private static final Long BACKOFF_MAX_SECONDS = 600L;

    @Override
    public SupervisorStrategy supervisorStrategy() {
        return new OneForOneStrategy(-1, Duration.Inf(),
                throwable -> {
                    if (throwable instanceof PKCS11Exception) {
                        // PKCS11Exceptions should make the module reinitialized
                        return SupervisorStrategy.restart();
                    } else {
                        return SupervisorStrategy.resume();
                    }
                }
        );
    }

    @Override
    protected void onUpdate() throws Exception {
        loadModules();

        if (SLAVE.equals(serverNodeType)) {
            mergeConfiguration();
        }

        if (!SLAVE.equals(serverNodeType)) {
            persistConfiguration();
        }
    }

    @Override
    public void onMessage(Object message) throws Exception {
        unhandled(message);
    }

    protected abstract void initializeModule(ModuleType module);

    private void loadModules() throws Exception {
        log.trace("loadModules()");

        if (!ModuleConf.hasChanged()) {
            // do not reload, if conf has not changed
            return;
        }

        ModuleConf.reload();

        Collection<ModuleType> modules = ModuleConf.getModules();
        addNewModules(modules);
        removeLostModules(modules);
    }

    private void persistConfiguration() {
        try {
            TokenManager.saveToConf();
        } catch (Exception e) {
            log.error("Failed to save conf", e);
        }
    }

    private void mergeConfiguration() {
        TokenManager.merge(addedCerts -> {
            if (!addedCerts.isEmpty()) {
                log.info("Requesting OCSP update for new certificates obtained in key configuration merge.");

                ServiceLocator.getOcspResponseManager(getContext()).tell(mapCertListToGetOcspResponses(addedCerts),
                        ActorRef.noSender());
            }
        });
    }

    private static GetOcspResponses mapCertListToGetOcspResponses(List<Cert> certs) {
        requireNonNull(certs);

        return new GetOcspResponses(certs.stream().map(cert -> {
            try {
                return CryptoUtils.calculateCertHexHash(cert.getCertificate());
            } catch (Exception e) {
                log.error("Failed to calculate hash for new certificate {}", cert, e);

                return null;
            }
        }).filter(Objects::nonNull).toArray(String[]::new));
    }

    private void addNewModules(Collection<ModuleType> modules) {
        modules.forEach(this::initializeModule);
    }

    private void removeLostModules(Collection<ModuleType> modules) {
        for (ActorRef module : getContext().getChildren()) {
            String moduleId = module.path().name();

            if (!containsModule(moduleId, modules)) {
                deinitializeModuleWorker(moduleId);
            }
        }
    }

    void initializeModuleWorker(ModuleType moduleType, Props props) {
        String name = moduleType.getType();
        log.trace("Starting module worker for module '{}'", name);

        getContext().watch(getContext().actorOf(backoffSupervisorProps(name, props, moduleType), name));
    }

    void deinitializeModuleWorker(String name) {
        ActorRef worker = getContext().getChild(name);

        if (worker != null) {
            log.trace("Stopping module worker for module '{}'", name);

            getContext().unwatch(worker);
            getContext().stop(worker);
        } else {
            log.warn("Module worker for module '{}' not found", name);
        }
    }

    boolean isModuleInitialized(ModuleType module) {
        return getContext().getChild(module.getType()) != null;
    }

    private static boolean containsModule(String moduleId,
            Collection<ModuleType> modules) {
        return modules.stream()
                .filter(m -> m.getType().equals(moduleId))
                .findFirst()
                .isPresent();
    }

    private Props backoffSupervisorProps(String childName, Props childProps, ModuleType module) {
        ModuleBackoffOptions moduleBackoffOptions = getModuleBackoffOptions(module);
        return BackoffSupervisor.props(
                Backoff.onFailure(
                        childProps,
                        childName,
                        Duration.create(moduleBackoffOptions.minSeconds, TimeUnit.SECONDS),
                        Duration.create(moduleBackoffOptions.maxSeconds, TimeUnit.SECONDS),
                        0.0)
                        .withSupervisorStrategy(backoffStrategy(moduleBackoffOptions.maxNrOfRetries)));
    }

    private OneForOneStrategy backoffStrategy(int maxNrOfRetries) {
        return new OneForOneStrategy(maxNrOfRetries, Duration.Inf(), t -> {
            // Throwable can be an ActorInitializationException caused by a PKCS11Exception.
            if (t instanceof PKCS11Exception || t.getCause() instanceof PKCS11Exception) {
                return SupervisorStrategy.restart();
            } else {
                return SupervisorStrategy.resume();
            }
        });
    }

    protected ModuleBackoffOptions getModuleBackoffOptions(ModuleType module) {
        ModuleBackoffOptions moduleBackoffOptions = new ModuleBackoffOptions();
        moduleBackoffOptions.setMaxNrOfRetries(-1);
        moduleBackoffOptions.setMinSeconds(BACKOFF_MIN_SECONDS);
        moduleBackoffOptions.setMaxSeconds(BACKOFF_MAX_SECONDS);
        return moduleBackoffOptions;
    }

    @Data
    protected static class ModuleBackoffOptions {

        private int maxNrOfRetries;
        private long minSeconds;
        private long maxSeconds;
    }
}
