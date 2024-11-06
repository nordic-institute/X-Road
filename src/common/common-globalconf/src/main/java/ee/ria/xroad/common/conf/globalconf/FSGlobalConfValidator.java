/*
 * The MIT License
 *
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

import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

@Slf4j
@ToString
public class FSGlobalConfValidator {
    public GlobalConfInitState getReadinessState(String globalConfigurationDir) {
        var globalConfPathOpt = resolveGlobalConfDir(globalConfigurationDir);
        if (globalConfPathOpt.isEmpty()) {
            log.warn("Invalid path: {}", globalConfigurationDir);
            return GlobalConfInitState.FAILURE_CONFIGURATION_ERROR;
        }

        try {
            var globalConfPath = globalConfPathOpt.get();
            if (!isDir(globalConfPath)) {
                log.warn("GlobalConf at [{}] is not a directory. Initialization will not continue.", globalConfPath);
                return GlobalConfInitState.FAILURE_CONFIGURATION_ERROR;
            }
            if (isDirEmpty(globalConfPathOpt.get())) {
                log.warn("GlobalConf at [{}] is empty. Either GlobalConf is not being downloaded, "
                                + "the download is in progress, or the data is corrupted. "
                                + "Initialization will not continue.",
                        globalConfPath);
                return GlobalConfInitState.FAILURE_MALFORMED;
            }
            if (!isInstanceIdentifierPresent(globalConfPathOpt.get())) {
                log.warn("Instance identifier at [{}] is missing. Initialization will not continue.", globalConfPath);
                return GlobalConfInitState.FAILURE_MISSING_INSTANCE_IDENTIFIER;
            }

            return GlobalConfInitState.READY_TO_INIT;
        } catch (Exception e) {
            log.error("Failed to resolve GlobalConf readiness state", e);
            return GlobalConfInitState.FAILURE_UNEXPECTED;
        }
    }

    private Optional<Path> resolveGlobalConfDir(String globalConfigurationDir) {
        try {
            return Optional.of(Paths.get(globalConfigurationDir));
        } catch (InvalidPathException e) {
            return Optional.empty();
        }
    }

    private boolean isDir(Path globalConfPath) {
        return globalConfPath.toFile().isDirectory();
    }

    private boolean isDirEmpty(Path globalConfPath) throws Exception {
        try (var fileStream = Files.list(globalConfPath)) {
            return fileStream.filter(file -> file.toFile().isDirectory()).findAny().isEmpty();
        }
    }

    private static boolean isInstanceIdentifierPresent(Path globalConfPath) {
        return Files.exists(globalConfPath.resolve(ConfigurationDirectory.INSTANCE_IDENTIFIER_FILE));
    }
}
