/*
 * The MIT License
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
package ee.ria.xroad.common;

import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;

import java.util.Optional;

/**
 * Contains system-wide constants for system properties.
 */
@Deprecated(forRemoval = true)
public final class SystemProperties {

    private SystemProperties() {
    }

    /**
     * The prefix for all properties.
     */
    public static final String PREFIX = "xroad.";
    private static final String CENTER_PREFIX = PREFIX + "center.";

    // Common -----------------------------------------------------------------

    /**
     * Property name of the temporary files path.
     */
    public static final String TEMP_FILES_PATH =
            PREFIX + "common.temp-files-path";

    /**
     * Property name of the downloaded global configuration directory.
     */
    public static final String CONFIGURATION_PATH =
            PREFIX + "common.configuration-path";

    /**
     * Default minimum supported global conf version on configuration proxy
     */
    public static final String DEFAULT_MINIMUM_CONFIGURATION_PROXY_SERVER_GLOBAL_CONFIGURATION_VERSION = "2";

    /**
     * Minimum supported global conf version on configuration proxy
     **/
    private static final String MINIMUM_CONFIGURATION_PROXY_SERVER_GLOBAL_CONFIGURATION_VERSION =
            PREFIX + "configuration-proxy.minimum-global-configuration-version";

    private static final String FALSE = Boolean.FALSE.toString();

    // Center -----------------------------------------------------------------

    public static final String CENTER_DATABASE_PROPERTIES = CENTER_PREFIX + "database-properties";

    // Misc -------------------------------------------------------------------

    /**
     * Property name of the configuration files path.
     */
    public static final String CONF_PATH = PREFIX + "conf.path";

    // Configuration proxy ------------------------------------------------- //

    /**
     * Property name of the confproxy download script path.
     */
    public static final String CONFIGURATION_PROXY_DOWNLOAD_SCRIPT =
            PREFIX + "configuration-proxy.download-script";

    /**
     * Property name of the confproxy configuration path.
     */
    public static final String CONFIGURATION_PROXY_CONF_PATH =
            PREFIX + "configuration-proxy.configuration-path";

    /**
     * Property name of the confproxy public configuration distribution path.
     */
    public static final String CONFIGURATION_PROXY_GENERATED_CONF_PATH =
            PREFIX + "configuration-proxy.generated-conf-path";

    /**
     * Property name of the confproxy configuration signature digest algorithm.
     */
    public static final String CONFIGURATION_PROXY_SIGNATURE_DIGEST_ALGORITHM_ID =
            PREFIX + "configuration-proxy.signature-digest-algorithm-id";

    /**
     * Property name of the confproxy configuration file hashing algorithm.
     */
    public static final String CONFIGURATION_PROXY_HASH_ALGORITHM_URI =
            PREFIX + "configuration-proxy.hash-algorithm-uri";

    /**
     * Property name of the confproxy webserver address.
     */
    public static final String CONFIGURATION_PROXY_ADDRESS =
            PREFIX + "configuration-proxy.address";

    // Configuration file names and section names -------------------------- //

    public static final String CONF_FILE_COMMON =
            getConfPath() + "conf.d/common.ini";

    public static final String CONF_FILE_NODE =
            getConfPath() + "conf.d/node.ini";

    public static final String CONF_FILE_CENTER =
            getConfPath() + "conf.d/center.ini";

    public static final String CONF_FILE_CONFPROXY =
            getConfPath() + "conf.d/confproxy.ini";

    public static final String CONF_FILE_USER_LOCAL =
            getConfPath() + "conf.d/local.ini";

    public static final String CONF_FILE_ADDON_PATH =
            getConfPath() + "conf.d/addons/";

    // --------------------------------------------------------------------- //

    public static final String DEFAULT_CONNECTOR_HOST = "0.0.0.0";

    /**
     * @return path to the directory where configuration files are located, '/etc/xroad/' by default.
     */
    public static String getConfPath() {
        return getProperty(CONF_PATH, DefaultFilepaths.CONF_PATH);
    }

    /**
     * @return path to the directory where the downloaded global configuration is placed,
     * '/etc/xroad/globalconf/' by default.
     */
    public static String getConfigurationPath() {
        return getProperty(CONFIGURATION_PATH, getConfPath() + DefaultFilepaths.CONFIGURATION_PATH);
    }

    /**
     * @return path to the directory where temporary files are stored, '/var/tmp/xroad/' by default.
     */
    public static String getTempFilesPath() {
        return getProperty(TEMP_FILES_PATH, DefaultFilepaths.TEMP_FILES_PATH);
    }

    /**
     * @return path to the central server database configuration file, '/etc/xroad/db.properties' by default.
     */
    public static String getCenterDatabasePropertiesFile() {
        return getProperty(CENTER_DATABASE_PROPERTIES,
                getConfPath() + DefaultFilepaths.SERVER_DATABASE_PROPERTIES);
    }

    /**
     * @return path to the directory containing configuration proxy configuration files,
     * '/etc/xroad/confproxy' by default.
     */
    public static String getConfigurationProxyConfPath() {
        return getProperty(CONFIGURATION_PROXY_CONF_PATH, getConfPath() + "confproxy/");
    }

    /**
     * @return path to the global configuration download script,
     * '/usr/share/xroad/scripts/download_instance_configuration.sh' by default.
     */
    public static String getConfigurationProxyDownloadScript() {
        return getProperty(CONFIGURATION_PROXY_DOWNLOAD_SCRIPT,
                "/usr/share/xroad/scripts/download_instance_configuration.sh");
    }

    /**
     * @return path to the directory on the configuration proxy where global
     * configuration files are generated for distribution, '/var/lib/xroad/public' by default.
     */
    public static String getConfigurationProxyGeneratedConfPath() {
        return getProperty(CONFIGURATION_PROXY_GENERATED_CONF_PATH,
                DefaultFilepaths.DISTRIBUTED_GLOBALCONF_PATH);
    }

    /**
     * @return ID of the signing digest algorithm the configuration proxy uses when
     * signing generated global configuration directories, 'SHA-512' by default.
     */
    public static DigestAlgorithm getConfigurationProxySignatureDigestAlgorithmId() {
        return Optional.ofNullable(getProperty(CONFIGURATION_PROXY_SIGNATURE_DIGEST_ALGORITHM_ID))
                .map(DigestAlgorithm::ofName)
                .orElse(DigestAlgorithm.SHA512);
    }

    /**
     * @return URI of the hashing algorithm the configuration proxy uses when
     * calculating hashes of files in the global configuratoin directory,
     * 'http://www.w3.org/2001/04/xmlenc#sha512' by default.
     */
    public static DigestAlgorithm getConfigurationProxyHashAlgorithmUri() {
        return Optional.ofNullable(getProperty(CONFIGURATION_PROXY_HASH_ALGORITHM_URI))
                .map(DigestAlgorithm::ofUri)
                .orElse(DigestAlgorithm.SHA512);
    }

    /**
     * @return the host address on which the configuration proxy listens for
     * global configuration download requests, '0.0.0.0' by default.
     */
    public static String getConfigurationProxyAddress() {
        return getProperty(CONFIGURATION_PROXY_ADDRESS, DEFAULT_CONNECTOR_HOST);
    }

    /**
     * @return minimum configuration proxy global configuration version or default
     */
    public static int getMinimumConfigurationProxyGlobalConfigurationVersion() {
        // read the setting
        int minVersion = Integer.parseInt(getProperty(
                MINIMUM_CONFIGURATION_PROXY_SERVER_GLOBAL_CONFIGURATION_VERSION,
                DEFAULT_MINIMUM_CONFIGURATION_PROXY_SERVER_GLOBAL_CONFIGURATION_VERSION));
        // check that it is a valid looking version number
        checkVersionValidity(minVersion, GlobalConfVersion.CURRENT_VERSION);
        // ignore the versions that are no longer supported
        if (minVersion < GlobalConfVersion.MINIMUM_SUPPORTED_VERSION) {
            minVersion = GlobalConfVersion.MINIMUM_SUPPORTED_VERSION;
        }
        return minVersion;
    }

    private static void checkVersionValidity(int min, int current) {
        if (min > current || min < 1) {
            throw new IllegalArgumentException("Illegal minimum global configuration version in system parameters");
        }
    }

    private static String getProperty(String key) {
        return SystemPropertySource.getPropertyResolver().getProperty(key);
    }

    private static String getProperty(String key, String defaultValue) {
        return SystemPropertySource.getPropertyResolver().getProperty(key, defaultValue);
    }

}
