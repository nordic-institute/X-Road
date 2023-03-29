/**
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

import ee.ria.xroad.common.util.process.ExternalProcessRunner;

import org.niis.xroad.cs.admin.api.dto.HAConfigStatus;
import org.niis.xroad.cs.admin.api.service.SystemParameterService;
import org.niis.xroad.restapi.common.backup.repository.BackupRepository;
import org.niis.xroad.restapi.common.backup.service.ConfigurationRestorationService;
import org.niis.xroad.restapi.config.audit.AuditDataHelper;
import org.niis.xroad.restapi.service.ApiKeyService;
import org.niis.xroad.restapi.util.FormatUtils;
import org.niis.xroad.restapi.util.PersistenceUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class CentralServerConfigurationRestorationService extends ConfigurationRestorationService {

    private final SystemParameterService systemParameterService;
    private final HAConfigStatus currentHaConfigStatus;

    public CentralServerConfigurationRestorationService(
            ExternalProcessRunner externalProcessRunner, BackupRepository backupRepository, ApiKeyService apiKeyService,
            AuditDataHelper auditDataHelper, PersistenceUtils persistenceUtils, ApplicationEventPublisher eventPublisher,
            SystemParameterService systemParameterService, HAConfigStatus haConfigStatus,
            @Value("${script.restore-configuration.path}") String configurationRestoreScriptPath
    ) {
        super(externalProcessRunner, backupRepository, apiKeyService, auditDataHelper,
                persistenceUtils, eventPublisher, configurationRestoreScriptPath);
        this.systemParameterService = systemParameterService;
        this.currentHaConfigStatus = haConfigStatus;
    }

    @Override
    protected String[] buildArguments(String backupFilePath) {
        String encodedInstanceIdentifier = FormatUtils.encodeStringToBase64(systemParameterService.getInstanceIdentifier());
        String encodedBackupPath = FormatUtils.encodeStringToBase64(backupFilePath);

        String configurationRestoreScriptArgs = "-b -i %s -f %s";
        String argumentsString = String
                .format(configurationRestoreScriptArgs, encodedInstanceIdentifier, encodedBackupPath)
                .trim();

        if (currentHaConfigStatus.isHaConfigured()) {
            String encodedHaNodeName = FormatUtils.encodeStringToBase64(currentHaConfigStatus.getCurrentHaNodeName());

            String haNodeNameArg = "-n %s";
            String haNodeNameArgumentString = String
                    .format(haNodeNameArg, encodedHaNodeName)
                    .trim();

            argumentsString = argumentsString + " " + haNodeNameArgumentString;
        }

        return argumentsString.split("\\s+");
    }
}
