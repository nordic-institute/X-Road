/*
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
package ee.ria.xroad.signer.tokenmanager.module;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.filewatcher.FileWatcherRunner;
import ee.ria.xroad.signer.certmanager.OcspResponseManager;
import ee.ria.xroad.signer.model.Cert;
import ee.ria.xroad.signer.protocol.message.GetOcspResponses;
import ee.ria.xroad.signer.tokenmanager.TokenManager;
import ee.ria.xroad.signer.tokenmanager.token.TokenWorker;
import ee.ria.xroad.signer.tokenmanager.token.TokenWorkerProvider;
import ee.ria.xroad.signer.tokenmanager.token.WorkerWithLifecycle;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;

import java.nio.file.Paths;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static ee.ria.xroad.common.SystemProperties.NodeType.SLAVE;
import static java.util.Objects.requireNonNull;

/**
 * Module manager base class.
 */
@Slf4j
public abstract class AbstractModuleManager implements WorkerWithLifecycle, TokenWorkerProvider {
    private final SystemProperties.NodeType serverNodeType = SystemProperties.getServerNodeType();

    @Autowired
    private OcspResponseManager ocspResponseManager;

    @SuppressWarnings("java:S3077")
    private volatile Map<String, AbstractModuleWorker> moduleWorkers = Collections.emptyMap();

    private FileWatcherRunner keyConfFileWatcherRunner;

    @Override
    @PostConstruct
    public void start() {
        log.info("Initializing module worker of instance {}", getClass().getSimpleName());
        try {
            TokenManager.init();

            if (SLAVE.equals(SystemProperties.getServerNodeType())) {
                // when the key conf file is changed from outside this system (i.e. a new copy from master),
                // send an update event to the module manager so it knows to load the new config
                this.keyConfFileWatcherRunner = FileWatcherRunner.create()
                        .watchForChangesIn(Paths.get(SystemProperties.getKeyConfFile()))
                        .listenToCreate().listenToModify()
                        .andOnChangeNotify(this::refresh)
                        .buildAndStartWatcher();
            }
            refresh();
        } catch (Exception e) {
            log.error("Failed to initialize token worker!", e);
        }
    }

    @PreDestroy
    @Override
    public void stop() {
        log.info("Destroying module worker");

        if (!SLAVE.equals(SystemProperties.getServerNodeType())) {
            try {
                TokenManager.saveToConf();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        if (this.keyConfFileWatcherRunner != null) {
            this.keyConfFileWatcherRunner.stop();
        }
    }

    @Override
    public void refresh() {
        log.trace("refresh()");
        loadModules();

        if (SLAVE.equals(serverNodeType)) {
            mergeConfiguration();
        }

        moduleWorkers.forEach((key, worker) -> {
            try {
                worker.refresh();
            } catch (Exception e) {
                log.error("Error refreshing module '{}'.", key);
            }
        });

        if (!SLAVE.equals(serverNodeType)) {
            persistConfiguration();
        }
    }

    @Override
    public Optional<TokenWorker> getTokenWorker(String tokenId) {
        for (Map.Entry<String, AbstractModuleWorker> entry : moduleWorkers.entrySet()) {
            var tokenOpt = entry.getValue().getTokenById(tokenId);
            if (tokenOpt.isPresent()) {
                return tokenOpt;
            }
        }
        return Optional.empty();
    }

    protected abstract AbstractModuleWorker createModuleWorker(ModuleType module) throws Exception;

    /**
     * Returns HSM module operational status.
     * Note: Only hardware token module manger returns a status. Default implementation returns null.
     *
     * @return status
     */
    public abstract Optional<Boolean> isHSMModuleOperational();

    private void loadModules() {
        log.trace("loadModules()");

        if (!ModuleConf.hasChanged()) {
            // do not reload, if conf has not changed
            return;
        }

        ModuleConf.reload();

        final Collection<ModuleType> modules = ModuleConf.getModules();
        final Map<String, AbstractModuleWorker> refreshedWorkerModules = loadModules(modules);
        final var oldModuleWorkers = moduleWorkers;

        log.trace("Registered {} modules in {}", refreshedWorkerModules.size(), getClass().getSimpleName());
        moduleWorkers = Collections.unmodifiableMap(refreshedWorkerModules);
        stopLostModules(oldModuleWorkers, modules);
    }

    private void stopLostModules(Map<String, AbstractModuleWorker> oldModuleWorkers, Collection<ModuleType> modules) {
        final Set<String> moduleTypes = modules.stream()
                .map(ModuleType::getType)
                .collect(Collectors.toSet());

        for (Map.Entry<String, AbstractModuleWorker> entry : oldModuleWorkers.entrySet()) {
            if (!moduleTypes.contains(entry.getKey())) {
                try {
                    log.trace("Stopping module worker for module '{}'", entry.getKey());
                    entry.getValue().stop();
                } catch (Exception e) {
                    log.error("Failed to stop module {}", entry.getKey(), e);
                }

            }
        }
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
                try {
                    ocspResponseManager.handleGetOcspResponses(mapCertListToGetOcspResponses(addedCerts));
                } catch (Exception e) {
                    log.error("Failed to refresh OCSP", e);
                    //TODO what should be done if failed?
                }
            }
        });
    }

    private static GetOcspResponses mapCertListToGetOcspResponses(List<Cert> certs) {
        requireNonNull(certs);

        return new GetOcspResponses(certs.stream().map(cert -> {
            try {
                return CryptoUtils.calculateCertSha1HexHash(cert.getCertificate());
            } catch (Exception e) {
                log.error("Failed to calculate hash for new certificate {}", cert, e);

                return null;
            }
        }).filter(Objects::nonNull).toArray(String[]::new));
    }

    private Map<String, AbstractModuleWorker> loadModules(Collection<ModuleType> modules) {
        final Map<String, AbstractModuleWorker> newModules = new HashMap<>();

        modules.forEach(moduleType -> {
            try {
                AbstractModuleWorker moduleWorker = moduleWorkers.get(moduleType.getType());
                if (moduleWorker == null) {
                    moduleWorker = createModuleWorker(moduleType);
                    moduleWorker.start();
                }

                newModules.put(moduleWorker.getModuleType().getType(), moduleWorker);
            } catch (Exception e) {
                log.error("Error loading module '{}'.", moduleType, e);
            }
        });
        return newModules;
    }


    boolean isModuleInitialized(ModuleType module) {
        return moduleWorkers.containsKey(module.getType());
    }

}
