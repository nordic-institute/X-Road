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

package org.niis.xroad.cs.admin.core.service;

import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.TimeUtils;
import ee.ria.xroad.common.util.process.ExternalProcessRunner;

import org.niis.xroad.cs.admin.api.dto.HAConfigStatus;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;
import org.niis.xroad.restapi.common.backup.repository.BackupRepository;
import org.niis.xroad.restapi.common.backup.service.BackupService;
import org.niis.xroad.restapi.common.backup.service.BaseConfigurationBackupGenerator;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;

@Component
public class CentralServerConfigurationBackupGenerator extends BaseConfigurationBackupGenerator {

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(BACKUP_FILENAME_DATE_TIME_FORMAT);
    private static final String FILE_NAME_FORMAT = "conf_backup_%s.gpg";
    private static final String ARG_VALUES_AS_ENC_BASE64 = "-b";
    private static final String ARG_INSTANCE_ID = "-i";
    private static final String ARG_NODE_NAME = "-n";
    private static final String ARG_BACKUP_FILE_PATH = "-f";

    private final SystemParameterService systemParameterService;
    private final HAConfigStatus haConfigStatus;

    public CentralServerConfigurationBackupGenerator(
            @Value("${script.generate-backup.path}") final String generateBackupScriptPath,
            final BackupService backupService,
            final BackupRepository backupRepository,
            final ExternalProcessRunner externalProcessRunner,
            final AuditDataHelper auditDataHelper, SystemParameterService systemParameterService, HAConfigStatus haConfigStatus) {
        super(generateBackupScriptPath, backupService, backupRepository, externalProcessRunner, auditDataHelper);
        this.systemParameterService = systemParameterService;
        this.haConfigStatus = haConfigStatus;
    }

    @Override
    protected String[] getScriptArgs(String backupFilename) {
        final var instanceIdentifier = systemParameterService.getInstanceIdentifier();
        var args = new ArrayList<String>();
        args.add(ARG_VALUES_AS_ENC_BASE64);

        args.add(ARG_INSTANCE_ID);
        args.add(CryptoUtils.encodeBase64(instanceIdentifier));

        if (haConfigStatus.isHaConfigured()) {
            args.add(ARG_NODE_NAME);
            args.add(CryptoUtils.encodeBase64(haConfigStatus.getCurrentHaNodeName()));
        }

        args.add(ARG_BACKUP_FILE_PATH);
        args.add(CryptoUtils.encodeBase64(backupRepository.getConfigurationBackupPath() + backupFilename));
        return args.toArray(new String[0]);
    }

    @Override
    protected String generateBackupFileName() {
        return String.format(FILE_NAME_FORMAT, TimeUtils.localDateTimeNow().format(DATE_TIME_FORMATTER));
    }
}
