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
