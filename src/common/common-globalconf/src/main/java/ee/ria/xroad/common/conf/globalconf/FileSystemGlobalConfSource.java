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
package ee.ria.xroad.common.conf.globalconf;

import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Optional;

import static ee.ria.xroad.common.ErrorCodes.X_MALFORMED_GLOBALCONF;
import static ee.ria.xroad.common.ErrorCodes.translateWithPrefix;
import static ee.ria.xroad.common.SystemProperties.getConfigurationPath;

/**
 * Wrapper source for file system based global configuration.
 */
@Slf4j
public class FileSystemGlobalConfSource implements GlobalConfSource {

    private VersionedConfigurationDirectory configurationDirectory;

    public FileSystemGlobalConfSource() {
        this(getConfigurationPath());
    }

    public FileSystemGlobalConfSource(String globalConfigurationDir) {
        try {
            configurationDirectory = new VersionedConfigurationDirectory(globalConfigurationDir);
        } catch (Exception e) {
            log.warn("Failed to initialize FileSystemGlobalConfSource", e);
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
    public synchronized void reload() {
        VersionedConfigurationDirectory original = configurationDirectory;
        try {
            if (original != null) {
                configurationDirectory = new VersionedConfigurationDirectory(getConfigurationPath(), original);
            } else {
                configurationDirectory = new VersionedConfigurationDirectory(getConfigurationPath());
            }
        } catch (Exception e) {
            throw translateWithPrefix(X_MALFORMED_GLOBALCONF, e);
        }
    }

    private VersionedConfigurationDirectory getConfigurationDirectory() {
        if (configurationDirectory == null) {
            throw new IllegalStateException("GlobalConf is not initialized");
        }

        return configurationDirectory;
    }
}
