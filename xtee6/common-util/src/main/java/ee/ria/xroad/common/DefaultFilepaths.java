package ee.ria.xroad.common;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.EnumSet;
import java.util.Set;

import static java.nio.file.attribute.PosixFilePermission.*;

/**
 * Default file paths for application configuration and artifacts based on FHS.
 */
public final class DefaultFilepaths {

    static final String CONF_PATH = "/etc/xroad/";

    static final String SERVER_DATABASE_PROPERTIES = "db.properties";

    static final String KEY_CONFIGURATION_FILE = "signer/keyconf.xml";

    static final String DEVICE_CONFIGURATION_FILE = "signer/devices.ini";

    static final String CONFIGURATION_ANCHOR_FILE = "configuration-anchor.xml";

    static final String CONFIGURATION_PATH = "globalconf";

    static final String LOG_PATH = "/var/log/xroad/";

    static final String SECURE_LOG_PATH = "/var/lib/xroad/";

    static final String OCSP_CACHE_PATH = "/var/cache/xroad/";

    static final String CONF_BACKUP_PATH = "/var/lib/xroad/backup/";

    static final String V5_IMPORT_PATH = "/var/lib/xroad/import";

    static final String DISTRIBUTED_GLOBALCONF_PATH = "/var/lib/xroad/public";

    static final String SECURE_LOG_FILE = SECURE_LOG_PATH + "slog";

    static final String TEMP_FILES_PATH = "/var/tmp/xroad/";

    static final String ASYNC_DB_PATH = "/var/spool/xroad/";

    static final String ASYNC_SENDER_CONFIGURATION_FILE =
            "async-sender.properties";

    static final String MONITOR_AGENT_CONFIGURATION_FILE =
            "monitor-agent.ini";

    static final String JETTY_SERVERPROXY_CONFIGURATION_FILE =
            "jetty/serverproxy.xml";

    static final String JETTY_CLIENTPROXY_CONFIGURATION_FILE =
            "jetty/clientproxy.xml";

    private static FileAttribute<Set<PosixFilePermission>> permissions =
            PosixFilePermissions.asFileAttribute(EnumSet.of(
                    OWNER_READ, OWNER_WRITE, GROUP_READ, GROUP_WRITE));

    /**
     * Creates a temporary file on disk (location specified by
     * SystemProperties.getTempFilesPath()) and returns its path.
     * @param prefix the prefix to use
     * @param suffix the suffix to use
     * @return path to the created temporary file
     * @throws IOException if an error occurs
     */
    public static Path createTempFile(String prefix, String suffix)
            throws IOException {
        Path tempDirPath = Paths.get(SystemProperties.getTempFilesPath());
        return createTempFile(tempDirPath, prefix, suffix);
    }

    /**
     * Creates a temporary file in the specified location. Also creates the
     * location if it does not exist.
     * @param tempDirPath the location
     * @param prefix the prefix to use
     * @param suffix the suffix to use
     * @return path to the created temporary file
     * @throws IOException if an error occurs
     */
    public static Path createTempFile(Path tempDirPath, String prefix,
            String suffix) throws IOException {
        if (!Files.exists(tempDirPath)) {
            Files.createDirectory(tempDirPath);
        }

        return Files.createTempFile(tempDirPath, prefix, suffix, permissions);
    }

    private DefaultFilepaths() {
    }
}
