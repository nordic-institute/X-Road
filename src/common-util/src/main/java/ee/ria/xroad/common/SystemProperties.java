/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

import ee.ria.xroad.common.util.CryptoUtils;


/**
 * Contains system-wide constants for system properties.
 */
public final class SystemProperties {

    private SystemProperties() {
    }

    /** The prefix for all properties. */
    public static final String PREFIX = "xroad.";

    // Common -----------------------------------------------------------------

    /** Property name of the temporary files path. */
    public static final String TEMP_FILES_PATH =
            PREFIX + "common.temp-files-path";

    /** Property name of the downloaded global configuration directory. */
    public static final String CONFIGURATION_PATH =
            PREFIX + "common.configuration-path";

    /** Property name of the default digital signature algorithm. */
    public static final String DEFAULT_SIGNATURE_ALGORITHM =
            PREFIX + "common.default-signature-algorithm";

    /** Current version number of the global configuration **/
    public static final int CURRENT_GLOBAL_CONFIGURATION_VERSION = 2;

    /** Default minimum supported global conf version on central server */
    public static final String DEFAULT_MINIMUM_CENTRAL_SERVER_GLOBAL_CONFIGURATION_VERSION = "2";

    /** Default minimum supported global conf version on configuration proxy */
    public static final String DEFAULT_MINIMUM_CONFIGURATION_PROXY_SERVER_GLOBAL_CONFIGURATION_VERSION = "2";

    /** Minimum supported global conf version on central server **/
    private static final String MINIMUM_CENTRAL_SERVER_GLOBAL_CONFIGURATION_VERSION =
            PREFIX + "center.minimum-global-configuration-version";

    /** Minimum supported global conf version on configuration proxy **/
    private static final String MINIMUM_CONFIGURATION_PROXY_SERVER_GLOBAL_CONFIGURATION_VERSION =
            PREFIX + "configuration-proxy.minimum-global-configuration-version";


    // Proxy ------------------------------------------------------------------

    /** Property name of controlling SSL support between Proxies. */
    public static final String PROXY_SSL_SUPPORT =
            PREFIX + "proxy.ssl-enabled";

    /** Property name of the configuration anchor file. */
    public static final String CONFIGURATION_ANCHOR_FILE =
            PREFIX + "proxy.configuration-anchor-file";

    /** Property name of the Proxy's local configuration file. */
    public static final String DATABASE_PROPERTIES =
            PREFIX + "proxy.database-properties";

    /** Property name of the Proxy's connector host name. */
    public static final String PROXY_CONNECTOR_HOST =
            PREFIX + "proxy.connector-host";

    /** Property name of the Client Proxy's port number. */
    public static final String PROXY_CLIENT_HTTP_PORT =
            PREFIX + "proxy.client-http-port";

    /** Property name of the Client Proxy's port number. */
    public static final String PROXY_CLIENT_HTTPS_PORT =
            PREFIX + "proxy.client-https-port";

    /** Property name of the Client Proxy's timeout (milliseconds). */
    public static final String PROXY_CLIENT_TIMEOUT =
            PREFIX + "proxy.client-timeout";

    /** Property name of the Server Proxy's port number. */
    public static final String PROXY_SERVER_PORT =
            PREFIX + "proxy.server-port";

    /** Property name of the Server Proxy's listen address. */
    public static final String PROXY_SERVER_LISTEN_ADDRESS =
            PREFIX + "proxy.server-listen-address";

    /** Property name of the Server Proxy's listen port number. */
    public static final String PROXY_SERVER_LISTEN_PORT =
            PREFIX + "proxy.server-listen-port";

    /** Property name of the cached OCSP response path. */
    public static final String OCSP_CACHE_PATH =
            PREFIX + "proxy.ocsp-cache-path";

    /** Property name of the Ocsp Responder port. */
    public static final String OCSP_RESPONDER_PORT =
            PREFIX + "proxy.ocsp-responder-port";

    /** Property name of the Ocsp Responder listen address. */
    public static final String OCSP_RESPONDER_LISTEN_ADDRESS =
            PREFIX + "proxy.ocsp-responder-listen-address";

    /** Property name of the Ocsp Responder Client connect timeout. */
    public static final String OCSP_RESPONDER_CLIENT_CONNECT_TIMEOUT =
            PREFIX + "proxy.ocsp-responder-client-connect-timeout";

    /** Property name of the Ocsp Responder Client read timeout. */
    public static final String OCSP_RESPONDER_CLIENT_READ_TIMEOUT =
            PREFIX + "proxy.ocsp-responder-client-read-timeout";

    /** Property name of the flag to turn off proxy client SSL verification. */
    public static final String PROXY_VERIFY_CLIENT_CERT =
            PREFIX + "proxy.verify-client-cert";

    /** Property name of the ClientProxy Jetty server configuration file. */
    public static final String JETTY_CLIENTPROXY_CONFIGURATION_FILE =
            PREFIX + "proxy.jetty-clientproxy-configuration-file";

    /** Property name of the ServerProxy Jetty server configuration file. */
    public static final String JETTY_SERVERPROXY_CONFIGURATION_FILE =
            PREFIX + "proxy.jetty-serverproxy-configuration-file";

    /** Property name of the CertHashBasedOcspResponder Jetty server configuration file. */
    public static final String JETTY_OCSP_RESPONDER_CONFIGURATION_FILE =
            PREFIX + "proxy.jetty-ocsp-responder-configuration-file";


    /** Property name of the ClientProxy HTTPS connector and ServerProxy HTTP client supported TLS protocols */
    private static final String PROXY_CLIENT_TLS_PROTOCOLS =
            PREFIX + "proxy.client-tls-protocols";

    /** Property name of the ClientProxy HTTPS connector and ServerProxy HTTP client supported TLS cipher suites */
    private static final String PROXY_CLIENT_TLS_CIPHERS =
            PREFIX + "proxy.client-tls-ciphers";

    private static final String PROXY_ENFORCE_TOKEN_PIN_POLICY =
            PREFIX + "proxy.enforce-token-pin-policy";

    public static final String SERVER_CONF_CACHE_PERIOD =
            PREFIX + "proxy.server-conf-cache-period";

    /** Property name of the idle time that connections to the ServerProxy Connector are allowed, in milliseconds */
    private static final String SERVERPROXY_CONNECTOR_MAX_IDLE_TIME =
            PREFIX + "proxy.server-connector-max-idle-time";

    /** Property name of the server Connector socket SO_LINGER timer, in seconds, value of -1 means off */
    private static final String SERVERPROXY_CONNECTOR_SO_LINGER =
            PREFIX + "proxy.server-connector-so-linger";

    /** Property name of the idle time that ClientProxy connections are allowed, in milliseconds */
    private static final String CLIENTPROXY_CONNECTOR_MAX_IDLE_TIME =
            PREFIX + "proxy.client-connector-max-idle-time";

    /** Property name of the client connector socket SO_LINGER timer, in seconds, value of -1 means off */
    private static final String CLIENTPROXY_CONNECTOR_SO_LINGER =
            PREFIX + "proxy.client-connector-so-linger";

    /** Property name for he connection maximum idle time that should be set for client proxy apache HttpClient,
     *  in seconds, value of -1 means off */
    private static final String CLIENTPROXY_HTTPCLIENT_TIMEOUT =
            PREFIX + "proxy.client-httpclient-timeout";

    /** Property name for the so_linger value that should be set for client proxy apache HttpClient */
    private static final String CLIENTPROXY_HTTPCLIENT_SO_LINGER =
            PREFIX + "proxy.client-httpclient-so-linger";

    private static final String CLIENTPROXY_POOL_IDLE_MONITOR_INTERVAL =
            PREFIX + "proxy.client-idle-connection-monitor-interval";

    private static final String CLIENTPROXY_POOL_IDLE_MONITOR_IDLE_TIME =
            PREFIX + "proxy.client-idle-connection-monitor-timeout";

    private static final String CLIENTPROXY_POOL_USE_IDLE_CONNECTION_MONITOR =
            PREFIX + "proxy.client-use-idle-connection-monitor";

    private static final String CLIENTPROXY_POOL_TOTAL_MAX_CONNECTIONS =
            PREFIX + "proxy.pool-total-max-connections";

    private static final String CLIENTPROXY_POOL_DEFAULT_MAX_CONN_PER_ROUTE =
            PREFIX + "proxy.pool-total-default-max-connections-per-route";

    private static final String CLIENTPROXY_USE_FASTEST_CONNECTING_SSL_SOCKET_AUTOCLOSE =
            PREFIX + "proxy.client-use-fastest-connecting-ssl-socket-autoclose";

    private static final String CLIENTPROXY_FASTEST_CONNECTING_SSL_USE_URI_CACHE =
            PREFIX + "proxy.client-fastest-connecting-ssl-use-uri-cache";

    private static final String CLIENTPROXY_POOL_VALIDATE_CONNECTIONS_AFTER_INACTIVITY_OF_MS =
            PREFIX + "proxy.pool-validate-connections-after-inactivity-of-millis";

    private static final String CLIENTPROXY_POOL_REUSE_CONNECTIONS =
            PREFIX + "proxy.pool-enable-connection-reuse";


    private static final String DEFAULT_SERVERPROXY_CONNECTOR_MAX_IDLE_TIME = "0";

    private static final String DEFAULT_SERVERPROXY_CONNECTOR_SO_LINGER = "0";

    private static final String DEFAULT_CLIENTPROXY_CONNECTOR_MAX_IDLE_TIME = "0";

    private static final String DEFAULT_CLIENTPROXY_CONNECTOR_SO_LINGER = "0";

    private static final String DEFAULT_CLIENTPROXY_HTTPCLIENT_TIMEOUT = "0";

    private static final String DEFAULT_CLIENTPROXY_HTTPCLIENT_SO_LINGER = "-1";

    private static final String DEFAULT_CLIENTPROXY_POOL_IDLE_MONITOR_INTERVAL = "30000";

    private static final String DEFAULT_CLIENTPROXY_POOL_IDLE_MONITOR_IDLE_TIME = "60000";

    private static final String DEFAULT_CLIENTPROXY_POOL_USE_IDLE_CONNECTION_MONITOR = "false";

    private static final String DEFAULT_CLIENTPROXY_POOL_TOTAL_MAX_CONNECTIONS = "10000";

    private static final String DEFAULT_CLIENTPROXY_POOL_DEFAULT_MAX_CONN_PER_ROUTE = "2500";

    private static final String DEFAULT_CLIENTPROXY_USE_FASTEST_CONNECTING_SSL_SOCKET_AUTOCLOSE = "false";

    private static final String DEFAULT_CLIENTPROXY_FASTEST_CONNECTING_SSL_USE_URI_CACHE = "true";

    private static final String DEFAULT_CLIENTPROXY_POOL_VALIDATE_CONNECTIONS_AFTER_INACTIVITY_OF_MS = "-1";

    /** The default value of the on/off switch for a group of settings that affect whether or not pooled connections
     * for the ClientProxy can be actually reused **/
    private static final String DEFAULT_CLIENTPROXY_POOL_REUSE_CONNECTIONS = "false";


    private static final String OCSP_VERIFIER_CACHE_PERIOD =
            PREFIX + "proxy.ocsp-verifier-cache-period";

    private static final int OCSP_VERIFIER_CACHE_PERIOD_MAX = 180;


    // Signer -----------------------------------------------------------------

    /** Property name of the key configuration file. */
    public static final String KEY_CONFIGURATION_FILE =
            PREFIX + "signer.key-configuration-file";

    /** Property name of the device configuration file. */
    public static final String DEVICE_CONFIGURATION_FILE =
            PREFIX + "signer.device-configuration-file";

    /** Property name of the Signer's port number. */
    public static final String SIGNER_PORT =
            PREFIX + "signer.port";

    /** Property name of the Signer's admin port number. */
    public static final String SIGNER_ADMIN_PORT =
            PREFIX + "signer.admin-port";

    /** Property name of the SignerClient's timeout. */
    public static final String SIGNER_CLIENT_TIMEOUT =
            PREFIX + "signer.client-timeout";

    public static final String SIGNER_MODULE_INSTANCE_PROVIDER =
            PREFIX + "signer.module-instance-provider";

    public static final String SIGNER_KEY_LENGTH =
            PREFIX + "signer.key-length";

    public static final int MIN_SIGNER_KEY_LENGTH = 2048;
    public static final int DEFAULT_SIGNER_KEY_LENGTH = MIN_SIGNER_KEY_LENGTH;

    public static final String SIGNER_CSR_SIGNATURE_ALGORITHM =
            PREFIX + "signer.csr-signature-algorithm";

    public static final String OCSP_RESPONSE_RETRIEVAL_ACTIVE =
            PREFIX + "signer.ocsp-response-retrieval-active";

    // AntiDos ----------------------------------------------------------------

    /** Property name of the AntiDos on/off switch */
    public static final String ANTIDOS_ENABLED =
            PREFIX + "anti-dos.enabled";

    /** Property name of the maximum number of allowed parallel connections */
    public static final String ANTIDOS_MAX_PARALLEL_CONNECTIONS =
            PREFIX + "anti-dos.max-parallel-connections";

    /** Property name of the maximum allowed cpu load value */
    public static final String ANTIDOS_MAX_CPU_LOAD =
            PREFIX + "anti-dos.max-cpu-load";

    /** Property name of the minimum number of free file handles*/
    public static final String ANTIDOS_MIN_FREE_FILEHANDLES =
            PREFIX + "anti-dos.min-free-file-handles";

    /** Property name of the maximum allowed JVM heap usage value */
    public static final String ANTIDOS_MAX_HEAP_USAGE =
            PREFIX + "anti-dos.max-heap-usage";

    // Configuration client ---------------------------------------------------

    public static final String CONFIGURATION_CLIENT_PORT =
            PREFIX + "configuration-client.port";

    public static final String CONFIGURATION_CLIENT_ADMIN_PORT =
            PREFIX + "configuration-client.admin-port";


    public static final String CONFIGURATION_CLIENT_UPDATE_INTERVAL_SECONDS =
            PREFIX + "configuration-client.update-interval";

    // Center -----------------------------------------------------------------

    public static final String CENTER_DATABASE_PROPERTIES =
            PREFIX + "center.database-properties";

    public static final String CENTER_TRUSTED_ANCHORS_ALLOWED =
            PREFIX + "center.trusted-anchors-allowed";

    public static final String CENTER_INTERNAL_DIRECTORY =
            PREFIX + "center.internal-directory";

    public static final String CENTER_EXTERNAL_DIRECTORY =
            PREFIX + "center.external-directory";

    private static final String CENTER_GENERATED_CONF_DIR =
            PREFIX + "center.generated-conf-dir";

    /** Property name of the path where conf backups are created. */
    public static final String CONF_BACKUP_PATH =
            PREFIX + "center.conf-backup-path";

    /** Property name of the path where original V5 data files are located. */
    public static final String V5_IMPORT_PATH =
            PREFIX + "center.v5-import-path";

    // Misc -------------------------------------------------------------------

    /** Property name of the configuration files path. */
    public static final String CONF_PATH =
            PREFIX + "conf.path";

    /** Property name of the log folder for Log Reader. */
    public static final String LOG_READER_PATH =
            PREFIX + "logReader.path";

    /** Property name of the application log file path. */
    public static final String LOG_PATH =
            PREFIX + "appLog.path";

    /** Property name of the application log level of ee.ria.xroad.* */
    public static final String XROAD_LOG_LEVEL =
            PREFIX + "appLog.xroad.level";

    // Proxy UI ---------------------------------------------------------------

    public static final String CLIENTS_IMPORTER_COMMAND =
            PREFIX + "proxy-ui.clients-importer-command";

    public static final String TLS_KEY_IMPORTER_COMMAND =
            PREFIX + "proxy-ui.tls-key-importer-command";

    public static final String TLS_KEY_EXPORTER_COMMAND =
            PREFIX + "proxy-ui.tls-key-exporter-command";

    /** Property name of the WSDL validator command. */
    public static final String WSDL_VALIDATOR_COMMAND =
            PREFIX + "proxy-ui.wsdl-validator-command";

    // Proxy & Central monitor agent ------------------------------------------

    /** Property name of the proxy monitor agent configuration file. */
    public static final String MONITOR_AGENT_CONFIGURATION_FILE =
            PREFIX + "monitor-agent.monitoring-conf-file";

    /** Property of the monitor agent admin port. **/
    public static final String MONITOR_AGENT_ADMIN_PORT =
            PREFIX + "monitor-agent.admin-port";

    /** Property of the proxy monitor agent sending interval in seconds. */
    public static final String PROXY_MONITOR_AGENT_SENDING_INTERVAL =
            PREFIX + "proxy-monitor-agent.sending-interval";

    /** Property name of the proxy monitor info collection interval. */
    public static final String PROXY_PARAMS_COLLECTING_INTERVAL =
            PREFIX + "proxy-monitor-agent.params-collecting-interval";

    public static final String NET_STATS_FILE =
            PREFIX + "proxy-monitor-agent.net-stats-file";

    public static final String MONITORING_AGENT_URI =
            PREFIX + "monitoringagent.uri";

    /** Property of the central monitor agent HTTPS port. **/
    public static final String CENTRAL_MONITOR_AGENT_HTTPS_PORT =
            PREFIX + "central-monitor-agent.https-port";

    // Zabbix configurator agent ----------------------------------------------

    /** Property name of the Zabbix configurator client's
     * timeout (milliseconds). */
    public static final String ZABBIX_CONFIGURATOR_CLIENT_TIMEOUT =
            PREFIX + "monitoring.zabbix-configurator-client-timeout";

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

    /** Property name of the confproxy configuration signature algorithm. */
    public static final String CONFIGURATION_PROXY_SIGNATURE_ALGORITHM_ID =
            PREFIX + "configuration-proxy.signature-algorithm-id";

    /** Property name of the confproxy configuration file hashing algorithm. */
    public static final String CONFIGURATION_PROXY_HASH_ALGORITHM_URI =
            PREFIX + "configuration-proxy.hash-algorithm-uri";

    /** Property name of the confproxy webserver address. */
    public static final String CONFIGURATION_PROXY_ADDRESS =
            PREFIX + "configuration-proxy.address";

    // Environmental Monitoring  -------------------------- //

    /** Property name of environmental monitor port. */
    public static final String ENV_MONITOR_PORT =
            PREFIX + "env-monitor.port";

    /** Property name of system metrics sensor interval. */
    public static final String ENV_MONITOR_SYSTEM_METRICS_SENSOR_INTERVAL =
            PREFIX + "env-monitor.system-metrics-sensor-interval";

    /** Property name of disk space sensor interval. */
    public static final String ENV_MONITOR_DISK_SPACE_SENSOR_INTERVAL =
            PREFIX + "env-monitor.disk-space-sensor-interval";

    /** Property name of system metrics sensor interval. */
    public static final String ENV_MONITOR_EXEC_LISTING_SENSOR_INTERVAL =
            PREFIX + "env-monitor.exec-listing-sensor-interval";

    // Configuration file names and section names -------------------------- //

    public static final String CONF_FILE_COMMON =
            getConfPath() + "conf.d/common.ini";

    public static final String CONF_FILE_PROXY =
            getConfPath() + "conf.d/proxy.ini";

    public static final String CONF_FILE_PROXY_UI =
            getConfPath() + "conf.d/proxy-ui.ini";

    public static final String CONF_FILE_SIGNER =
            getConfPath() + "conf.d/signer.ini";

    public static final String CONF_FILE_CENTER =
            getConfPath() + "conf.d/center.ini";

    public static final String CONF_FILE_CONFPROXY =
            getConfPath() + "conf.d/confproxy.ini";

    public static final String CONF_FILE_OP_MONITOR =
            getConfPath() + "conf.d/op-monitor.ini";

    public static final String CONF_FILE_ENV_MONITOR =
            getConfPath() + "conf.d/monitor.ini";

    public static final String CONF_FILE_USER_LOCAL =
            getConfPath() + "conf.d/local.ini";

    public static final String CONF_FILE_ADDON_PATH =
            getConfPath() + "conf.d/addons/";


    // --------------------------------------------------------------------- //

    // For testing purpose only!
    public static final boolean USE_DUMMY_SIGNATURE = false;

    // For testing purpose only!
    public static final boolean IGNORE_SIGNATURE_VERIFICATION = false;

    // --------------------------------------------------------------------- //

    private static final String DEFAULT_CONNECTOR_HOST = "0.0.0.0";

    /**
     * @return path to the directory where configuration files are located,
     * '/etc/xroad/' by default.
     */
    public static String getConfPath() {
        return System.getProperty(CONF_PATH, DefaultFilepaths.CONF_PATH);
    }

    /**
     * @return path to the directory where application logs are stored,
     * '/var/log/xroad/' by default.
     */
    public static String getLogPath() {
        return System.getProperty(LOG_PATH, DefaultFilepaths.LOG_PATH);
    }

    /**
     * @return log level of the 'ee.ria.xroad.*' packages, 'DEBUG' by default.
     */
    public static String getXROADLogLevel() {
        return System.getProperty(XROAD_LOG_LEVEL, "DEBUG");
    }

    /**
     * @return path to the proxy database configuration file,
     * '/etc/xroad/db.properties' by default.
     */
    public static String getDatabasePropertiesFile() {
        return System.getProperty(DATABASE_PROPERTIES,
                getConfPath() + DefaultFilepaths.SERVER_DATABASE_PROPERTIES);
    }

    /**
     * @return path to the configuration anchor file,
     * '/etc/xroad/configuration-anchor.xml' by default.
     */
    public static String getConfigurationAnchorFile() {
        return System.getProperty(CONFIGURATION_ANCHOR_FILE,
                getConfPath() + DefaultFilepaths.CONFIGURATION_ANCHOR_FILE);
    }

    /**
     * @return path to the directory where the downloaded global configuration
     * is placed, '/etc/xroad/globalconf/' by default.
     */
    public static String getConfigurationPath() {
        return System.getProperty(CONFIGURATION_PATH,
                getConfPath() + DefaultFilepaths.CONFIGURATION_PATH);
    }

    /**
     * @return path to the signing key configuration file,
     * '/etc/xroad/signer/keyconf.xml' by default.
     */
    public static String getKeyConfFile() {
        return System.getProperty(KEY_CONFIGURATION_FILE,
                getConfPath() + DefaultFilepaths.KEY_CONFIGURATION_FILE);
    }

    /**
     * @return path to the signing key device configuration file,
     * '/etc/xroad/signer/devices.ini' by default.
     */
    public static String getDeviceConfFile() {
        return System.getProperty(DEVICE_CONFIGURATION_FILE,
                getConfPath() + DefaultFilepaths.DEVICE_CONFIGURATION_FILE);
    }

    /**
     * @return path to the client proxy jetty server configuration file,
     * '/etc/xroad/jetty/clientproxy.xml' by default.
     */
    public static String getJettyClientProxyConfFile() {
        return System.getProperty(JETTY_CLIENTPROXY_CONFIGURATION_FILE,
                getConfPath() + DefaultFilepaths.JETTY_CLIENTPROXY_CONFIGURATION_FILE);
    }

    /**
     * @return path to the server proxy jetty server configuration file,
     * '/etc/xroad/jetty/serverproxy.xml' by default.
     */
    public static String getJettyServerProxyConfFile() {
        return System.getProperty(JETTY_SERVERPROXY_CONFIGURATION_FILE,
                getConfPath() + DefaultFilepaths.JETTY_SERVERPROXY_CONFIGURATION_FILE);
    }

    /**
     * @return path to the cert hash based OCSP responder jetty server configuration file,
     * '/etc/xroad/jetty/ocsp-responder.xml' by default.
     */
    public static String getJettyOcspResponderConfFile() {
        return System.getProperty(JETTY_OCSP_RESPONDER_CONFIGURATION_FILE,
                getConfPath() + DefaultFilepaths.JETTY_OCSP_RESPONDER_CONFIGURATION_FILE);
    }

    /**
     * @return WSDL validator command string. Defaults to null.
     */
    public static String getWsdlValidatorCommand() {
        return System.getProperty(WSDL_VALIDATOR_COMMAND, null);
    }

    /**
     * @return path to the directory where query logs are archived,
     * '/var/lib/xroad/' by default.
     */
    public static String getLogReaderPath() {
        return System.getProperty(LOG_READER_PATH,
                DefaultFilepaths.SECURE_LOG_PATH);
    }

    /**
     * @return path to the directory where temporary files are stored,
     * '/var/tmp/xroad/' by default.
     */
    public static String getTempFilesPath() {
        return System.getProperty(TEMP_FILES_PATH,
                DefaultFilepaths.TEMP_FILES_PATH);
    }

    /**
     * @return path to the directory where OCSP responses are stored,
     * '/var/cache/xroad/' by default.
     */
    public static String getOcspCachePath() {
        return System.getProperty(OCSP_CACHE_PATH,
                DefaultFilepaths.OCSP_CACHE_PATH);
    }

    /**
     * @return path to the directory where configuration backups are stored,
     * '/var/lib/xroad/backup/' by default.
     */
    public static String getConfBackupPath() {
        return System.getProperty(CONF_BACKUP_PATH,
                DefaultFilepaths.CONF_BACKUP_PATH);
    }

    /**
     * @return path to the directory where imported V5 data files are stored,
     * '/var/lib/xroad/import' by default.
     */
    public static String getV5ImportPath() {
        return System.getProperty(V5_IMPORT_PATH,
                DefaultFilepaths.V5_IMPORT_PATH);
    }

    /**
     * @return the host address on which the client proxy is listening,
     * '0.0.0.0' by default.
     */
    public static String getConnectorHost() {
        return System.getProperty(PROXY_CONNECTOR_HOST,
                DEFAULT_CONNECTOR_HOST);
    }

    /**
     * @return the HTTP port on which the client proxy is listening,
     * '8080' by default.
     */
    public static int getClientProxyHttpPort() {
        return Integer.parseInt(System.getProperty(PROXY_CLIENT_HTTP_PORT,
                Integer.toString(PortNumbers.CLIENT_HTTP_PORT)));
    }

    /**
     * @return the HTTPS port on which the client proxy is listening,
     * '8443' by default.
     */
    public static int getClientProxyHttpsPort() {
        return Integer.parseInt(System.getProperty(PROXY_CLIENT_HTTPS_PORT,
                Integer.toString(PortNumbers.CLIENT_HTTPS_PORT)));
    }

    /**
     * @return the client proxy connection timeout in milliseconds,
     * '300000' by default.
     */
    public static int getClientProxyTimeout() {
        return Integer.parseInt(System.getProperty(PROXY_CLIENT_TIMEOUT,
                "300000")); // default timeout in milliseconds
    }

    /**
     * @return the HTTP port on which the server proxy listens for messages,
     * '5500' by default.
     */
    public static int getServerProxyPort() {
        return Integer.parseInt(System.getProperty(PROXY_SERVER_PORT,
                Integer.toString(PortNumbers.PROXY_PORT)));
    }

    /**
     * @return the HTTP port on which the server proxy listens for messages,
     * '5500' by default.
     */
    public static int getServerProxyListenPort() {
        return Integer.parseInt(System.getProperty(PROXY_SERVER_LISTEN_PORT,
                Integer.toString(PortNumbers.PROXY_PORT)));
    }

    /**
     * @return the host address on which the server proxy listens for messages,
     * '0.0.0.0' by default.
     */
    public static String getServerProxyListenAddress() {
        return System.getProperty(PROXY_SERVER_LISTEN_ADDRESS,
                DEFAULT_CONNECTOR_HOST);
    }

    /**
     * @return the HTTP port on which the signer listens for signing requests,
     * '5558' by default.
     */
    public static int getSignerPort() {
        return Integer.parseInt(System.getProperty(SIGNER_PORT,
                Integer.toString(PortNumbers.SIGNER_PORT)));
    }

    /**
     * @return the port on which the signer admin listens for requests
     */
    public static int getSignerAdminPort() {
        return Integer.parseInt(System.getProperty(SIGNER_ADMIN_PORT,
                Integer.toString(PortNumbers.SIGNER_ADMIN_PORT)));
    }

    /**
     * @return the signer connection timeout in milliseconds,
     * '60000' by default.
     */
    public static int getSignerClientTimeout() {
        return Integer.parseInt(System.getProperty(SIGNER_CLIENT_TIMEOUT,
                "60000")); // default timeout in milliseconds
    }

    /**
     * @return authentication and signing key length.
     */
    public static int getSignerKeyLength() {
        return Math.max(MIN_SIGNER_KEY_LENGTH,
                Integer.getInteger(SIGNER_KEY_LENGTH, DEFAULT_SIGNER_KEY_LENGTH));
    }

    /**
     * Get CSR signature algorithm
     *
     * @return algorithm
     */
    public static String getSignerCsrSignatureAlgorithm() {
        return System.getProperty(SIGNER_CSR_SIGNATURE_ALGORITHM, getDefaultSignatureAlgorithm());
    }

    /**
     * @return whether OCSP-response retrieval loop should be activated
     */
    public static boolean isOcspResponseRetrievalActive() {
        return "true".equalsIgnoreCase(
                System.getProperty(OCSP_RESPONSE_RETRIEVAL_ACTIVE, "true"));
    }

    /**
     * @return the HTTP port on which the configuration client is listening,
     * '5665' by default.
     */
    public static int getConfigurationClientPort() {
        return Integer.parseInt(
                System.getProperty(CONFIGURATION_CLIENT_PORT,
                        Integer.toString(PortNumbers.CONFIGURATION_CLIENT_PORT)));
    }

    /**
     * @return the HTTP port on which the configuration client is listening,
     * '5675' by default.
     */
    public static int getConfigurationClientAdminPort() {
        return Integer.parseInt(
                System.getProperty(CONFIGURATION_CLIENT_ADMIN_PORT,
                        Integer.toString(PortNumbers.CONFIGURATION_CLIENT_ADMIN_PORT)));
    }


    /**
     * @return the update interval in seconds at which configuration client
     * downloads the global configuration, '60' by default.
     */
    public static int getConfigurationClientUpdateIntervalSeconds() {
        return Integer.parseInt(
                System.getProperty(CONFIGURATION_CLIENT_UPDATE_INTERVAL_SECONDS,
                        "60")); // default time in seconds
    }

    /**
     * @return the HTTP port on which the server proxy OCSP responder is listening,
     * '5577' by default.
     */
    public static int getOcspResponderPort() {
        return Integer.parseInt(System.getProperty(OCSP_RESPONDER_PORT,
                Integer.toString(PortNumbers.PROXY_OCSP_PORT)));
    }

    /**
     * @return the host address on which the server proxy OCSP responder is listening,
     * '0.0.0.0' by default.
     */
    public static String getOcspResponderListenAddress() {
        return System.getProperty(OCSP_RESPONDER_LISTEN_ADDRESS,
                DEFAULT_CONNECTOR_HOST);
    }

    /**
     * @return the OCSP Responder Client connect timeout in milliseconds,
     * '20000' by default.
     */
    public static int getOcspResponderClientConnectTimeout() {
        return Integer.parseInt(System.getProperty(OCSP_RESPONDER_CLIENT_CONNECT_TIMEOUT, "20000"));
    }

    /**
     * @return the OCSP Responder Client read timeout in milliseconds,
     * '30000' by default.
     */
    public static int getOcspResponderClientReadTimeout() {
        return Integer.parseInt(System.getProperty(OCSP_RESPONDER_CLIENT_READ_TIMEOUT, "30000"));
    }

    /**
     * @return the shell command used when importing V5 clients.
     */
    public static String getClientsImporterCommand() {
        return System.getProperty(CLIENTS_IMPORTER_COMMAND);
    }

    /**
     * @return the shell command used when importing V5 internal TLS key to V6.
     */
    public static String getInternalTlsKeyImporterCommand() {
        return System.getProperty(TLS_KEY_IMPORTER_COMMAND);
    }

    /**
     * @return the shell command used when exporting V6 internal TLS key to V5.
     */
    public static String getInternalTlsKeyExporterCommand() {
        return System.getProperty(TLS_KEY_EXPORTER_COMMAND);
    }

    /**
     * @return whether SSL should be used between client and server proxies,
     * 'true' by default.
     */
    public static boolean isSslEnabled() {
        return "true".equalsIgnoreCase(
                System.getProperty(PROXY_SSL_SUPPORT, "true"));
    }

    /**
     * @return path to the central server database configuration file,
     * '/etc/xroad/db.properties' by default.
     */
    public static String getCenterDatabasePropertiesFile() {
        return System.getProperty(CENTER_DATABASE_PROPERTIES,
                getConfPath() + DefaultFilepaths.SERVER_DATABASE_PROPERTIES);
    }

    /**
     * @return whether configuration of trusted anchors is enabled in the
     * central server UI, 'true' by default.
     */
    public static boolean getCenterTrustedAnchorsAllowed() {
        return "true".equalsIgnoreCase(
                System.getProperty(CENTER_TRUSTED_ANCHORS_ALLOWED, "false"));
    }

    /**
     * @return the name of the signed internal configuration directory
     * that will be distributed to security servers inside the instance,
     * 'internalconf' by default.
     */
    public static String getCenterInternalDirectory() {
        return System.getProperty(CENTER_INTERNAL_DIRECTORY, "internalconf");
    }

    /**
     * @return the name of the signed external configuration directory
     * that will be distributed to security servers inside the federation,
     * 'externalconf' by default.
     */
    public static String getCenterExternalDirectory() {
        return System.getProperty(CENTER_EXTERNAL_DIRECTORY, "externalconf");
    }

    /**
     * @return path to the directory on the central server where both private
     * and shared parameter files are created for distribution,
     * '/var/lib/xroad/public' by default.
     */
    public static String getCenterGeneratedConfDir() {
        return System.getProperty(CENTER_GENERATED_CONF_DIR,
                DefaultFilepaths.DISTRIBUTED_GLOBALCONF_PATH);
    }

    /**
     * @return the HTTP port on which the monitor agent listens for administrative
     * commands, '5588' by default.
     */
    public static int getMonitorAgentAdminPort() {
        return Integer.parseInt(System.getProperty(
                MONITOR_AGENT_ADMIN_PORT,
                Integer.toString(PortNumbers.MONITOR_AGENT_ADMIN_PORT)));
    }

    /**
     * @return the interval in seconds at which monitor agent sends collected
     * monitoring data, '180' by default.
     */
    public static int getProxyMonitorAgentSendingInterval() {
        return Integer.parseInt(System.getProperty(
                PROXY_MONITOR_AGENT_SENDING_INTERVAL, "180"));
    }

    /**
     * @return path to the monitor agent configuration file,
     * '/etc/xroad/monitor-agent.ini' by default.
     */
    public static String getMonitorAgentConfFile() {
        return System.getProperty(MONITOR_AGENT_CONFIGURATION_FILE,
                getConfPath()
                        + DefaultFilepaths.MONITOR_AGENT_CONFIGURATION_FILE);
    }

    /**
     * @return the Zabbix configurator client connection timeout in milliseconds,
     * '300000' by default.
     */
    public static int getZabbixConfiguratorClientTimeout() {
        return Integer.parseInt(System.getProperty(
                ZABBIX_CONFIGURATOR_CLIENT_TIMEOUT,
                "300000")); // Default timeout in milliseconds.
    }

    /**
     * @return path to the directory containing configuration proxy configuration
     * files, '/etc/xroad/confproxy' by default.
     */
    public static String getConfigurationProxyConfPath() {
        return System.getProperty(CONFIGURATION_PROXY_CONF_PATH,
                getConfPath() + "confproxy/");
    }

    /**
     * @return path to the global configuration download script,
     * '/usr/share/xroad/scripts/download_instance_configuration.sh' by default.
     */
    public static String getConfigurationProxyDownloadScript() {
        return System.getProperty(CONFIGURATION_PROXY_DOWNLOAD_SCRIPT,
                "/usr/share/xroad/scripts/download_instance_configuration.sh");
    }

    /**
     * @return path to the directory on the configuration proxy where global
     * configuration files are generated for distribution,
     * '/var/lib/xroad/public' by default.
     */
    public static String getConfigurationProxyGeneratedConfPath() {
        return System.getProperty(CONFIGURATION_PROXY_GENERATED_CONF_PATH,
                DefaultFilepaths.DISTRIBUTED_GLOBALCONF_PATH);
    }

    /**
     * @return ID of the signing algorithm the configuration proxy uses when
     * signing generated global configuration directories,
     * 'SHA512withRSA' by default.
     */
    public static String getConfigurationProxySignatureAlgorithmId() {
        return System.getProperty(CONFIGURATION_PROXY_SIGNATURE_ALGORITHM_ID,
                CryptoUtils.SHA512WITHRSA_ID);
    }

    /**
     * @return URI of the hashing algorithm the configuration proxy uses when
     * calculating hashes of files in the global configuratoin directory,
     * 'http://www.w3.org/2001/04/xmlenc#sha512' by default.
     */
    public static String getConfigurationProxyHashAlgorithmUri() {
        return System.getProperty(CONFIGURATION_PROXY_HASH_ALGORITHM_URI,
                CryptoUtils.DEFAULT_DIGEST_ALGORITHM_URI);
    }

    /**
     * @return the host address on which the configuration proxy listens for
     * global configuration download requests, '0.0.0.0' by default.
     */
    public static String getConfigurationProxyAddress() {
        return System.getProperty(CONFIGURATION_PROXY_ADDRESS,
                DEFAULT_CONNECTOR_HOST);
    }

    /**
     * @return the interval in seconds at which proxy monitor agent collects
     * monitoring data, '60' by default.
     */
    public static int getProxyParamsCollectingInterval() {
        return Integer.parseInt(System.getProperty(
                PROXY_PARAMS_COLLECTING_INTERVAL, "60"));
    }

    /**
     * @return environmental monitoring port,
     * '2552' by default.
     */
    public static int getEnvMonitorPort() {
        return Integer.parseInt(System.getProperty(
                ENV_MONITOR_PORT,
                "2552")); // Default port
    }

    /**
     * @return system metrics sensor interval in seconds,
     * '5' by default.
     */
    public static int getEnvMonitorSystemMetricsSensorInterval() {
        return Integer.parseInt(System.getProperty(
                ENV_MONITOR_SYSTEM_METRICS_SENSOR_INTERVAL,
                "5")); // Default interval in seconds.
    }

    /**
     * @return disk space sensor interval in seconds,
     * '60' by default.
     */
    public static int getEnvMonitorDiskSpaceSensorInterval() {
        return Integer.parseInt(System.getProperty(
                ENV_MONITOR_DISK_SPACE_SENSOR_INTERVAL,
                "60")); // Default interval in seconds.
    }


    /**
     * @return exec listing sensor interval in seconds,
     * '60' by default.
     */
    public static int getEnvMonitorExecListingSensorInterval() {
        return Integer.parseInt(System.getProperty(
                ENV_MONITOR_EXEC_LISTING_SENSOR_INTERVAL,
                "60")); // Default interval in seconds.
    }

    /**
     * @return path to the file containing network statistics,
     * '/proc/net/dev' by default.
     */
    public static String getNetStatsFile() {
        return System.getProperty(NET_STATS_FILE, "/proc/net/dev");
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
        return "true".equalsIgnoreCase(
                System.getProperty(PROXY_VERIFY_CLIENT_CERT, "true"));
    }

    /**
     * @return the maximum number of allowed parallel connections that
     * Anti-Dos will let through to be processed, '5000' by default.
     */
    public static int getAntiDosMaxParallelConnections() {
        return Integer.parseInt(System.getProperty(
                ANTIDOS_MAX_PARALLEL_CONNECTIONS, "5000"));
    }

    /**
     * @return the maximum allowed CPU load value after which Anti-Dos will
     * start rejecting incoming connections (ignored if > 1.0),
     * '1.1' by default.
     */
    public static double getAntiDosMaxCpuLoad() {
        return Double.parseDouble(System.getProperty(
                ANTIDOS_MAX_CPU_LOAD, "1.1"));
    }

    /**
     * @return the minimum number of free file handles after which Anti-Dos will
     * start rejecting incoming connections, '100' by default.
     */
    public static int getAntiDosMinFreeFileHandles() {
        return Integer.parseInt(System.getProperty(
                ANTIDOS_MIN_FREE_FILEHANDLES, "100"));
    }

    /**
     * @return the maximum allowed JVM heap usage value after which Anti-Dos
     * will start rejecting incoming connections (ignored if > 1.0),
     * '1.1' by default.
     */
    public static double getAntiDosMaxHeapUsage() {
        return Double.parseDouble(System.getProperty(
                ANTIDOS_MAX_HEAP_USAGE, "1.1"));
    }

    /**
     * @return whether Anti-Dos should be used, 'true' by default.
     */
    public static boolean isAntiDosEnabled() {
        return "true".equalsIgnoreCase(System.getProperty(
                ANTIDOS_ENABLED, "true"));
    }

    /**
     * @return the HTTPS port at which the central monitor agent listens for
     * incoming monitoring data, '443' by default.
     */
    public static int getCentralMonitorAgentPort() {
        return Integer.parseInt(
                System.getProperty(CENTRAL_MONITOR_AGENT_HTTPS_PORT,
                        Integer.toString(PortNumbers.CLIENT_HTTPS_PORT)));
    }

    /**
     * Global default digital signature algorithm.
     *
     * @return algorithm.
     */
    public static String getDefaultSignatureAlgorithm() {
        return System.getProperty(DEFAULT_SIGNATURE_ALGORITHM, CryptoUtils.DEFAULT_SIGNATURE_ALGORITHM);
    }

    /**
     * Get proxy client's TLS protocols.
     *
     * @return protocols.
     */
    public static String[] getProxyClientTLSProtocols() {
        return System.getProperty(PROXY_CLIENT_TLS_PROTOCOLS, "TLSv1.2,TLSv1.1").split(",");
    }

    private static final String DEFAULT_CLIENT_SSL_CIPHER_SUITES = "TLS_ECDHE_RSA_WITH_AES_128_GCM_SHA256,"
            + "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256,"
            + "TLS_ECDHE_RSA_WITH_AES_256_GCM_SHA384,"
            + "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384,"
            + "TLS_DHE_RSA_WITH_AES_128_GCM_SHA256,"
            + "TLS_DHE_RSA_WITH_AES_128_CBC_SHA256,"
            + "TLS_DHE_RSA_WITH_AES_256_CBC_SHA256,"
            + "TLS_DHE_RSA_WITH_AES_256_GCM_SHA384,"
            + "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA,"
            + "TLS_DHE_RSA_WITH_AES_128_CBC_SHA,"
            + "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA,"
            + "TLS_DHE_RSA_WITH_AES_256_CBC_SHA";

    /**
     * Get proxy client's TLS cipher suites.
     *
     * @return cipher suites.
     */
    public static String[] getProxyClientTLSCipherSuites() {
        return System.getProperty(PROXY_CLIENT_TLS_CIPHERS, DEFAULT_CLIENT_SSL_CIPHER_SUITES).split(",");
    }

    /**
     * Tell whether token PIN policy should be enforced.
     *
     * @return true if PIN policy should be enforced.
     */
    public static boolean shouldEnforceTokenPinPolicy() {
        return Boolean.valueOf(System.getProperty(PROXY_ENFORCE_TOKEN_PIN_POLICY, "false"));
    }

    /**
     *
     * @return the update interval in seconds at which server conf in cached '60' by default
     */
    public static int getServerConfCachePeriod() {
        return Integer.parseInt(
                System.getProperty(SERVER_CONF_CACHE_PERIOD,
                        "60")); // default time in seconds
    }

    /**
     *
     * @return the interval in seconds at which verifier caches results.
     * Max value is 180 seconds and cannot be exceeded in configuration.
     * Default is 60 s.
     */
    public static int getOcspVerifierCachePeriod() {
        int period = Integer.parseInt(System.getProperty(OCSP_VERIFIER_CACHE_PERIOD, "60"));
        return period < OCSP_VERIFIER_CACHE_PERIOD_MAX ? period : OCSP_VERIFIER_CACHE_PERIOD_MAX;
    }

    /**
     * @return the connection maximum idle time that should be set for server proxy connector
     */
    public static int getServerProxyConnectorMaxIdleTime() {
        return Integer.parseInt(System.getProperty(SERVERPROXY_CONNECTOR_MAX_IDLE_TIME,
                DEFAULT_SERVERPROXY_CONNECTOR_MAX_IDLE_TIME));

    }

    /**
     * @return the so_linger value that should be set for server proxy connector
     */
    public static int getServerProxyConnectorSoLinger() {
        return Integer.parseInt(System.getProperty(SERVERPROXY_CONNECTOR_SO_LINGER,
                DEFAULT_SERVERPROXY_CONNECTOR_SO_LINGER));
    }

    /**
     * @return the connection maximum idle time that should be set for client proxy apache HttpClient
     */
    public static int getClientProxyHttpClientTimeout() {
        return Integer.parseInt(System.getProperty(CLIENTPROXY_HTTPCLIENT_TIMEOUT,
                DEFAULT_CLIENTPROXY_HTTPCLIENT_TIMEOUT));

    }

    /**
     * @return the so_linger value that should be set for client proxy apache HttpClient
     */
    public static int getClientProxyHttpClientSoLinger() {
        return Integer.parseInt(System.getProperty(CLIENTPROXY_HTTPCLIENT_SO_LINGER,
                DEFAULT_CLIENTPROXY_HTTPCLIENT_SO_LINGER));
    }


    /**
     * @return the so_linger value that should be set for client proxy connector
     */
    public static int getClientProxyConnectorSoLinger() {
        return Integer.parseInt(System.getProperty(CLIENTPROXY_CONNECTOR_SO_LINGER,
                DEFAULT_CLIENTPROXY_CONNECTOR_SO_LINGER));
    }

    /**
     * @return the connection maximum idle time that should be set for client proxy connector
     */
    public static int getClientProxyConnectorMaxIdleTime() {
        return Integer.parseInt(System.getProperty(CLIENTPROXY_CONNECTOR_MAX_IDLE_TIME,
                DEFAULT_CLIENTPROXY_CONNECTOR_MAX_IDLE_TIME));

    }

    /**
     * @return true if the idle connection monitor thread should be used for client proxy
     */
    public static boolean isClientUseIdleConnectionMonitor() {
        return Boolean.parseBoolean(System.getProperty(CLIENTPROXY_POOL_USE_IDLE_CONNECTION_MONITOR,
                DEFAULT_CLIENTPROXY_POOL_USE_IDLE_CONNECTION_MONITOR));
    }

    /**
     * @return the interval at which pooled idle connections should be cleaned up by the connection monitor
     */
    public static int getClientProxyIdleConnectionMonitorInterval() {
        return Integer.parseInt(System.getProperty(CLIENTPROXY_POOL_IDLE_MONITOR_INTERVAL,
                DEFAULT_CLIENTPROXY_POOL_IDLE_MONITOR_INTERVAL));
    }

    /**
     * @return the idle time after which pooled connections should be discarded
     */
    public static int getClientProxyIdleConnectionMonitorIdleTime() {
        return Integer.parseInt(System.getProperty(CLIENTPROXY_POOL_IDLE_MONITOR_IDLE_TIME,
                DEFAULT_CLIENTPROXY_POOL_IDLE_MONITOR_IDLE_TIME));
    }

    public static boolean isEnableClientProxyPooledConnectionReuse() {
        return Boolean.parseBoolean(System.getProperty(CLIENTPROXY_POOL_REUSE_CONNECTIONS,
                DEFAULT_CLIENTPROXY_POOL_REUSE_CONNECTIONS));
    }

    public static int getClientProxyPoolTotalMaxConnections() {
        return Integer.parseInt(System.getProperty(CLIENTPROXY_POOL_TOTAL_MAX_CONNECTIONS,
                DEFAULT_CLIENTPROXY_POOL_TOTAL_MAX_CONNECTIONS));
    }

    public static int getClientProxyPoolDefaultMaxConnectionsPerRoute() {
        return Integer.parseInt(System.getProperty(CLIENTPROXY_POOL_DEFAULT_MAX_CONN_PER_ROUTE,
                DEFAULT_CLIENTPROXY_POOL_DEFAULT_MAX_CONN_PER_ROUTE));
    }

    /**
     * @return true if SSL sockets should close the underlying socket layer when the SSL socket is closed
     */
    public static boolean isUseSslSocketAutoClose() {
        return Boolean.parseBoolean(System.getProperty(CLIENTPROXY_USE_FASTEST_CONNECTING_SSL_SOCKET_AUTOCLOSE,
                DEFAULT_CLIENTPROXY_USE_FASTEST_CONNECTING_SSL_SOCKET_AUTOCLOSE));
    }

    /**
     * @return true if the URI of the fastest responder should be cached.
     * fastest responder
     */
    public static boolean isUseCachedSSLSessionHostUri() {
        return Boolean.parseBoolean(System.getProperty(CLIENTPROXY_FASTEST_CONNECTING_SSL_USE_URI_CACHE,
                DEFAULT_CLIENTPROXY_FASTEST_CONNECTING_SSL_USE_URI_CACHE));
    }

    /**
     * @return the time in milliseconds, after which connections in a pool should be check for validity, ie.
     * after this time, check if pooled connections are still alive, don't just assume they are.
     */
    public static int getClientProxyValidatePoolConnectionsAfterInactivityMs() {
        return Integer.parseInt(System.getProperty(CLIENTPROXY_POOL_VALIDATE_CONNECTIONS_AFTER_INACTIVITY_OF_MS,
                DEFAULT_CLIENTPROXY_POOL_VALIDATE_CONNECTIONS_AFTER_INACTIVITY_OF_MS));
    }

    /**
     *
     * @return minimum central server global configuration version or default
     */
    public static int getMinimumCentralServerGlobalConfigurationVersion() {
        int version = Integer.parseInt(System.getProperty(MINIMUM_CENTRAL_SERVER_GLOBAL_CONFIGURATION_VERSION,
                DEFAULT_MINIMUM_CENTRAL_SERVER_GLOBAL_CONFIGURATION_VERSION));
        checkVersionValidity(version, CURRENT_GLOBAL_CONFIGURATION_VERSION,
                DEFAULT_MINIMUM_CENTRAL_SERVER_GLOBAL_CONFIGURATION_VERSION);
        return version;
    }

    /**
     *
     * @return minimum configuration proxy global configuration version or default
     */
    public static int getMinimumConfigurationProxyGlobalConfigurationVersion() {
        int version = Integer.parseInt(System.getProperty(
                MINIMUM_CONFIGURATION_PROXY_SERVER_GLOBAL_CONFIGURATION_VERSION,
                DEFAULT_MINIMUM_CONFIGURATION_PROXY_SERVER_GLOBAL_CONFIGURATION_VERSION));
        checkVersionValidity(version, CURRENT_GLOBAL_CONFIGURATION_VERSION,
                DEFAULT_MINIMUM_CONFIGURATION_PROXY_SERVER_GLOBAL_CONFIGURATION_VERSION);
        return version;
    }

    private static void checkVersionValidity(int version, int current, String defaultVersion) {
        if (version > current || version < 1) {
            throw new IllegalArgumentException("Illegal minimum global configuration version in "
                    + "system parameters");
        }
    }
}
