package ee.ria.xroad.proxy.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;

import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * Utils for querying version information
 */
@Slf4j
public final class VersionUtil {

    private VersionUtil() { }

    /**
     * Read installed proxy version information from package
     * @return version string of format
     */
    public static String readProxyVersion() {
        String version;
        try {
            String cmd;
            if (Files.exists(Paths.get("/etc/redhat-release"))) {
                cmd = "rpm -q --queryformat '%{VERSION}-%{RELEASE}' xroad-proxy";
            } else {
                cmd = "dpkg-query -f '${Version}' -W xroad-proxy";
            }
            Process p = Runtime.getRuntime().exec(cmd);
            p.waitFor();
            version = IOUtils.toString(p.getInputStream(), Charset.defaultCharset());
        } catch (Exception ex) {
            version = "unknown";
            log.warn("Unable to read proxy version", ex);
        }
        return version;
    }

    /**
     * Parse/validate version string
     * Accepts version strings of format 6.19.0-0.20180709122743git861f417 and 6.19.0-1
     * Otherwise returns "unknown"
     * @param versionStr
     * @return version string or "unknown" in case the version string does not look valid
     */
    public static String parseVersion(String versionStr) {
        if (!(versionStr.matches("^\\d\\.\\d+\\.\\d+-[1]")
                || versionStr.matches("^\\d\\.\\d+\\.\\d+-[0]\\.\\d+git\\S+"))) {
            versionStr = "unknown";
            log.warn("Unable to read proxy version");
        }
        return versionStr;
    }
}
