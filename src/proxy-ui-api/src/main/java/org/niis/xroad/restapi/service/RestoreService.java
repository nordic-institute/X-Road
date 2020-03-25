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

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.identifier.SecurityServerId;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.restapi.cache.CurrentSecurityServerId;
import org.niis.xroad.restapi.exceptions.DeviationAwareRuntimeException;
import org.niis.xroad.restapi.util.FormatUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

import java.io.File;

@Slf4j
@Service
@PreAuthorize("isAuthenticated()")
public class RestoreService {
    @Setter
    private String configurationRestorerScriptPath;
    @Setter
    private String configurationRestorerScriptArgs;
    @Setter
    private String configurationBackupPath;

    private final ExternalProcessRunner externalProcessRunner;
    private final CurrentSecurityServerId currentSecurityServerId;

    @Autowired
    public RestoreService(ExternalProcessRunner externalProcessRunner,
            @Value("${script.restore-configuration.path}") String configurationRestorerScriptPath,
            @Value("${script.restore-configuration.args}") String configurationRestorerScriptArgs,
            CurrentSecurityServerId currentSecurityServerId) {
        this.externalProcessRunner = externalProcessRunner;
        this.configurationRestorerScriptPath = configurationRestorerScriptPath;
        this.configurationRestorerScriptArgs = configurationRestorerScriptArgs;
        this.currentSecurityServerId = currentSecurityServerId;
        this.configurationBackupPath = SystemProperties.getConfBackupPath();
    }

    public void restoreFromBackup(String fileName) throws BackupFileNotFoundException, InterruptedException {
        String backupFilePath = configurationBackupPath + fileName;
        File backupFile = new File(backupFilePath);
        if (!backupFile.isFile()) {
            throw new BackupFileNotFoundException("backup file " + backupFilePath + " does not exist");
        }
        String[] arguments = buildArguments(backupFilePath);
        try {
            ExternalProcessRunner.ProcessResult processResult = externalProcessRunner
                    .executeAndThrowOnFailure(configurationRestorerScriptPath, arguments);

            int exitCode = processResult.getExitCode();

            String restoreFinishedLogMsg = String.format("Restoring configuration finished with exit status %s",
                    exitCode);
            log.info(restoreFinishedLogMsg);
            log.info(" --- Restore script console output - START --- ");
            log.info(ExternalProcessRunner.processOutputToString(processResult.getProcessOutput()));
            log.info(" --- Restore script console output - END --- ");
        } catch (ProcessFailedException | ProcessNotExecutableException e) {
            throw new DeviationAwareRuntimeException("restoring from a backup failed", e.getErrorDeviation());
        }
    }

    /**
     * Encodes args with base64 and returns all options and args as an array
     * @param backupFilePath
     * @return
     */
    private String[] buildArguments(String backupFilePath) {
        SecurityServerId securityServerId = currentSecurityServerId.getServerId();
        String encodedOwner = FormatUtils.encodeStringToBase64(securityServerId.toShortString());
        String encodedBackupPath = FormatUtils.encodeStringToBase64(backupFilePath);
        String argumentsString = String
                .format(configurationRestorerScriptArgs, encodedOwner, encodedBackupPath)
                .trim();
        return argumentsString.split("\\s+");
    }
}
