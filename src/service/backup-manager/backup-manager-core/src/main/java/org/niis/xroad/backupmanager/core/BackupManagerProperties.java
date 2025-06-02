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

package org.niis.xroad.backupmanager.core;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

@ConfigMapping(prefix = "xroad.backup-manager")
public interface BackupManagerProperties {

    @WithName("backup-location")
    @WithDefault("/var/lib/xroad/backup")
    String backupLocation();

    @WithName("valid-filename-pattern")
    @WithDefault("^(?!\\.)[\\w\\.\\-]+\\.gpg$")
    String validFilenamePattern();

    @WithName("autobackup-cron-expression")
    @WithDefault("0 15 3 * * ?")
    String autoBackupCronExpression();

    @WithName("autobackup-script-path")
    @WithDefault("/usr/share/xroad/scripts/autobackup_xroad_proxy_configuration.sh")
    String autoBackupScriptPath();

    @WithName("autobackup-delete-old-backups-cron")
//    @WithDefault("10 * * * * ?")    <--- the default,
    @WithDefault("0 0 4 * * ?")
    String autoBackupDeleteOldBackupsCron();

    @WithName("autobackup-keep-for")
    @WithDefault("30d")
    Duration autoBackupKeepFor();

    @WithName("backup-script-path")
    @WithDefault("/usr/share/xroad/scripts/backup_xroad_proxy_configuration.sh")
    String backupScriptPath();

    @WithName("restore-script-path")
    @WithDefault("/usr/share/xroad/scripts/restore_xroad_proxy_configuration.sh")
    String restoreScriptPath();

    @WithName("generate-gpg-keypair-path")
    @WithDefault("/usr/share/xroad/scripts/generate_gpg_keypair.sh")
    String generateGpgKeypairScriptPath();

    @WithName("gpgkeys-home")
    @WithDefault("/etc/xroad/gpghome")
    String gpgKeysHomePath(); //also hardcoded in the scripts

    @WithName("backup-encryption-enabled")
    @WithDefault("false")
    boolean backupEncryptionEnabled();

    @WithName("backup-encryption-keyids")
    Optional<List<String>> backupEncryptionKeyids();

}
