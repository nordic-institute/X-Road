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
package org.niis.xroad.confproxy.common.utils;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.confproxy.common.config.ConfigurationProxyProperties;
import org.niis.xroad.confproxy.common.domain.ConfProxyInstance;
import org.niis.xroad.confproxy.common.exceptions.ConfProxyErrorCode;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@UtilityClass
public class ConfProxyUtils {
    private static final int MAX_CONFIGURATION_LIFETIME_SECONDS = 600;

    /**
     * Deletes outdated previously generated global configurations from configuration target path
     * e.g. /var/lib/xroad/public, as defined by the 'validity interval' configuration proxy property.
     * @param proxyInstance the configuration proxy instance configuration
     */
    public static void purgeOutdatedGenerations(final ConfProxyInstance proxyInstance) {
        Path instanceDir = Paths.get(proxyInstance.getConfigurationTargetPath());
        log.debug("Create directories {}", instanceDir);
        try {
            org.niis.xroad.globalconf.util.FileUtils.createDirectories(instanceDir); //avoid errors if it's not present
            for (String genTime : subDirectoryNames(instanceDir)) {
                Date current = new Date();
                Date old;
                try {
                    old = new Date(Long.parseLong(genTime));
                } catch (NumberFormatException e) {
                    log.error("Unable to parse directory name {}", genTime);
                    continue;
                }
                long diffSeconds = TimeUnit.MILLISECONDS.toSeconds((current.getTime() - old.getTime()));
                long timeToKeep = Math.min(MAX_CONFIGURATION_LIFETIME_SECONDS, proxyInstance.getValidityIntervalSeconds());
                if (diffSeconds > timeToKeep) {
                    Path oldPath =
                            Paths.get(proxyInstance.getConfigurationTargetPath(), genTime);
                    FileUtils.deleteDirectory(oldPath.toFile());
                    log.debug("Purge directory {}", oldPath);
                } else {
                    Path valid = instanceDir.resolve(genTime);
                    log.debug("A valid generated configuration exists in '{}'",
                            valid);
                }
            }
        } catch (IOException e) {
            throw XrdRuntimeException.systemException(ErrorCode.IO_ERROR)
                    .details("Failed to purge outdated generations under: " + instanceDir)
                    .cause(e)
                    .build();
        }
    }

    /**
     * Gets the list of subdirectory names in the given directory path.
     * @param dir path to the directory
     * @return list of subdirectory names
     */
    public static List<String> subDirectoryNames(final Path dir) {
        List<String> subdirs = new ArrayList<>();
        try (var stream = Files.newDirectoryStream(dir, Files::isDirectory)) {
            stream.forEach(subDir -> subdirs.add(subDir.getFileName().toString()));
            return subdirs;
        } catch (IOException e) {
            throw XrdRuntimeException.systemException(ConfProxyErrorCode.MALFORMED_URI_ERROR)
                    .details("Failed to read subdirectories under: " + dir)
                    .cause(e)
                    .build();
        }
    }

    /**
     * Gets the public URL where configurations should be available,
     * 'configuration-proxy.address' needs to be defined in 'local.yaml'.
     * @return list of  URLs where global configurations are made available
     */
    public static List<String> getConfigurationProxyURLs(final String address, final String instance) {
        if (ConfigurationProxyProperties.DEFAULT_CONNECTOR_HOST.equals(address)) {
            return List.of();
        }

        try {
            return List.of(
                    new URI("http", address, "/" + instance, null).toString(),
                    new URI("https", address, "/" + instance, null).toString());
        } catch (URISyntaxException e) {
            throw XrdRuntimeException.systemException(ConfProxyErrorCode.MALFORMED_URI_ERROR)
                    .details("Failed to build URLs for instance: '%s' address: '%s'".formatted(instance, address))
                    .cause(e)
                    .build();
        }
    }
}
