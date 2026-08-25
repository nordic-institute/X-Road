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

package org.niis.xroad.auxiliaryservice.core.config;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.properties.config.XRoadConfig;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.niis.xroad.common.properties.config.keys.AuxiliaryServiceConfigKeys.BACKUP_AUTOBACKUP_CRON_EXPRESSION;
import static org.niis.xroad.common.properties.config.keys.AuxiliaryServiceConfigKeys.BACKUP_AUTOBACKUP_DELETE_OLD_BACKUPS_CRON;
import static org.niis.xroad.common.properties.config.keys.AuxiliaryServiceConfigKeys.BACKUP_AUTOBACKUP_KEEP_FOR;
import static org.niis.xroad.common.properties.config.keys.AuxiliaryServiceConfigKeys.BACKUP_AUTOBACKUP_SCRIPT_PATH;
import static org.niis.xroad.common.properties.config.keys.AuxiliaryServiceConfigKeys.BACKUP_CREATE_BACKUP_METADATA_PATH;
import static org.niis.xroad.common.properties.config.keys.AuxiliaryServiceConfigKeys.BACKUP_ENCRYPTION_ENABLED;
import static org.niis.xroad.common.properties.config.keys.AuxiliaryServiceConfigKeys.BACKUP_ENCRYPTION_KEYIDS;
import static org.niis.xroad.common.properties.config.keys.AuxiliaryServiceConfigKeys.BACKUP_FORMAT_VERSION_FILE_PATH;
import static org.niis.xroad.common.properties.config.keys.AuxiliaryServiceConfigKeys.BACKUP_GENERATE_GPG_KEYPAIR_PATH;
import static org.niis.xroad.common.properties.config.keys.AuxiliaryServiceConfigKeys.BACKUP_GPGKEYS_HOME;
import static org.niis.xroad.common.properties.config.keys.AuxiliaryServiceConfigKeys.BACKUP_LOCATION;
import static org.niis.xroad.common.properties.config.keys.AuxiliaryServiceConfigKeys.BACKUP_RESTORE_SCRIPT_PATH;
import static org.niis.xroad.common.properties.config.keys.AuxiliaryServiceConfigKeys.BACKUP_SCRIPT_PATH;
import static org.niis.xroad.common.properties.config.keys.AuxiliaryServiceConfigKeys.BACKUP_VALID_FILENAME_PATTERN;

@RequiredArgsConstructor
public class BackupProperties {

    private final XRoadConfig xRoadConfig;

    public String location() {
        return xRoadConfig.value(BACKUP_LOCATION);
    }

    public String validFilenamePattern() {
        return xRoadConfig.value(BACKUP_VALID_FILENAME_PATTERN);
    }

    public String autoBackupCronExpression() {
        return xRoadConfig.value(BACKUP_AUTOBACKUP_CRON_EXPRESSION);
    }

    public String autoBackupScriptPath() {
        return xRoadConfig.value(BACKUP_AUTOBACKUP_SCRIPT_PATH);
    }

    public String autoBackupDeleteOldBackupsCron() {
        return xRoadConfig.value(BACKUP_AUTOBACKUP_DELETE_OLD_BACKUPS_CRON);
    }

    public Duration autoBackupKeepFor() {
        return xRoadConfig.value(BACKUP_AUTOBACKUP_KEEP_FOR);
    }

    public String scriptPath() {
        return xRoadConfig.value(BACKUP_SCRIPT_PATH);
    }

    public String restoreScriptPath() {
        return xRoadConfig.value(BACKUP_RESTORE_SCRIPT_PATH);
    }

    public String createBackupMetadataPath() {
        return xRoadConfig.value(BACKUP_CREATE_BACKUP_METADATA_PATH);
    }

    public String backupFormatVersionFilePath() {
        return xRoadConfig.value(BACKUP_FORMAT_VERSION_FILE_PATH);
    }

    public String generateGpgKeypairScriptPath() {
        return xRoadConfig.value(BACKUP_GENERATE_GPG_KEYPAIR_PATH);
    }

    public String gpgKeysHomePath() {
        return xRoadConfig.value(BACKUP_GPGKEYS_HOME);
    }

    public boolean encryptionEnabled() {
        return xRoadConfig.value(BACKUP_ENCRYPTION_ENABLED);
    }

    public Optional<List<String>> encryptionKeyids() {
        return xRoadConfig.valueOpt(BACKUP_ENCRYPTION_KEYIDS).map(List::of);
    }

}
