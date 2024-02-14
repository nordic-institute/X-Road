/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
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
package ee.ria.xroad.confproxy.util;

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.globalconf.VersionedConfigurationDirectory;
import ee.ria.xroad.confproxy.ConfProxyProperties;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Provides configuration proxy utility functions.
 */
@Slf4j
public final class ConfProxyHelper {
    private static final int SUCCESS = 0;
    private static final int ERROR_CODE_INTERNAL = 125;
    private static final int ERROR_CODE_INVALID_SIGNATURE_VALUE = 124;
    private static final int ERROR_CODE_EXPIRED_CONF = 123;
    private static final int ERROR_CODE_CANNOT_DOWNLOAD_CONF = 122;
    private static final int MAX_CONFIGURATION_LIFETIME_SECONDS = 600;
    private static final String CONFIGURATION_CLIENT_ERROR = "configuration-client error (exit code %1$d)";

    /**
     * Unavailable utility class constructor.
     */
    private ConfProxyHelper() {
    }

    /**
     * Invoke the configuration client script to download the global
     * configuration from the source defined in the provided source anchor.
     * @param path where the downloaded files should be placed
     * @param sourceAnchor path to the source anchor xml file
     * @return downloaded configuration directory
     * @throws Exception if an configuration client error occurs
     */
    public static VersionedConfigurationDirectory downloadConfiguration(
            final String path, final String sourceAnchor, final int version) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                ConfProxyProperties.getDownloadScriptPath(),
                sourceAnchor, path, String.format("%d", version));
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        log.info("Running '{} {} {} {}' ...", ConfProxyProperties.getDownloadScriptPath(), sourceAnchor, path,
                version);
        runConfClient(pb);
        return new VersionedConfigurationDirectory(path);
    }

    /**
     * Invoke the configuration client script to check whether the downloaded
     * global configuration is valid according to the provided source anchor.
     * @param sourceAnchor path to the source anchor xml file
     * @throws Exception if an configuration client error occurs
     */
    public static void validateConfiguration(final String sourceAnchor)
            throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
                ConfProxyProperties.getDownloadScriptPath(),
                sourceAnchor);
        pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        log.info("Running '{} {}' ...",
                ConfProxyProperties.getDownloadScriptPath(),
                sourceAnchor);
        runConfClient(pb);
    }

    /**
     * Helper method for running the configuration client script.
     * @param pb the configuration client script process builder
     * @throws Exception if errors occur when running the configuration client
     */
    private static void runConfClient(final ProcessBuilder pb)
            throws Exception {
        int exitCode = -1;
        try {
            Process process = pb.start();
            exitCode = process.waitFor();

        } catch (IOException e) {
            log.error("IOException", e);
            exitCode = 2;
            throw e;
        } catch (Exception e) {
            log.error("Undetermined ConfigurationClient exitCode", e);
            //undetermined ConfigurationClient exitCode, fail in 'finally'
            throw e;
        }
        switch (exitCode) {
            case SUCCESS:
                break;
            case ERROR_CODE_CANNOT_DOWNLOAD_CONF:
                throw new Exception(String.format(CONFIGURATION_CLIENT_ERROR, exitCode)
                        + ", download failed");
            case ERROR_CODE_EXPIRED_CONF:
                throw new Exception(String.format(CONFIGURATION_CLIENT_ERROR, exitCode)
                        + ", configuration is outdated");
            case ERROR_CODE_INVALID_SIGNATURE_VALUE:
                throw new Exception(String.format(CONFIGURATION_CLIENT_ERROR, exitCode)
                        + ", configuration is incorrect");
            case ERROR_CODE_INTERNAL:
                throw new Exception(String.format(CONFIGURATION_CLIENT_ERROR, exitCode));
            default:
                throw new Exception("Failed to download GlobalConf ["
                        + String.format(CONFIGURATION_CLIENT_ERROR, exitCode)
                        + "], make sure configuration-client is"
                        + "installed correctly");
        }
    }

    /**
     * Gets all existing subdirectory names from the configuration proxy
     * configuration directory, which correspond to the configuration proxy
     * instance ids.
     * @return list of configuration proxy instance ids
     * @throws IOException if the configuration proxy configuration path is
     * erroneous
     */
    public static List<String> availableInstances() throws IOException {
        Path confPath =
                Paths.get(SystemProperties.getConfigurationProxyConfPath());
        return subDirectoryNames(confPath);
    }

    /**
     * Deletes outdated previously generated global configurations from configuration target path
     * e.g. /var/lib/xroad/public, as defined by the 'validity interval' configuration proxy property.
     * @param conf the configuration proxy instance configuration
     * @throws IOException
     * in case an old global configuration could not be deleted
     */
    public static void purgeOutdatedGenerations(final ConfProxyProperties conf)
            throws IOException {
        Path instanceDir = Paths.get(conf.getConfigurationTargetPath());
        log.debug("Create directories {}", instanceDir);
        Files.createDirectories(instanceDir); //avoid errors if it's not present
        for (String genTime : subDirectoryNames(instanceDir)) {
            Date current = new Date();
            Date old;
            try {
                old = new Date(Long.parseLong(genTime));
            } catch (NumberFormatException e) {
                log.error("Unable to parse directory name {}", genTime);
                continue;
            }
            long diffSeconds = TimeUnit.MILLISECONDS
                    .toSeconds((current.getTime() - old.getTime()));
            long timeToKeep = Math.min(MAX_CONFIGURATION_LIFETIME_SECONDS,
                    conf.getValidityIntervalSeconds());
            if (diffSeconds > timeToKeep) {
                Path oldPath =
                        Paths.get(conf.getConfigurationTargetPath(), genTime);
                FileUtils.deleteDirectory(oldPath.toFile());
                log.debug("Purge directory {}", oldPath);
            } else {
                Path valid = instanceDir.resolve(genTime);
                log.debug("A valid generated configuration exists in '{}'",
                        valid);
            }
        }
    }

    /**
     * Gets the list of subdirectory names in the given directory path.
     * @param dir path to the directory
     * @return list of subdirectory names
     * @throws IOException if opening the directory fails
     */
    private static List<String> subDirectoryNames(final Path dir)
            throws IOException {
        List<String> subdirs = new ArrayList<>();
        try (DirectoryStream<Path> stream =
                     Files.newDirectoryStream(dir, Files::isDirectory)) {
            for (Path subDir : stream) {
                String conf = subDir.getFileName().toString();
                subdirs.add(conf);
            }
            return subdirs;
        }
    }
}
