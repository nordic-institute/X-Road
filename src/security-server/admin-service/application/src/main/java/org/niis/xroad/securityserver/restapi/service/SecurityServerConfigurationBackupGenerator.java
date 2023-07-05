/**
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
package org.niis.xroad.securityserver.restapi.service;

import ee.ria.xroad.common.identifier.SecurityServerId;
import ee.ria.xroad.common.util.process.ExternalProcessRunner;

import org.niis.xroad.restapi.common.backup.repository.BackupRepository;
import org.niis.xroad.restapi.common.backup.service.BackupService;
import org.niis.xroad.restapi.common.backup.service.BaseConfigurationBackupGenerator;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Component
public class SecurityServerConfigurationBackupGenerator extends BaseConfigurationBackupGenerator {
    private final ServerConfService serverConfService;

    public SecurityServerConfigurationBackupGenerator(@Value("${script.generate-backup.path}") String generateBackupScriptPath,
                                                      BackupService backupService,
                                                      BackupRepository backupRepository,
                                                      ExternalProcessRunner externalProcessRunner,
                                                      AuditDataHelper auditDataHelper,
                                                      ServerConfService serverConfService) {
        super(generateBackupScriptPath, backupService, backupRepository, externalProcessRunner, auditDataHelper);
        this.serverConfService = serverConfService;
    }


    @Override
    protected String[] getScriptArgs(String backupFileName) {
        SecurityServerId securityServerId = serverConfService.getSecurityServerId();


        String fullPath = backupRepository.getConfigurationBackupPath() + backupFileName;
        return new String[]{"-s", securityServerId.toShortString(), "-f", fullPath};
    }

    /**
     * Generate name for a new backup file, e.g.,"conf_backup_20200223-081227.gpg"
     */
    @Override
    protected String generateBackupFileName() {
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern(BACKUP_FILENAME_DATE_TIME_FORMAT);
        return "conf_backup_" + LocalDateTime.now().format(dtf) + ".gpg";
    }
}
