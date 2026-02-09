/*
 * The MIT License
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.cs.admin.globalconf.generator;

import ee.ria.xroad.common.util.TimeUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.niis.xroad.cs.admin.globalconf.generator.FileUtils.delete;

@Slf4j
@RequiredArgsConstructor
public class GlobalConfApplier {
    private static final int OLD_CONF_PRESERVING_SECONDS = 600;

    private final int confVersion;
    private final ConfigurationDistributor configurationDistributor;
    private final SystemParameterService systemParameterService;
    private final GlobalConfGenerationProperties properties;
    private final Path configurationPath;
    private final Set<ConfigurationPart> configurationParts = new HashSet<>();

    public void apply() {
        configurationDistributor.moveDirectoryContentFile(properties.getTmpInternalDirectory(), properties.getInternalDirectory());
        configurationDistributor.moveDirectoryContentFile(properties.getTmpExternalDirectory(), properties.getExternalDirectory());

        cleanUpOldConfigurations();

        writeLocalCopy(confVersion, configurationParts);
    }

    public void rollback() {
        configurationDistributor.deleteDirectoryContentFile(properties.getTmpInternalDirectory());
        configurationDistributor.deleteDirectoryContentFile(properties.getTmpExternalDirectory());

        try {
            delete(configurationDistributor.getConfigLocationPath());
        } catch (IOException e) {
            throw XrdRuntimeException.systemException(e);
        }
    }

    public void addConfigurationParts(Set<ConfigurationPart> parts) {
        this.configurationParts.addAll(parts);
    }

    public void cleanUpOldConfigurations() {
        try (var filesStream = Files.list(configurationDistributor.getVersionLocationPath())) {
            filesStream
                    .filter(GlobalConfApplier::isExpiredConfDir)
                    .forEach(GlobalConfApplier::deleteExpiredConfigDir);
        } catch (Exception e) {
            throw XrdRuntimeException.systemException(e);
        }
    }

    private static boolean isExpiredConfDir(Path dirPath) {
        try {
            return Files.isDirectory(dirPath)
                    && dirPath.getFileName().toString().matches("\\A\\d+\\z")
                    && Files.getLastModifiedTime(dirPath).toInstant().isBefore(
                    TimeUtils.now().minusSeconds(OLD_CONF_PRESERVING_SECONDS));
        } catch (IOException ioException) {
            throw XrdRuntimeException.systemException(ioException);
        }
    }

    private static void deleteExpiredConfigDir(Path dirPath) {
        try {
            log.trace("Deleting expired global configuration directory {}", dirPath);
            org.apache.commons.io.FileUtils.deleteDirectory(dirPath.toFile());
        } catch (IOException ioException) {
            throw XrdRuntimeException.systemException(ioException);
        }
    }

    private void writeLocalCopy(int configurationVersion, Set<ConfigurationPart> allConfigurationParts) {
        new LocalCopyWriter(configurationVersion,
                systemParameterService.getInstanceIdentifier(),
                configurationPath,
                TimeUtils.now().plusSeconds(systemParameterService.getConfExpireIntervalSeconds())
        )
                .write(allConfigurationParts);
    }
}
