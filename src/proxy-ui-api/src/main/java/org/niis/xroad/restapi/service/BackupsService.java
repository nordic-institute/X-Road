/**
 * The MIT License
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
package org.niis.xroad.restapi.service;

import ee.ria.xroad.common.identifier.SecurityServerId;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.converter.BackupsConverter;
import org.niis.xroad.restapi.exceptions.DeviationAwareRuntimeException;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.openapi.model.Backup;
import org.niis.xroad.restapi.repository.BackupsRepository;
import org.niis.xroad.restapi.util.FormatUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Optional;

/**
 * Backups service.
 */
@Slf4j
@Service
@PreAuthorize("isAuthenticated()")
public class BackupsService {

    private static final String BACKUP_GENERATION_FAILED = "backup_generation_failed";

    private final BackupsRepository backupsRepository;
    private final BackupsConverter backupsConverter;
    private final ServerConfService serverConfService;
    private final ExternalProcessRunner externalProcessRunner;

    @Setter
    private String generateBackupScriptPath;
    private static final String BACKUP_FILENAME_DATE_TIME_FORMAT = "yyyyMMdd-HHmmss";

    /**
     * BackupsService constructor
     * @param backupsRepository
     */
    @Autowired
    public BackupsService(BackupsRepository backupsRepository, BackupsConverter backupsConverter,
                          ServerConfService serverConfService, ExternalProcessRunner externalProcessRunner,
                          @Value("${script.generate-backup.path}") String generateBackupScriptPath) {
        this.backupsRepository = backupsRepository;
        this.backupsConverter = backupsConverter;
        this.serverConfService = serverConfService;
        this.externalProcessRunner = externalProcessRunner;
        this.generateBackupScriptPath = generateBackupScriptPath;
    }

    /**
     * Return a list of available backup files
     * @return
     */
    public List<Backup> getBackupFiles() {
        List<File> backupFiles = backupsRepository.getBackupFiles();
        List<Backup> backups = backupsConverter.convert(backupFiles);
        setCreatedAt(backups);
        return backups;
    }

    /**
     * Delete a backup file or throws an exception if the file does not exist
     * @param filename
     * @throws BackupFileNotFoundException
     */
    public void deleteBackup(String filename) throws BackupFileNotFoundException {
        if (!backupExists(filename).isPresent()) {
            throw new BackupFileNotFoundException(getFileNotFoundExceptionMessage(filename));
        }
        backupsRepository.deleteBackupFile(filename);
    }

    /**
     * Read backup file's content
     * @param filename
     * @return
     * @throws BackupFileNotFoundException
     */
    public byte[] readBackupFile(String filename) throws BackupFileNotFoundException {
        if (!backupExists(filename).isPresent()) {
            throw new BackupFileNotFoundException(getFileNotFoundExceptionMessage(filename));
        }
        return backupsRepository.readBackupFile(filename);
    }

    /**
     * Generate a new backup file
     * @throws ProcessFailedException
     * @throws ProcessNotExecutableException
     * @throws InterruptedException
     * @return
     */
    public Backup generateBackup() throws InterruptedException, BackupFileNotFoundException {
        SecurityServerId securityServerId = serverConfService.getSecurityServerId();
        String filename = generateBackupFileName();
        String fullPath =  backupsRepository.getConfigurationBackupPath() + filename;
        String[] args = new String[] {"-s", securityServerId.toShortString(), "-f", fullPath};

        try {
            log.info("Run configuration backup with command '"
                    + generateBackupScriptPath + " " + Arrays.toString(args) + "'");

            List<String> output = externalProcessRunner.execute(generateBackupScriptPath, args);

            log.info(" --- Backup script console output - START --- ");
            log.info(String.join("\n", output));
            log.info(" --- Backup script console output - END --- ");
        } catch (ProcessNotExecutableException | ProcessFailedException e) {
            log.error("Failed to generate backup", e);
            throw new DeviationAwareRuntimeException(e, new ErrorDeviation(BACKUP_GENERATION_FAILED));
        }

        Optional<Backup> backup = backupExists(filename);
        if (!backup.isPresent()) {
            throw new BackupFileNotFoundException(getFileNotFoundExceptionMessage(filename));
        }
        return backup.get();
    }

    /**
     * Check if a backup file with the given filename exists
     * @param filename
     * @return
     */
    private Optional<Backup> backupExists(String filename) {
        return getBackupFiles().stream()
                .filter(b -> b.getFilename().equals(filename))
                .findFirst();
    }

    /**
     * Set the "createdAt" property to a list of backups
     * @param backups
     */
    private void setCreatedAt(List<Backup> backups) {
        backups.stream().forEach(b -> {
            Date createdAt = backupsRepository.getCreatedAt(b.getFilename());
            b.setCreatedAt(FormatUtils.fromDateToOffsetDateTime(createdAt));
        });
    }

    /**
     * Generate name for a new backup file, e.g.,"conf_backup_20200223-081227.tar"
     * @return
     */
    public String generateBackupFileName() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern(BACKUP_FILENAME_DATE_TIME_FORMAT);
        return "conf_backup_" + LocalDateTime.now().format(dtf) + ".tar";
    }

    private String getFileNotFoundExceptionMessage(String filename) {
        StringBuilder sb = new StringBuilder();
        sb.append("Backup file with name ").append(filename).append(" not found");
        return sb.toString();
    }
}
