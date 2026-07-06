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

package org.niis.xroad.auxiliaryservice.core.backup;

import ee.ria.xroad.common.util.process.ExternalProcessRunner;
import ee.ria.xroad.common.util.process.ProcessFailedException;
import ee.ria.xroad.common.util.process.ProcessNotExecutableException;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.auxiliaryservice.core.config.BackupProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

@Startup
@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class BackupMetadataService {

    static final String METADATA_SUFFIX = ".metadata";

    private final ExternalProcessRunner externalProcessRunner;
    private final BackupProperties backupProperties;

    private String currentFormatVersion;

    @PostConstruct
    void init() {
        String formatVersionFilePath = backupProperties.backupFormatVersionFilePath();
        try {
            currentFormatVersion = Files.readString(Path.of(formatVersionFilePath)).strip();
        } catch (IOException e) {
            log.warn("Failed to read backup format version file {}: {}",
                    formatVersionFilePath, e.getMessage());
        }
    }

    public boolean isBackupCompatible(Path backupPath) {
        return readMetadataVersion(backupPath).map(this::isCompatible).orElse(false);
    }

    public void deleteMetadata(Path backupPath) {
        try {
            Files.deleteIfExists(toMetadataPath(backupPath));
        } catch (IOException e) {
            log.warn("Failed to delete metadata for {}: {}", backupPath.getFileName(), e.getMessage());
        }
    }

    /**
     * Runs the metadata creation script, which parses the backup's version and writes the backup's ".metadata" file
     */
    public boolean determineBackupCompatibility(Path backupPath) {
        try {
            ExternalProcessRunner.ProcessResult processResult = externalProcessRunner.execute(
                    backupProperties.createBackupMetadataPath(),
                    backupPath.toString());

            log.info(" --- Backup label script console output - START --- ");
            log.info(ExternalProcessRunner.processOutputToString(processResult.getProcessOutput()));
            log.info(" --- Backup label script console output - END --- ");

            if (processResult.getExitCode() != 0) {
                log.warn("Backup label script failed for {} with exit code {}",
                        backupPath.getFileName(), processResult.getExitCode());
                return false;
            }

            return isBackupCompatible(backupPath);
        } catch (ProcessNotExecutableException | ProcessFailedException e) {
            log.warn("Reading backup label failed for {}: {}", backupPath.getFileName(), e.getMessage());
            return false;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Reading backup label interrupted for {}", backupPath.getFileName());
            return false;
        }
    }

    private Optional<String> readMetadataVersion(Path backupPath) {
        Path metaPath = toMetadataPath(backupPath);
        if (!Files.exists(metaPath)) {
            return Optional.empty();
        }
        try {
            return Optional.of(Files.readString(metaPath).strip());
        } catch (IOException e) {
            log.warn("Failed to read metadata for {}: {}", backupPath.getFileName(), e.getMessage());
            return Optional.empty();
        }
    }

    private boolean isCompatible(String version) {
        return currentFormatVersion != null && currentFormatVersion.equals(version);
    }

    private static Path toMetadataPath(Path backupPath) {
        return backupPath.resolveSibling(backupPath.getFileName() + METADATA_SUFFIX);
    }
}
