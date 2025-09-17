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

import java.util.Arrays;
import java.util.Optional;

/**
 * Contains system-wide constants for system properties.
 */
@Deprecated(forRemoval = true)
public final class SystemProperties {

    private SystemProperties() {
    }

    /** The prefix for all properties. */
    public static final String PREFIX = "xroad.";
    private static final String SIGNER_PREFIX = PREFIX + "signer.";
    private static final String CENTER_PREFIX = PREFIX + "center.";

    private static final String COMMA_SPLIT = "\\s*,\\s*";

    // Common -----------------------------------------------------------------

    /** Property name of the temporary files path. */
    public static final String TEMP_FILES_PATH =
            PREFIX + "common.temp-files-path";

    /** Property name of the downloaded global configuration directory. */
    public static final String CONFIGURATION_PATH =
            PREFIX + "common.configuration-path";

    /** Current version number of the global configuration **/
    public static final int CURRENT_GLOBAL_CONFIGURATION_VERSION = 5;

    /** Minimum supported version number of the global configuration **/
    public static final int MINIMUM_SUPPORTED_GLOBAL_CONFIGURATION_VERSION = 2;

    /** Default minimum supported global conf version on central server */
    public static final String DEFAULT_MINIMUM_CENTRAL_SERVER_GLOBAL_CONFIGURATION_VERSION = "2";

    /** Default minimum supported global conf version on configuration proxy */
    public static final String DEFAULT_MINIMUM_CONFIGURATION_PROXY_SERVER_GLOBAL_CONFIGURATION_VERSION = "2";

    /** Minimum supported global conf version on central server **/
    private static final String MINIMUM_CENTRAL_SERVER_GLOBAL_CONFIGURATION_VERSION =
            CENTER_PREFIX + "minimum-global-configuration-version";

    /** Minimum supported global conf version on configuration proxy **/
    private static final String MINIMUM_CONFIGURATION_PROXY_SERVER_GLOBAL_CONFIGURATION_VERSION =
            PREFIX + "configuration-proxy.minimum-global-configuration-version";

    // REST API ---------------------------------------------------------------

    /** Property name of the Proxy UI API's ssl configuration file. */
    public static final String PROXY_UI_API_SSL_PROPERTIES =
            PREFIX + "proxy-ui-api.ssl-properties";

    /** Default whitelist for Proxy UI API's key management API (allow only localhost access, ipv4 and ipv6) */
    public static final String DEFAULT_KEY_MANAGEMENT_API_WHITELIST = "127.0.0.0/8, ::1";

    /** Default whitelist for Proxy UI API's regular APIs (allow all) */
    public static final String DEFAULT_REGULAR_API_WHITELIST = "0.0.0.0/0, ::/0";

    /** Property name of the whitelist for Proxy UI API's key management API */
    public static final String PROXY_UI_API_KEY_MANAGEMENT_API_WHITELIST =
            PREFIX + "proxy-ui-api.key-management-api-whitelist";

    /** Property name of the whitelist for Proxy UI API's regular APIs */
    public static final String PROXY_UI_API_REGULAR_API_WHITELIST =
            PREFIX + "proxy-ui-api.regular-api-whitelist";

    // Proxy ------------------------------------------------------------------

    private static final String PROXY_PREFIX = PREFIX + "proxy.";

    /** Property name of controlling SSL support between Proxies. */
    public static final String PROXY_SSL_SUPPORT =
            PROXY_PREFIX + "ssl-enabled";

    /** Property name of the Client Proxy's timeout (milliseconds). */
    public static final String PROXY_CLIENT_TIMEOUT =
            PROXY_PREFIX + "client-timeout";

    /** Property name of the Server Proxy's port number. */
    public static final String PROXY_SERVER_PORT =
            PROXY_PREFIX + "server-port";

    /** Property name of the flag to turn off proxy client SSL verification. */
    public static final String PROXY_VERIFY_CLIENT_CERT = PROXY_PREFIX + "verify-client-cert";

    /** Property name of the flag to turn on proxy client SSL logging. */
    public static final String PROXY_LOG_CLIENT_CERT = PROXY_PREFIX + "log-client-cert";

    /** Property name of the ClientProxy HTTPS connector and ServerProxy HTTP client supported TLS protocols */
    private static final String PROXY_CLIENT_TLS_PROTOCOLS =
            PROXY_PREFIX + "client-tls-protocols";

    /** Property name of the ClientProxy HTTPS connector and ServerProxy HTTP client supported TLS cipher suites */
    private static final String PROXY_CLIENT_TLS_CIPHERS =
            PROXY_PREFIX + "client-tls-ciphers";

    /** Property name of the ClientProxy HTTPS client and ServerProxy HTTPS connector supported TLS cipher suites */
    private static final String PROXY_XROAD_TLS_CIPHERS = PROXY_PREFIX + "xroad-tls-ciphers";

    private static final String SIGNER_ENFORCE_TOKEN_PIN_POLICY = SIGNER_PREFIX + "enforce-token-pin-policy";

    /** Property name of the idle time that connections to the ServerProxy Connector are allowed, in milliseconds */
    private static final String SERVERPROXY_CONNECTOR_MAX_IDLE_TIME =
            PROXY_PREFIX + "server-connector-max-idle-time";

    /** Property name of the server Connector socket SO_LINGER timer, in seconds, value of -1 means off */
    private static final String SERVERPROXY_CONNECTOR_SO_LINGER =
            PROXY_PREFIX + "server-connector-so-linger";

    /** Property name of the server's minimum supported client version */
    private static final String SERVERPROXY_MIN_SUPPORTED_CLIENT_VERSION =
            PROXY_PREFIX + "server-min-supported-client-version";

    /** Property name of the client connector socket SO_LINGER timer, in seconds, value of -1 means off */
    private static final String CLIENTPROXY_CONNECTOR_SO_LINGER =
            PROXY_PREFIX + "client-connector-so-linger";

    /**
     * Property name for he connection maximum idle time that should be set for client proxy apache HttpClient,
     * in milliseconds, value 0 means infinite timeout, -1 means the system default
     */
    private static final String CLIENTPROXY_HTTPCLIENT_TIMEOUT =
            PROXY_PREFIX + "client-httpclient-timeout";

    private static final String CLIENTPROXY_POOL_REUSE_CONNECTIONS =
            PROXY_PREFIX + "pool-enable-connection-reuse";

    private static final String ENFORCE_CLIENT_IS_CERT_VALIDITY_PERIOD_CHECK =
            PROXY_PREFIX + "enforce-client-is-cert-validity-period-check";

    private static final String FALSE = Boolean.FALSE.toString();
    private static final String TRUE = Boolean.TRUE.toString();
    private static final String DEFAULT_CENTER_TRUSTED_ANCHORS_ALLOWED = FALSE;

    private static final String DEFAULT_CENTER_AUTO_APPROVE_AUTH_CERT_REG_REQUESTS = FALSE;

    private static final String DEFAULT_CENTER_AUTO_APPROVE_CLIENT_REG_REQUESTS = FALSE;

    private static final String DEFAULT_CENTER_AUTO_APPROVE_OWNER_CHANGE_REQUESTS = FALSE;

    private static final String DEFAULT_SERVERPROXY_CONNECTOR_MAX_IDLE_TIME = "0";

    private static final String DEFAULT_SERVERPROXY_CONNECTOR_SO_LINGER = "-1";

    private static final String DEFAULT_CLIENTPROXY_CONNECTOR_SO_LINGER = "-1";

    private static final String DEFAULT_CLIENTPROXY_HTTPCLIENT_TIMEOUT = "0";

    private static final String DEFAULT_CLIENTPROXY_TIMEOUT = "30000";

    private static final String DEFAULT_ENFORCE_CLIENT_IS_CERT_VALIDITY_PERIOD_CHECK = FALSE;

    /**
     * The default value of the on/off switch for a group of settings that affect whether or not pooled connections
     * for the ClientProxy can be actually reused
     **/
    private static final String DEFAULT_CLIENTPROXY_POOL_REUSE_CONNECTIONS = FALSE;

    public static final String DEFAULT_SIGNER_ENFORCE_TOKEN_PIN_POLICY = FALSE;

    private static final String OCSP_VERIFIER_CACHE_PERIOD =
            PROXY_PREFIX + "ocsp-verifier-cache-period";

    private static final int OCSP_VERIFIER_CACHE_PERIOD_MAX = 180;

    // Signer -----------------------------------------------------------------

    /** Property name of the SignerClient's timeout. */
    public static final String SIGNER_CLIENT_TIMEOUT = SIGNER_PREFIX + "client-timeout";

    public static final String SIGNER_KEY_LENGTH = SIGNER_PREFIX + "key-length";
    public static final String SIGNER_KEY_NAMED_CURVE = SIGNER_PREFIX + "key-named-curve";

    public static final int MIN_SIGNER_KEY_LENGTH = 2048;
    public static final int DEFAULT_SIGNER_KEY_LENGTH = MIN_SIGNER_KEY_LENGTH;

    public static final String DEFAULT_SIGNER_CLIENT_TIMEOUT = "60000";

    public static final String DEFAULT_SIGNER_KEY_NAMED_CURVE = "secp256r1";

    // Center -----------------------------------------------------------------

    public static final String CENTER_DATABASE_PROPERTIES = CENTER_PREFIX + "database-properties";

    public static final String CENTER_TRUSTED_ANCHORS_ALLOWED = CENTER_PREFIX + "trusted-anchors-allowed";

    public static final String CENTER_INTERNAL_DIRECTORY = CENTER_PREFIX + "internal-directory";

    public static final String CENTER_EXTERNAL_DIRECTORY = CENTER_PREFIX + "external-directory";

    private static final String CENTER_GENERATED_CONF_DIR = CENTER_PREFIX + "generated-conf-dir";

    /** Property name of the path where conf backups are created. */
    public static final String CONF_BACKUP_PATH = CENTER_PREFIX + "conf-backup-path";

    /** Property name of enabling automatic approval of auth cert registration requests. */
    public static final String CENTER_AUTO_APPROVE_AUTH_CERT_REG_REQUESTS = CENTER_PREFIX + "auto-approve-auth-cert-reg-requests";

    /** Property name of enabling automatic approval of client registration requests. */
    public static final String CENTER_AUTO_APPROVE_CLIENT_REG_REQUESTS = CENTER_PREFIX + "auto-approve-client-reg-requests";

    /** Property name of enabling automatic approval of owner change requests. */
    public static final String CENTER_AUTO_APPROVE_OWNER_CHANGE_REQUESTS = CENTER_PREFIX + "auto-approve-owner-change-requests";

    // Misc -------------------------------------------------------------------

    /** Property name of the configuration files path. */
    public static final String CONF_PATH = PREFIX + "conf.path";

    /** Property name of the application log file path. */
    public static final String LOG_PATH = PREFIX + "appLog.path";

    /** Property name of the application log level of ee.ria.xroad. */
    public static final String XROAD_LOG_LEVEL = PREFIX + "appLog.xroad.level";

    // Proxy UI ---------------------------------------------------------------

    /**
     * Property name of the management request sender client keystore path, used to send management requests from Proxy UI.
     */
    private static final String MANAGEMENT_REQUEST_SENDER_CLIENT_KEYSTORE =
            PREFIX + "proxy-ui-api.management-request-sender-client-keystore";

    /**
     * Property name of the management request sender client keystore password, used to send management requests from Proxy UI.
     */
    private static final String MANAGEMENT_REQUEST_SENDER_CLIENT_KEYSTORE_PASSWORD =
            PREFIX + "proxy-ui-api.management-request-sender-client-keystore-password";
    private static final String MANAGEMENT_REQUEST_SENDER_CLIENT_KEYSTORE_PASSWORD_ENV =
            propertyNameToEnvVariable(MANAGEMENT_REQUEST_SENDER_CLIENT_KEYSTORE_PASSWORD);

    /**
     * Property name of the management request sender client truststore path, used to send management requests from Proxy UI.
     */
    private static final String MANAGEMENT_REQUEST_SENDER_CLIENT_TRUSTSTORE =
            PREFIX + "proxy-ui-api.management-request-sender-client-truststore";

    /**
     * Property name of the management request sender client truststore password, used to send management requests from Proxy UI.
     */
    private static final String MANAGEMENT_REQUEST_SENDER_CLIENT_TRUSTSTORE_PASSWORD =
            PREFIX + "proxy-ui-api.management-request-sender-client-truststore-password";
    private static final String MANAGEMENT_REQUEST_SENDER_CLIENT_TRUSTSTORE_PASSWORD_ENV =
            propertyNameToEnvVariable(MANAGEMENT_REQUEST_SENDER_CLIENT_TRUSTSTORE_PASSWORD);

    // Proxy & Central monitor agent ------------------------------------------


    public static final String NET_STATS_FILE =
            PREFIX + "proxy-monitor-agent.net-stats-file";

    // Configuration proxy ------------------------------------------------- //

    /** Property name of the confproxy download script path. */
    public static final String CONFIGURATION_PROXY_DOWNLOAD_SCRIPT =
            PREFIX + "configuration-proxy.download-script";

    /** Property name of the confproxy configuration path. */
    public static final String CONFIGURATION_PROXY_CONF_PATH =
            PREFIX + "configuration-proxy.configuration-path";

    /** Property name of the confproxy public configuration distribution path. */
    public static final String CONFIGURATION_PROXY_GENERATED_CONF_PATH =
            PREFIX + "configuration-proxy.generated-conf-path";

    /** Property name of the confproxy configuration signature digest algorithm. */
    public static final String CONFIGURATION_PROXY_SIGNATURE_DIGEST_ALGORITHM_ID =
            PREFIX + "configuration-proxy.signature-digest-algorithm-id";

    /** Property name of the confproxy configuration file hashing algorithm. */
    public static final String CONFIGURATION_PROXY_HASH_ALGORITHM_URI =
            PREFIX + "configuration-proxy.hash-algorithm-uri";

    /** Property name of the confproxy webserver address. */
    public static final String CONFIGURATION_PROXY_ADDRESS =
            PREFIX + "configuration-proxy.address";

    // Cluster node configuration ------------------------------------------ //

    /**
     * The type of this server node in the cluster. Default is {@link #STANDALONE} which means this server is not
     * part of a cluster.
     */
    public enum NodeType {
        STANDALONE, MASTER, SLAVE;

        /**
         * Parse an enum (ignoring case) from the given String or return the default {@link #STANDALONE}
         * if the argument is not understood.
         *
         * @param name
         * @return
         */
        public static NodeType fromStringIgnoreCaseOrReturnDefault(String name) {
            return Arrays.stream(NodeType.values())
                    .filter(e -> e.name().equalsIgnoreCase(name))
                    .findAny()
                    .orElse(STANDALONE);
        }
    }

    public static final String NODE_TYPE = PREFIX + "node.type";

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
     * @return path to the directory where application logs are stored, '/var/log/xroad/' by default.
     */
    public static String getLogPath() {
        return getProperty(LOG_PATH, DefaultFilepaths.LOG_PATH);
    }

    /**
     * @return log level of the 'ee.ria.xroad.*' packages, 'DEBUG' by default.
     */
    public static String getXROADLogLevel() {
        return getProperty(XROAD_LOG_LEVEL, "DEBUG");
    }

    /**
     * @return path to the proxy ssl configuration file, '/etc/xroad/ssl.properties' by default.
     */
    public static String getSslPropertiesFile() {
        return getProperty(PROXY_UI_API_SSL_PROPERTIES,
                getConfPath() + DefaultFilepaths.PROXY_UI_API_SSL_PROPERTIES);
    }

    /**
     * TO DO: not correct, fix
     *
     * @return whitelist for Proxy UI API's key management API, "127.0.0.0/8, ::1" (localhost) by default
     */
    public static String getKeyManagementApiWhitelist() {
        return getProperty(PROXY_UI_API_KEY_MANAGEMENT_API_WHITELIST,
                DEFAULT_KEY_MANAGEMENT_API_WHITELIST);
    }

    /**
     * @return whitelist for Proxy UI API's regular APIs, "0.0.0.0/0, ::/0" (allow all) by default
     */
    public static String getRegularApiWhitelist() {
        return getProperty(PROXY_UI_API_REGULAR_API_WHITELIST,
                DEFAULT_REGULAR_API_WHITELIST);
    }

    /**
     * @return path to the directory where the downloaded global configuration is placed,
     * '/etc/xroad/globalconf/' by default.
     */
    public static String getConfigurationPath() {
        return getProperty(CONFIGURATION_PATH, getConfPath() + DefaultFilepaths.CONFIGURATION_PATH);
    }

    /**
     * @return path to the management request sender client keystore. Uses PKCS#12 format.
     */
    public static String getManagementRequestSenderClientKeystore() {
        return getProperty(MANAGEMENT_REQUEST_SENDER_CLIENT_KEYSTORE);
    }

    /**
     * @return management request sender client keystore password.
     */
    public static char[] getManagementRequestSenderClientKeystorePassword() {
        return Optional.ofNullable(getProperty(MANAGEMENT_REQUEST_SENDER_CLIENT_KEYSTORE_PASSWORD,
                        System.getenv().get(MANAGEMENT_REQUEST_SENDER_CLIENT_KEYSTORE_PASSWORD_ENV)))
                .map(String::toCharArray)
                .orElse(null);
    }

    /**
     * @return path to the management request sender client truststore. Uses PKCS#12 format.
     */
    public static String getManagementRequestSenderClientTruststore() {
        return getProperty(MANAGEMENT_REQUEST_SENDER_CLIENT_TRUSTSTORE);
    }

    /**
     * @return management request sender client truststore password.
     */
    public static char[] getManagementRequestSenderClientTruststorePassword() {
        return Optional.ofNullable(getProperty(MANAGEMENT_REQUEST_SENDER_CLIENT_TRUSTSTORE_PASSWORD,
                        System.getenv().get(MANAGEMENT_REQUEST_SENDER_CLIENT_TRUSTSTORE_PASSWORD_ENV)))
                .map(String::toCharArray)
                .orElse(null);
    }

    /**
     * @return path to the directory where temporary files are stored, '/var/tmp/xroad/' by default.
     */
    public static String getTempFilesPath() {
        return getProperty(TEMP_FILES_PATH, DefaultFilepaths.TEMP_FILES_PATH);
    }

    /**
     * @return path to the directory where configuration backups are stored, '/var/lib/xroad/backup/' by default.
     */
    public static String getConfBackupPath() {
        return getProperty(CONF_BACKUP_PATH, DefaultFilepaths.CONF_BACKUP_PATH);
    }

    /**
     * @return the client proxy connect timeout in milliseconds, '30000' by default.
     */
    public static int getClientProxyTimeout() {
        return Integer.parseInt(getProperty(PROXY_CLIENT_TIMEOUT, DEFAULT_CLIENTPROXY_TIMEOUT));
    }

    /**
     * @return the HTTP port on which the server proxy listens for messages, '5500' by default.
     */
    public static int getServerProxyPort() {
        return Integer.parseInt(getProperty(PROXY_SERVER_PORT, Integer.toString(PortNumbers.PROXY_PORT)));
    }

    /**
     * @return the signer connection timeout in milliseconds, '60000' by default.
     */
    public static int getSignerClientTimeout() {
        return Integer.parseInt(getProperty(SIGNER_CLIENT_TIMEOUT, DEFAULT_SIGNER_CLIENT_TIMEOUT));
    }

    /**
     * @return authentication and signing key length when RSA is used.
     */
    public static int getSignerKeyLength() {
        return Math.max(MIN_SIGNER_KEY_LENGTH, Integer.getInteger(SIGNER_KEY_LENGTH, DEFAULT_SIGNER_KEY_LENGTH));
    }

    /**
     * @return authentication and signing key named curve when EC is used.
     */
    public static String getSignerKeyNamedCurve() {
        return getProperty(SIGNER_KEY_NAMED_CURVE, DEFAULT_SIGNER_KEY_NAMED_CURVE);
    }

    /**
     * @return whether SSL should be used between client and server proxies, 'true' by default.
     */
    public static boolean isSslEnabled() {
        return Boolean.parseBoolean(getProperty(PROXY_SSL_SUPPORT, TRUE));
    }

    /**
     * @return path to the central server database configuration file, '/etc/xroad/db.properties' by default.
     */
    public static String getCenterDatabasePropertiesFile() {
        return getProperty(CENTER_DATABASE_PROPERTIES,
                getConfPath() + DefaultFilepaths.SERVER_DATABASE_PROPERTIES);
    }

    /**
     * @return whether configuration of trusted anchors is enabled in the central server UI, 'true' by default.
     */
    public static boolean getCenterTrustedAnchorsAllowed() {
        return Boolean.parseBoolean(getProperty(CENTER_TRUSTED_ANCHORS_ALLOWED,
                DEFAULT_CENTER_TRUSTED_ANCHORS_ALLOWED));
    }

    /**
     * @return the name of the signed internal configuration directory
     * that will be distributed to security servers inside the instance, internalconf' by default.
     */
    public static String getCenterInternalDirectory() {
        return getProperty(CENTER_INTERNAL_DIRECTORY, "internalconf");
    }

    /**
     * @return the name of the signed external configuration directory
     * that will be distributed to security servers inside the federation, 'externalconf' by default.
     */
    public static String getCenterExternalDirectory() {
        return getProperty(CENTER_EXTERNAL_DIRECTORY, "externalconf");
    }

    /**
     * @return path to the directory on the central server where both private
     * and shared parameter files are created for distribution, '/var/lib/xroad/public' by default.
     */
    public static String getCenterGeneratedConfDir() {
        return getProperty(CENTER_GENERATED_CONF_DIR, DefaultFilepaths.DISTRIBUTED_GLOBALCONF_PATH);
    }

    /**
     * @return whether automatic approval of auth cert registration requests is enabled, 'false' by default.
     */
    public static boolean getCenterAutoApproveAuthCertRegRequests() {
        return Boolean.parseBoolean(getProperty(CENTER_AUTO_APPROVE_AUTH_CERT_REG_REQUESTS,
                DEFAULT_CENTER_AUTO_APPROVE_AUTH_CERT_REG_REQUESTS));
    }

    /**
     * @return whether automatic approval of client registration requests is enabled, 'false' by default.
     */
    public static boolean getCenterAutoApproveClientRegRequests() {
        return Boolean.parseBoolean(getProperty(CENTER_AUTO_APPROVE_CLIENT_REG_REQUESTS,
                DEFAULT_CENTER_AUTO_APPROVE_CLIENT_REG_REQUESTS));
    }

    /**
     * @return whether automatic approval of owner change requests is enabled, 'false' by default.
     */
    public static boolean getCenterAutoApproveOwnerChangeRequests() {
        return Boolean.parseBoolean(getProperty(CENTER_AUTO_APPROVE_OWNER_CHANGE_REQUESTS,
                DEFAULT_CENTER_AUTO_APPROVE_OWNER_CHANGE_REQUESTS));
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
     * @return path to the file containing network statistics,
     * '/proc/net/dev' by default.
     */
    public static String getNetStatsFile() {
        return getProperty(NET_STATS_FILE, "/proc/net/dev");
    }

    /**
     * This parameter may be set to false for cases where an external
     * component has already verified the certificate before sending the
     * request to client proxy.
     *
     * @return whether the client proxy should verify client's SSL certificate,
     * 'true' by default.
     */
    public static boolean shouldVerifyClientCert() {
        return Boolean.parseBoolean(getProperty(PROXY_VERIFY_CLIENT_CERT, TRUE));
    }

    /**
     * This parameter may be set to true for cases where there is a need to log client's SSL certificate.
     *
     * @return whether the client proxy should log client's SSL certificate,
     * 'false' by default.
     */
    public static boolean shouldLogClientCert() {
        return Boolean.parseBoolean(getProperty(PROXY_LOG_CLIENT_CERT, FALSE));
    }

    /**
     * Get proxy client's TLS protocols.
     *
     * @return protocols.
     */
    public static String[] getProxyClientTLSProtocols() {
        return getProperty(PROXY_CLIENT_TLS_PROTOCOLS, "TLSv1.2").trim().split(COMMA_SPLIT);
    }

    private static final String DEFAULT_CLIENT_SSL_CIPHER_SUITES = "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,"
            + "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256,"
            + "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,"
            + "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384,"
            + "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256,"
            + "TLS_DHE_RSA_WITH_AES_128_CBC_SHA256,"
            + "TLS_DHE_RSA_WITH_AES_256_CBC_SHA256,"
            + "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384";

    /**
     * Get proxy client's accepted TLS cipher suites (between is and ss).
     *
     * @return cipher suites.
     */
    public static String[] getProxyClientTLSCipherSuites() {
        return getProperty(PROXY_CLIENT_TLS_CIPHERS, DEFAULT_CLIENT_SSL_CIPHER_SUITES).trim().split(COMMA_SPLIT);
    }

    private static final String DEFAULT_XROAD_SSL_CIPHER_SUITES = "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384,"
            + "TLS_DHE_RSA_WITH_AES_256_CBC_SHA256,"
            + "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384";

    /**
     * Get X-Road accepted TLS cipher suites (between ss and ss).
     *
     * @return cipher suites.
     */
    public static String[] getXroadTLSCipherSuites() {
        return getProperty(PROXY_XROAD_TLS_CIPHERS, DEFAULT_XROAD_SSL_CIPHER_SUITES).trim().split(COMMA_SPLIT);
    }

    /**
     * Tell whether token PIN policy should be enforced.
     *
     * @return true if PIN policy should be enforced.
     */
    public static boolean shouldEnforceTokenPinPolicy() {
        return Boolean.parseBoolean(getProperty(SIGNER_ENFORCE_TOKEN_PIN_POLICY,
                DEFAULT_SIGNER_ENFORCE_TOKEN_PIN_POLICY));
    }

    /**
     * @return the interval in seconds at which verifier caches results.
     * Max value is 180 seconds and cannot be exceeded in configuration.
     * Default is 60 s.
     */
    public static int getOcspVerifierCachePeriod() {
        int period = Integer.parseInt(getProperty(OCSP_VERIFIER_CACHE_PERIOD, "60"));
        return period < OCSP_VERIFIER_CACHE_PERIOD_MAX ? period : OCSP_VERIFIER_CACHE_PERIOD_MAX;
    }

    /**
     * @return the connection maximum idle time that should be set for server proxy connector
     */
    public static int getServerProxyConnectorMaxIdleTime() {
        return Integer.parseInt(getProperty(SERVERPROXY_CONNECTOR_MAX_IDLE_TIME,
                DEFAULT_SERVERPROXY_CONNECTOR_MAX_IDLE_TIME));
    }

    /**
     * @return the so_linger value in milliseconds that should be set for server proxy connector, -1 (disabled) by
     * default
     */
    @SuppressWarnings("checkstyle:magicnumber")
    public static int getServerProxyConnectorSoLinger() {
        final int linger = Integer.parseInt(getProperty(SERVERPROXY_CONNECTOR_SO_LINGER,
                DEFAULT_SERVERPROXY_CONNECTOR_SO_LINGER));
        if (linger >= 0) return linger * 1000;
        return -1;
    }

    public static String getServerProxyMinSupportedClientVersion() {
        return getProperty(SERVERPROXY_MIN_SUPPORTED_CLIENT_VERSION);
    }

    /**
     * @return the connection maximum idle time that should be set for client proxy apache HttpClient
     */
    public static int getClientProxyHttpClientTimeout() {
        return Integer.parseInt(getProperty(CLIENTPROXY_HTTPCLIENT_TIMEOUT,
                DEFAULT_CLIENTPROXY_HTTPCLIENT_TIMEOUT));
    }

    /**
     * @return the so_linger value in seconds that should be set for client proxy connector, 0 by default
     */
    public static int getClientProxyConnectorSoLinger() {
        return Integer.parseInt(getProperty(CLIENTPROXY_CONNECTOR_SO_LINGER,
                DEFAULT_CLIENTPROXY_CONNECTOR_SO_LINGER));
    }

    public static boolean isEnableClientProxyPooledConnectionReuse() {
        return Boolean.parseBoolean(getProperty(CLIENTPROXY_POOL_REUSE_CONNECTIONS,
                DEFAULT_CLIENTPROXY_POOL_REUSE_CONNECTIONS));
    }

    /**
     * @return the {@link #NODE_TYPE} in a cluster for this Server.
     */
    public static NodeType getServerNodeType() {
        return NodeType.fromStringIgnoreCaseOrReturnDefault(getProperty(NODE_TYPE));
    }

    /**
     * @return minimum central server global configuration version or default
     */
    public static int getMinimumCentralServerGlobalConfigurationVersion() {
        // read the setting
        int minVersion = Integer.parseInt(getProperty(MINIMUM_CENTRAL_SERVER_GLOBAL_CONFIGURATION_VERSION,
                DEFAULT_MINIMUM_CENTRAL_SERVER_GLOBAL_CONFIGURATION_VERSION));
        // check that it is a valid looking version number
        checkVersionValidity(minVersion, CURRENT_GLOBAL_CONFIGURATION_VERSION,
                DEFAULT_MINIMUM_CENTRAL_SERVER_GLOBAL_CONFIGURATION_VERSION);
        // ignore the versions that are no longer supported
        if (minVersion < MINIMUM_SUPPORTED_GLOBAL_CONFIGURATION_VERSION) {
            minVersion = MINIMUM_SUPPORTED_GLOBAL_CONFIGURATION_VERSION;
        }
        return minVersion;
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
        checkVersionValidity(minVersion, CURRENT_GLOBAL_CONFIGURATION_VERSION,
                DEFAULT_MINIMUM_CONFIGURATION_PROXY_SERVER_GLOBAL_CONFIGURATION_VERSION);
        // ignore the versions that are no longer supported
        if (minVersion < MINIMUM_SUPPORTED_GLOBAL_CONFIGURATION_VERSION) {
            minVersion = MINIMUM_SUPPORTED_GLOBAL_CONFIGURATION_VERSION;
        }
        return minVersion;
    }

    private static void checkVersionValidity(int min, int current, String defaultVersion) {
        if (min > current || min < 1) {
            throw new IllegalArgumentException("Illegal minimum global configuration version in system parameters");
        }
    }

    /**
     * @return Whether to throw an exception about expired or not yet valid certificates, 'false' by default..
     */
    public static boolean isClientIsCertValidityPeriodCheckEnforced() {
        return Boolean.parseBoolean(getProperty(ENFORCE_CLIENT_IS_CERT_VALIDITY_PERIOD_CHECK,
                DEFAULT_ENFORCE_CLIENT_IS_CERT_VALIDITY_PERIOD_CHECK));
    }

    private static String propertyNameToEnvVariable(String propName) {
        return propName.toUpperCase().replaceAll("[.-]", "_");
    }

    private static String getProperty(String key) {
        return SystemPropertySource.getPropertyResolver().getProperty(key);
    }

    private static String getProperty(String key, String defaultValue) {
        return SystemPropertySource.getPropertyResolver().getProperty(key, defaultValue);
    }

}
