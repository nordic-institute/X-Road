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
package org.niis.xroad.cs.admin.core.service;

import ee.ria.xroad.common.util.EncoderUtils;
import ee.ria.xroad.common.util.TimeUtils;
import ee.ria.xroad.common.util.process.ExternalProcessRunner;
import ee.ria.xroad.common.util.process.ProcessFailedException;
import ee.ria.xroad.common.util.process.ProcessNotExecutableException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.exception.InternalServerErrorException;
import org.niis.xroad.cs.admin.api.dto.BackupFile;
import org.niis.xroad.cs.admin.api.dto.HAConfigStatus;
import org.niis.xroad.cs.admin.api.service.BackupService;
import org.niis.xroad.cs.admin.api.service.ConfigurationBackupGenerator;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;
import org.niis.xroad.cs.admin.core.repository.BackupRepository;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Optional;

import static org.niis.xroad.common.core.exception.ErrorCode.BACKUP_GENERATION_FAILED;

@Slf4j
@RequiredArgsConstructor
@Component
public class ConfigurationBackupGeneratorImpl implements ConfigurationBackupGenerator {
    private static final String BACKUP_FILENAME_DATE_TIME_FORMAT = "yyyyMMdd-HHmmss";

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(BACKUP_FILENAME_DATE_TIME_FORMAT);
    private static final String FILE_NAME_FORMAT = "conf_backup_%s.gpg";
    private static final String ARG_VALUES_AS_ENC_BASE64 = "-b";
    private static final String ARG_INSTANCE_ID = "-i";
    private static final String ARG_NODE_NAME = "-n";
    private static final String ARG_BACKUP_FILE_PATH = "-f";

    private final SystemParameterService systemParameterService;
    private final HAConfigStatus haConfigStatus;

    @Value("${script.generate-backup.path}")
    private final String generateBackupScriptPath;
    private final BackupService backupService;
    private final BackupRepository backupRepository;

    private final ExternalProcessRunner externalProcessRunner;
    private final AuditDataHelper auditDataHelper;

    /**
     * Generate a new backup file
     * @return
     * @throws InterruptedException if the thread the backup process is interrupted and the backup fails. <b>The
     *                              interrupted thread has already been handled with so you can choose to ignore this exception if you
     *                              so please.</b>
     */
    @Override
    public BackupFile generateBackup() throws InterruptedException {
        String filename = generateBackupFileName();

        auditDataHelper.putBackupFilename(backupRepository.getAbsoluteBackupFilePath(filename));

        try {
            var args = getScriptArgs(filename);
            log.info("Run configuration backup with command '"
                    + generateBackupScriptPath + " " + Arrays.toString(args) + "'");

            ExternalProcessRunner.ProcessResult processResult = externalProcessRunner
                    .executeAndThrowOnFailure(generateBackupScriptPath, args);

            log.info(" --- Backup script console output - START --- ");
            log.info(String.join("\n", processResult.getProcessOutput()));
            log.info(" --- Backup script console output - END --- ");
        } catch (ProcessNotExecutableException | ProcessFailedException e) {
            throw new InternalServerErrorException(e, BACKUP_GENERATION_FAILED.build());
        }

        Optional<BackupFile> backupFile = backupService.getBackup(filename);
        if (backupFile.isEmpty()) {
            throw new InternalServerErrorException(BACKUP_GENERATION_FAILED.build());
        }
        return backupFile.get();
    }

    private String[] getScriptArgs(String backupFilename) {
        final var instanceIdentifier = systemParameterService.getInstanceIdentifier();
        var args = new ArrayList<String>();
        args.add(ARG_VALUES_AS_ENC_BASE64);

        args.add(ARG_INSTANCE_ID);
        args.add(EncoderUtils.encodeBase64(instanceIdentifier));

        if (haConfigStatus.isHaConfigured()) {
            args.add(ARG_NODE_NAME);
            args.add(EncoderUtils.encodeBase64(haConfigStatus.getCurrentHaNodeName()));
        }

        args.add(ARG_BACKUP_FILE_PATH);
        args.add(EncoderUtils.encodeBase64(backupRepository.getConfigurationBackupPath() + backupFilename));
        return args.toArray(new String[0]);
    }

    private String generateBackupFileName() {
        return String.format(FILE_NAME_FORMAT, TimeUtils.localDateTimeNow().format(DATE_TIME_FORMATTER));
    }

}
