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

package ee.ria.xroad.common.util;

import ee.ria.xroad.common.util.process.ExternalProcessRunner;
import ee.ria.xroad.common.util.process.ProcessFailedException;
import ee.ria.xroad.common.util.process.ProcessNotExecutableException;

import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Determines backup format compatibility from the backup's ".metadata" sidecar file. Backups created by this
 * system already have their metadata written by the backup-creation script itself (see write_backup_metadata()
 * in _backup_xroad.sh); uploaded backups are checked via the metadata-creation script.
 */
@Slf4j
public class BackupMetadataHandler {

    private static final String METADATA_SUFFIX = ".metadata";

    private static final ObjectMapper MAPPER = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();

    private final ExternalProcessRunner externalProcessRunner;
    private final String createBackupMetadataScriptPath;
    private final Path backupDirectory;
    private final String expectedServerType;
    private final String currentFormatVersion;

    public BackupMetadataHandler(ExternalProcessRunner externalProcessRunner,
                                  String backupFormatVersionFilePath,
                                  String createBackupMetadataScriptPath,
                                  Path backupDirectory,
                                  String expectedServerType) {
        this.externalProcessRunner = externalProcessRunner;
        this.createBackupMetadataScriptPath = createBackupMetadataScriptPath;
        this.backupDirectory = backupDirectory.normalize();
        this.expectedServerType = expectedServerType;
        this.currentFormatVersion = readFormatVersion(backupFormatVersionFilePath);
    }

    public boolean isBackupCompatible(Path backupPath) {
        return readMetadata(backupPath).map(this::isCompatible).orElse(false);
    }

    public void deleteMetadata(Path backupPath) {
        if (isOutsideBackupDirectory(backupPath)) {
            log.warn("Refusing to delete metadata for path outside the backup directory: {}", backupPath);
            return;
        }
        try {
            Files.deleteIfExists(toMetadataPath(backupPath));
        } catch (IOException e) {
            log.warn("Failed to delete metadata for {}: {}", backupPath.getFileName(), e.getMessage());
        }
    }

    /**
     * Runs the metadata creation script, which clears any stale ".metadata" file for this backup up front and
     * writes a fresh one only if it can parse the backup's version. This only creates/refreshes the metadata
     * file; call {@link #isBackupCompatible(Path)} separately to read the compatibility result.
     */
    public void createMetadata(Path backupPath) {
        if (isOutsideBackupDirectory(backupPath)) {
            log.warn("Refusing to create metadata for path outside the backup directory: {}", backupPath);
            return;
        }
        try {
            ExternalProcessRunner.ProcessResult processResult = externalProcessRunner.execute(
                    createBackupMetadataScriptPath,
                    backupPath.toString());

            log.info(" --- Backup label script console output - START --- ");
            log.info(ExternalProcessRunner.processOutputToString(processResult.getProcessOutput()));
            log.info(" --- Backup label script console output - END --- ");

            if (processResult.getExitCode() != 0) {
                log.warn("Backup label script failed for {} with exit code {}",
                        backupPath.getFileName(), processResult.getExitCode());
            }
        } catch (ProcessNotExecutableException | ProcessFailedException e) {
            log.warn("Reading backup label failed for {}: {}", backupPath.getFileName(), e.getMessage());
            deleteMetadata(backupPath);
        } catch (InterruptedException e) {
            log.warn("Reading backup label interrupted for {}", backupPath.getFileName());
            deleteMetadata(backupPath);
        }
    }

    private boolean isCompatible(BackupMetadata metadata) {
        return currentFormatVersion != null
                && currentFormatVersion.equals(metadata.version())
                && expectedServerType.equals(metadata.serverType());
    }

    private boolean isOutsideBackupDirectory(Path backupPath) {
        return !backupPath.normalize().startsWith(backupDirectory);
    }

    private static String readFormatVersion(String backupFormatVersionFilePath) {
        try {
            return Files.readString(Path.of(backupFormatVersionFilePath)).strip();
        } catch (IOException e) {
            log.warn("Failed to read backup format version file {}: {}", backupFormatVersionFilePath, e.getMessage());
            return null;
        }
    }

    private static Optional<BackupMetadata> readMetadata(Path backupPath) {
        Path metaPath = toMetadataPath(backupPath);
        if (!Files.exists(metaPath)) {
            return Optional.empty();
        }
        try {
            String json = Files.readString(metaPath);
            return Optional.ofNullable(MAPPER.readValue(json, BackupMetadata.class));
        } catch (Exception e) {
            log.warn("Failed to read metadata for {}: {}", backupPath.getFileName(), e.getMessage());
            return Optional.empty();
        }
    }

    private static Path toMetadataPath(Path backupPath) {
        return backupPath.resolveSibling(backupPath.getFileName() + METADATA_SUFFIX);
    }

    private record BackupMetadata(String version, String serverType) {
    }
}
