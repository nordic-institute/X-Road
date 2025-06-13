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
package org.niis.xroad.signer.core.tokenmanager.module;

import ee.ria.xroad.common.CodedException;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.signer.core.config.SignerHwTokenAddonProperties;
import org.niis.xroad.signer.core.tokenmanager.token.TokenWorker;
import org.niis.xroad.signer.core.tokenmanager.token.TokenWorkerProvider;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static org.niis.xroad.signer.core.util.ExceptionHelper.tokenNotFound;

/**
 * Module manager base class.
 */
@Slf4j
@ApplicationScoped
@RequiredArgsConstructor
public class ModuleManager implements TokenWorkerProvider {

    private final SoftwareModuleWorkerFactory softwareModuleWorkerFactory;
    private final HardwareModuleWorkerFactory hardwareModuleWorkerFactory;
    private final SignerHwTokenAddonProperties hwTokenAddonProperties;
    private final ModuleConf moduleConf;

    @SuppressWarnings("java:S3077")
    private volatile Map<String, AbstractModuleWorker> moduleWorkers = Collections.emptyMap();

    @PostConstruct
    public void start() {
        log.info("Initializing module worker of instance {}", getClass().getSimpleName());
        try {
            refresh();
        } catch (Exception e) {
            log.error("Failed to initialize token worker!", e);
        }
    }

    public void refresh() {
        log.trace("refresh()");
        loadModules();

        moduleWorkers.forEach((key, worker) -> {
            try {
                worker.refresh();
            } catch (Exception e) {
                log.error("Error refreshing module '{}'.", key);
            }
        });
    }

    @Override
    public TokenWorker getTokenWorker(String tokenId) {
        for (Map.Entry<String, AbstractModuleWorker> entry : moduleWorkers.entrySet()) {
            var tokenOpt = entry.getValue().getTokenById(tokenId);
            if (tokenOpt.isPresent()) {
                return tokenOpt.get();
            }
        }
        throw tokenNotFound(tokenId);
    }

    /**
     * Returns HSM module operational status.
     * Note: Only hardware token module manager returns a status.
     *
     * @return status
     */
    public boolean isHSMModuleOperational() {
        if (hwTokenAddonProperties.enabled()) {
            return moduleConf.getModules().stream()
                    .noneMatch(moduleType -> moduleType instanceof HardwareModuleType
                            && !isModuleInitialized(moduleType));
        }
        return false;
    }

    private AbstractModuleWorker createModuleWorker(ModuleType module) {
        if (module instanceof HardwareModuleType hmt) {
            return createHardwareWorker(hmt);
        }
        if (module instanceof SoftwareModuleType softwareModuleType) {
            return createSoftwareModule(softwareModuleType);
        }
        throw new CodedException(X_INTERNAL_ERROR, "unrecognized module type found!");
    }

    private SoftwareModuleWorkerFactory.SoftwareModuleWorker createSoftwareModule(SoftwareModuleType softwareModule) {
        log.debug("Initializing software module");
        return softwareModuleWorkerFactory.create(softwareModule);
    }

    private HardwareModuleWorkerFactory.HardwareModuleWorker createHardwareWorker(HardwareModuleType hardwareModule) {
        try {
            return hardwareModuleWorkerFactory.create(hardwareModule);
        } catch (Exception e) {
            log.error("Error initializing hardware module '{}'", hardwareModule.getType(), e);
        }

        return null;
    }

    private void loadModules() {
        log.trace("loadModules()");

        if (!moduleConf.hasChanged()) {
            // do not reload, if conf has not changed
            return;
        }

        moduleConf.reload();

        final Collection<ModuleType> modules = moduleConf.getModules();
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
                    entry.getValue().destroy();
                } catch (Exception e) {
                    log.error("Failed to stop module {}", entry.getKey(), e);
                }

            }
        }
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
