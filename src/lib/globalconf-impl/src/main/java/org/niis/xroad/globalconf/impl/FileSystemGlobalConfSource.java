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
package org.niis.xroad.globalconf.impl;

import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.globalconf.ManagedLifecycleGlobalConfSource;
import org.niis.xroad.globalconf.model.FileSource;
import org.niis.xroad.globalconf.model.GlobalConfInitException;
import org.niis.xroad.globalconf.model.GlobalConfInitState;
import org.niis.xroad.globalconf.model.PrivateParameters;
import org.niis.xroad.globalconf.model.SharedParameters;
import org.niis.xroad.globalconf.model.SharedParametersCache;
import org.niis.xroad.globalconf.model.VersionedConfigurationDirectory;
import org.niis.xroad.globalconf.util.FSGlobalConfValidator;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

/**
 * Wrapper source for file system based global configuration.
 */
@Slf4j
public class FileSystemGlobalConfSource implements ManagedLifecycleGlobalConfSource {
    private final FSGlobalConfValidator fsGlobalConfValidator;
    private final String globalConfigurationDir;
    private volatile VersionedConfigurationDirectory configurationDirectory;
    private GlobalConfInitState lastState = GlobalConfInitState.UNKNOWN;

    public FileSystemGlobalConfSource(String confDir) {
        this.globalConfigurationDir = confDir;
        this.fsGlobalConfValidator = new FSGlobalConfValidator();
    }

    @Override
    public void init() {
        try {
            load();
        } catch (Exception e) {
            log.warn("Failed to initialize FileSystemGlobalConfSource");
        }
    }

    @Override
    public Integer getVersion() {
        return getConfigurationDirectory().getVersion();
    }

    @Override
    public String getInstanceIdentifier() {
        return getConfigurationDirectory().getInstanceIdentifier();
    }

    @Override
    public Optional<SharedParameters> findShared(String xRoadInstance) {
        return getConfigurationDirectory().findShared(xRoadInstance);
    }

    @Override
    public Optional<PrivateParameters> findPrivate(String instanceIdentifier) {
        return getConfigurationDirectory().findPrivate(instanceIdentifier);
    }

    @Override
    public List<SharedParameters> getShared() {
        return getConfigurationDirectory().getShared();
    }

    @Override
    public Optional<SharedParametersCache> findSharedParametersCache(String instanceIdentifier) {
        return getConfigurationDirectory().findSharedParametersCache(instanceIdentifier);
    }

    @Override
    public List<SharedParametersCache> getSharedParametersCaches() {
        return getConfigurationDirectory().getSharedParametersCaches();
    }

    @Override
    public boolean isExpired() {
        return getConfigurationDirectory().isExpired();
    }

    @Override
    public void reload() {
        synchronized (FileSystemGlobalConfSource.class) {
            load();
        }
    }

    /**
     * Returns loaded configuration source, with a fallback to loading it if it's not loaded yet.
     * Usually it is not loaded only during initialization phase.
     */
    private VersionedConfigurationDirectory getConfigurationDirectory() {
        if (configurationDirectory == null) {
            synchronized (FileSystemGlobalConfSource.class) {
                if (configurationDirectory == null) {
                    log.warn("Configuration source was not loaded. Trying to reload..");
                    if (!load()) {
                        throw new GlobalConfInitException(lastState);
                    }
                }
            }
        }

        return configurationDirectory;
    }

    /**
     * Load configuration source from file system.
     *
     * @return true if configuration source was loaded successfully
     */
    private boolean load() {
        var state = fsGlobalConfValidator.getReadinessState(globalConfigurationDir);
        if (state == GlobalConfInitState.READY_TO_INIT) {
            VersionedConfigurationDirectory original = configurationDirectory;
            try {
                if (original != null) {
                    configurationDirectory = new VersionedConfigurationDirectory(globalConfigurationDir, original);
                    log.debug("Successfully reloaded globalconf source");
                } else {
                    configurationDirectory = new VersionedConfigurationDirectory(globalConfigurationDir);
                    log.debug("Successfully loaded globalconf source");
                }

                lastState = GlobalConfInitState.INITIALIZED;
                return true;
            } catch (Exception e) {
                log.error("Failed to load configuration source", e);
                lastState = GlobalConfInitState.FAILURE_MALFORMED;
            }
        } else {
            lastState = state;
        }
        return false;
    }


    @Override
    public GlobalConfInitState getReadinessState() {
        return lastState;
    }

    @Override
    public FileSource getFile(String fileName) {
        var path = Paths.get(globalConfigurationDir, getInstanceIdentifier(), fileName);
        return new FileSystemFileSource(path);
    }

    @ToString
    @RequiredArgsConstructor
    public static class FileSystemFileSource implements FileSource<Path> {
        private final Path path;

        @Override
        public Optional<Path> getFile() {
            if (path != null && Files.exists(path)) {
                return Optional.of(path);
            }
            return Optional.empty();
        }

    }
}
