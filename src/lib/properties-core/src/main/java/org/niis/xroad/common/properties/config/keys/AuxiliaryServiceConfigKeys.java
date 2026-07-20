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

import org.niis.xroad.common.properties.config.Category;
import org.niis.xroad.common.properties.config.ConfigKey;
import org.niis.xroad.common.properties.config.ConfigKeyProvider;
import org.niis.xroad.common.properties.config.Prefix;

import java.time.Duration;

/** Auxiliary-service keys ({@code xroad.auxiliary-service.backup.*} and {@code .message-log.*}). */
@SuppressWarnings("checkstyle:MagicNumber") // a keys registry: default literals are the point
public final class AuxiliaryServiceConfigKeys implements ConfigKeyProvider {

    private static final Prefix AUXILIARY_SERVICE = Prefix.of(Category.AUXILIARY_SERVICE, "xroad.auxiliary-service");
    private static final Prefix BACKUP = AUXILIARY_SERVICE.subPrefix("backup");
    private static final Prefix MESSAGE_LOG = AUXILIARY_SERVICE.subPrefix("message-log");
    private static final Prefix RPC = AUXILIARY_SERVICE.subPrefix("rpc");
    private static final Prefix READINESS_CHECK = AUXILIARY_SERVICE.subPrefix("readiness-check");
    private static final Prefix READINESS_CHECK_KUBERNETES = READINESS_CHECK.subPrefix("kubernetes");

    private static final AuxiliaryServiceConfigKeys INSTANCE = new AuxiliaryServiceConfigKeys();

    /** {@code xroad.auxiliary-service.backup.location}. */
    public static final ConfigKey<String> BACKUP_LOCATION = BACKUP
            .string("location")
            .withDefaultValue("/var/lib/xroad/backup")
            .exposedInUi()
            .build();

    /** {@code xroad.auxiliary-service.backup.valid-filename-pattern}. */
    public static final ConfigKey<String> BACKUP_VALID_FILENAME_PATTERN = BACKUP
            .string("valid-filename-pattern")
            .withDefaultValue("^(?!\\.)[\\w\\.\\-]+\\.gpg$")
            .exposedInUi()
            .build();

    /** {@code xroad.auxiliary-service.backup.autobackup-cron-expression}. */
    public static final ConfigKey<String> BACKUP_AUTOBACKUP_CRON_EXPRESSION = BACKUP
            .string("autobackup-cron-expression")
            .withDefaultValue("0 15 3 * * ?")
            .exposedInUi()
            .build();

    /** {@code xroad.auxiliary-service.backup.autobackup-script-path}. */
    public static final ConfigKey<String> BACKUP_AUTOBACKUP_SCRIPT_PATH = BACKUP
            .string("autobackup-script-path")
            .withDefaultValue("/usr/share/xroad/scripts/autobackup_xroad_proxy_configuration.sh")
            .withContainerDefaultValue("/usr/share/xroad/scripts/containerised/autobackup.sh")
            .exposedInUi()
            .build();

    /** {@code xroad.auxiliary-service.backup.autobackup-delete-old-backups-cron}. */
    public static final ConfigKey<String> BACKUP_AUTOBACKUP_DELETE_OLD_BACKUPS_CRON = BACKUP
            .string("autobackup-delete-old-backups-cron")
            .withDefaultValue("0 0 4 * * ?")
            .exposedInUi()
            .build();

    /** {@code xroad.auxiliary-service.backup.autobackup-keep-for}. */
    public static final ConfigKey<Duration> BACKUP_AUTOBACKUP_KEEP_FOR = BACKUP
            .keyDuration("autobackup-keep-for")
            .withDefaultValue(Duration.ofDays(30))
            .exposedInUi()
            .build();

    /** {@code xroad.auxiliary-service.backup.script-path}. */
    public static final ConfigKey<String> BACKUP_SCRIPT_PATH = BACKUP
            .string("script-path")
            .withDefaultValue("/usr/share/xroad/scripts/backup_xroad_proxy_configuration.sh")
            .withContainerDefaultValue("/usr/share/xroad/scripts/containerised/create_backup.sh")
            .exposedInUi()
            .build();

    /** {@code xroad.auxiliary-service.backup.restore-script-path}. */
    public static final ConfigKey<String> BACKUP_RESTORE_SCRIPT_PATH = BACKUP
            .string("restore-script-path")
            .withDefaultValue("/usr/share/xroad/scripts/restore_xroad_proxy_configuration.sh")
            .withContainerDefaultValue("/usr/share/xroad/scripts/containerised/restore_backup.sh")
            .exposedInUi()
            .build();

    /** {@code xroad.auxiliary-service.backup.create-backup-metadata-path}. */
    public static final ConfigKey<String> BACKUP_CREATE_BACKUP_METADATA_PATH = BACKUP
            .string("create-backup-metadata-path")
            .withDefaultValue("/usr/share/xroad/scripts/_create_backup_metadata.sh")
            .withContainerDefaultValue("/usr/share/xroad/scripts/containerised/create_backup_metadata.sh")
            .exposedInUi()
            .build();

    /** {@code xroad.auxiliary-service.backup.backup-format-version-file-path}. */
    public static final ConfigKey<String> BACKUP_FORMAT_VERSION_FILE_PATH = BACKUP
            .string("backup-format-version-file-path")
            .withDefaultValue("/usr/share/xroad/scripts/_backup_format_version")
            .withContainerDefaultValue("/usr/share/xroad/scripts/containerised/backup_format_version")
            .exposedInUi()
            .build();

    /** {@code xroad.auxiliary-service.backup.generate-gpg-keypair-path}. */
    public static final ConfigKey<String> BACKUP_GENERATE_GPG_KEYPAIR_PATH = BACKUP
            .string("generate-gpg-keypair-path")
            .withDefaultValue("/usr/share/xroad/scripts/generate_gpg_keypair.sh")
            .exposedInUi()
            .build();

    /** {@code xroad.auxiliary-service.backup.gpgkeys-home}. */
    public static final ConfigKey<String> BACKUP_GPGKEYS_HOME = BACKUP
            .string("gpgkeys-home")
            .withDefaultValue("/etc/xroad/gpghome")
            .exposedInUi()
            .build();

    /** {@code xroad.auxiliary-service.backup.encryption-enabled}. */
    public static final ConfigKey<Boolean> BACKUP_ENCRYPTION_ENABLED = BACKUP
            .bool("encryption-enabled")
            .withDefaultValue(false)
            .exposedInUi()
            .build();

    /** {@code xroad.auxiliary-service.backup.encryption-keyids}. */
    public static final ConfigKey<String[]> BACKUP_ENCRYPTION_KEYIDS = BACKUP
            .stringArray("encryption-keyids")
            .exposedInUi()
            .build();

    /** {@code xroad.auxiliary-service.message-log.archive-cron}. */
    public static final ConfigKey<String> MESSAGE_LOG_ARCHIVE_CRON = MESSAGE_LOG
            .string("archive-cron")
            .withDefaultValue("0 0 0/6 1/1 * ?")
            .exposedInUi()
            .build();

    /** {@code xroad.auxiliary-service.message-log.clean-cron}. */
    public static final ConfigKey<String> MESSAGE_LOG_CLEAN_CRON = MESSAGE_LOG
            .string("clean-cron")
            .withDefaultValue("0 0 0/12 1/1 * ?")
            .exposedInUi()
            .build();

    /** {@code xroad.auxiliary-service.message-log.command-path}. */
    public static final ConfigKey<String> MESSAGE_LOG_COMMAND_PATH = MESSAGE_LOG
            .string("command-path")
            .withDefaultValue("/usr/share/xroad/bin/xroad-message-log-archiver")
            .withContainerDefaultValue("/usr/share/xroad/scripts/containerised/message_log_archiver.sh")
            .exposedInUi()
            .build();

    // --- xroad.auxiliary-service.rpc --------------------------------------------

    /** {@code xroad.auxiliary-service.rpc.enabled}. */
    public static final ConfigKey<Boolean> RPC_ENABLED = RPC
            .bool("enabled")
            .withDefaultValue(true)
            .exposedInUi()
            .build();

    /** {@code xroad.auxiliary-service.rpc.listen-address}. */
    public static final ConfigKey<String> RPC_LISTEN_ADDRESS = RPC
            .string("listen-address")
            .withDefaultValue("127.0.0.1")
            .withContainerDefaultValue("0.0.0.0")
            .exposedInUi()
            .build();

    /** {@code xroad.auxiliary-service.rpc.port}. */
    public static final ConfigKey<Integer> RPC_PORT = RPC
            .integer("port")
            .withDefaultValue(7665)
            .exposedInUi()
            .build();

    // --- xroad.auxiliary-service.readiness-check.kubernetes ---------------------

    /** {@code xroad.auxiliary-service.readiness-check.kubernetes.service-host} — optional. */
    public static final ConfigKey<String> READINESS_CHECK_KUBERNETES_SERVICE_HOST = READINESS_CHECK_KUBERNETES
            .string("service-host")
            .exposedInUi()
            .build();

    /** {@code xroad.auxiliary-service.readiness-check.kubernetes.service-port} — optional. */
    public static final ConfigKey<String> READINESS_CHECK_KUBERNETES_SERVICE_PORT = READINESS_CHECK_KUBERNETES
            .string("service-port")
            .exposedInUi()
            .build();

    /** {@code xroad.auxiliary-service.readiness-check.kubernetes.token-path}. */
    public static final ConfigKey<String> READINESS_CHECK_KUBERNETES_TOKEN_PATH = READINESS_CHECK_KUBERNETES
            .string("token-path")
            .withDefaultValue("/var/run/secrets/kubernetes.io/serviceaccount/token")
            .exposedInUi()
            .build();

    /** {@code xroad.auxiliary-service.readiness-check.kubernetes.ca-cert-path}. */
    public static final ConfigKey<String> READINESS_CHECK_KUBERNETES_CA_CERT_PATH = READINESS_CHECK_KUBERNETES
            .string("ca-cert-path")
            .withDefaultValue("/var/run/secrets/kubernetes.io/serviceaccount/ca.crt")
            .exposedInUi()
            .build();

    /** {@code xroad.auxiliary-service.readiness-check.kubernetes.connect-timeout-ms}. */
    public static final ConfigKey<Integer> READINESS_CHECK_KUBERNETES_CONNECT_TIMEOUT_MS = READINESS_CHECK_KUBERNETES
            .integer("connect-timeout-ms")
            .withDefaultValue(5000)
            .exposedInUi()
            .build();

    /** {@code xroad.auxiliary-service.readiness-check.kubernetes.read-timeout-ms}. */
    public static final ConfigKey<Integer> READINESS_CHECK_KUBERNETES_READ_TIMEOUT_MS = READINESS_CHECK_KUBERNETES
            .integer("read-timeout-ms")
            .withDefaultValue(5000)
            .exposedInUi()
            .build();

    private AuxiliaryServiceConfigKeys() {
    }

    /** @return the provider singleton (pass to {@code XRoadConfigBuilder.register(...)}). */
    public static AuxiliaryServiceConfigKeys instance() {
        return INSTANCE;
    }

    @Override
    public Prefix scope() {
        return AUXILIARY_SERVICE;
    }
}
