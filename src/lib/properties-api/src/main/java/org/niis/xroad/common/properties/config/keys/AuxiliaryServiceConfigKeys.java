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

package org.niis.xroad.common.properties.config.keys;

import org.niis.xroad.common.properties.config.ConfigKey;
import org.niis.xroad.common.properties.config.ConfigKeyProvider;
import org.niis.xroad.common.properties.config.Scope;

import java.time.Duration;

/** Auxiliary-service keys ({@code xroad.auxiliary-service.backup.*} and {@code .message-log.*}). */
@SuppressWarnings("checkstyle:MagicNumber") // a keys registry: default literals are the point
public final class AuxiliaryServiceConfigKeys implements ConfigKeyProvider {

    private static final Scope AUXILIARY_SERVICE = Scope.of("xroad.auxiliary-service", "auxiliary-service");
    private static final Scope BACKUP = AUXILIARY_SERVICE.child("backup");
    private static final Scope MESSAGE_LOG = AUXILIARY_SERVICE.child("message-log");

    private static final AuxiliaryServiceConfigKeys INSTANCE = new AuxiliaryServiceConfigKeys();

    /** {@code xroad.auxiliary-service.backup.location}. */
    public static final ConfigKey<String> BACKUP_LOCATION = BACKUP
            .string("location")
            .withDefaultValue("/var/lib/xroad/backup")
            .build();

    /** {@code xroad.auxiliary-service.backup.valid-filename-pattern}. */
    public static final ConfigKey<String> BACKUP_VALID_FILENAME_PATTERN = BACKUP
            .string("valid-filename-pattern")
            .withDefaultValue("^(?!\\.)[\\w\\.\\-]+\\.gpg$")
            .build();

    /** {@code xroad.auxiliary-service.backup.autobackup-cron-expression}. */
    public static final ConfigKey<String> BACKUP_AUTOBACKUP_CRON_EXPRESSION = BACKUP
            .string("autobackup-cron-expression")
            .withDefaultValue("0 15 3 * * ?")
            .build();

    /** {@code xroad.auxiliary-service.backup.autobackup-script-path}. */
    public static final ConfigKey<String> BACKUP_AUTOBACKUP_SCRIPT_PATH = BACKUP
            .string("autobackup-script-path")
            .withDefaultValue("/usr/share/xroad/scripts/autobackup_xroad_proxy_configuration.sh")
            .withContainerDefaultValue("/usr/share/xroad/scripts/containerised/autobackup.sh")
            .build();

    /** {@code xroad.auxiliary-service.backup.autobackup-delete-old-backups-cron}. */
    public static final ConfigKey<String> BACKUP_AUTOBACKUP_DELETE_OLD_BACKUPS_CRON = BACKUP
            .string("autobackup-delete-old-backups-cron")
            .withDefaultValue("0 0 4 * * ?")
            .build();

    /** {@code xroad.auxiliary-service.backup.autobackup-keep-for}. */
    public static final ConfigKey<Duration> BACKUP_AUTOBACKUP_KEEP_FOR = BACKUP
            .keyDuration("autobackup-keep-for")
            .withDefaultValue(Duration.ofDays(30))
            .build();

    /** {@code xroad.auxiliary-service.backup.script-path}. */
    public static final ConfigKey<String> BACKUP_SCRIPT_PATH = BACKUP
            .string("script-path")
            .withDefaultValue("/usr/share/xroad/scripts/backup_xroad_proxy_configuration.sh")
            .withContainerDefaultValue("/usr/share/xroad/scripts/containerised/create_backup.sh")
            .build();

    /** {@code xroad.auxiliary-service.backup.restore-script-path}. */
    public static final ConfigKey<String> BACKUP_RESTORE_SCRIPT_PATH = BACKUP
            .string("restore-script-path")
            .withDefaultValue("/usr/share/xroad/scripts/restore_xroad_proxy_configuration.sh")
            .withContainerDefaultValue("/usr/share/xroad/scripts/containerised/restore_backup.sh")
            .build();

    /** {@code xroad.auxiliary-service.backup.generate-gpg-keypair-path}. */
    public static final ConfigKey<String> BACKUP_GENERATE_GPG_KEYPAIR_PATH = BACKUP
            .string("generate-gpg-keypair-path")
            .withDefaultValue("/usr/share/xroad/scripts/generate_gpg_keypair.sh")
            .build();

    /** {@code xroad.auxiliary-service.backup.gpgkeys-home}. */
    public static final ConfigKey<String> BACKUP_GPGKEYS_HOME = BACKUP
            .string("gpgkeys-home")
            .withDefaultValue("/etc/xroad/gpghome")
            .build();

    /** {@code xroad.auxiliary-service.backup.encryption-enabled}. */
    public static final ConfigKey<Boolean> BACKUP_ENCRYPTION_ENABLED = BACKUP
            .bool("encryption-enabled")
            .withDefaultValue(false)
            .build();

    /** {@code xroad.auxiliary-service.backup.encryption-keyids}. */
    public static final ConfigKey<String[]> BACKUP_ENCRYPTION_KEYIDS = BACKUP
            .stringArray("encryption-keyids")
            .build();

    /** {@code xroad.auxiliary-service.message-log.archive-cron}. */
    public static final ConfigKey<String> MESSAGE_LOG_ARCHIVE_CRON = MESSAGE_LOG
            .string("archive-cron")
            .withDefaultValue("0 0 0/6 1/1 * ?")
            .build();

    /** {@code xroad.auxiliary-service.message-log.clean-cron}. */
    public static final ConfigKey<String> MESSAGE_LOG_CLEAN_CRON = MESSAGE_LOG
            .string("clean-cron")
            .withDefaultValue("0 0 0/12 1/1 * ?")
            .build();

    /** {@code xroad.auxiliary-service.message-log.command-path}. */
    public static final ConfigKey<String> MESSAGE_LOG_COMMAND_PATH = MESSAGE_LOG
            .string("command-path")
            .withDefaultValue("/usr/share/xroad/bin/xroad-message-log-archiver")
            .withContainerDefaultValue("/usr/share/xroad/scripts/containerised/message_log_archiver.sh")
            .build();

    private AuxiliaryServiceConfigKeys() {
    }

    /** @return the provider singleton (pass to {@code XRoadConfigBuilder.register(...)}). */
    public static AuxiliaryServiceConfigKeys instance() {
        return INSTANCE;
    }

    @Override
    public Scope scope() {
        return AUXILIARY_SERVICE;
    }
}
