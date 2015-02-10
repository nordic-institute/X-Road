package ee.cyber.sdsb.common;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import ee.cyber.sdsb.common.util.CryptoUtils;


/**
 * Contains system-wide constants for system properties.
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class SystemProperties {

    /** The prefix for all properties. */
    public static final String PREFIX = "ee.cyber.sdsb.";

    // Common -----------------------------------------------------------------

    /** Property name of the temporary files path. */
    public static final String TEMP_FILES_PATH =
            PREFIX + "common.temp-files-path";

    /** Property name of the downloaded global configuration directory. */
    public static final String CONFIGURATION_PATH =
            PREFIX + "common.configuration-path";

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

    /** Property name of the flag to turn off proxy client SSL verification. */
    public static final String PROXY_VERIFY_CLIENT_CERT =
            PREFIX + "proxy.verify-client-cert";

    /** Property name of the ClientProxy Jetty server configuration file. */
    public static final String JETTY_CLIENTPROXY_CONFIGURATION_FILE =
            PREFIX + "proxy.jetty-clientproxy-configuration-file";

    /** Property name of the ServerProxy Jetty server configuration file. */
    public static final String JETTY_SERVERPROXY_CONFIGURATION_FILE =
            PREFIX + "proxy.jetty-serverproxy-configuration-file";

    /** Property name of switch to log both messages or only the response
     * in ClientProxy and request in ServerProxy. */
    public static final String PROXY_LOG_BOTH_MESSAGES =
            PREFIX + "proxy.log-both-messages";

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

    /** Property name of the SignerClient's timeout. */
    public static final String SIGNER_CLIENT_TIMEOUT =
            PREFIX + "signer.client-timeout";

    public static final String SIGNER_MODULE_INSTANCE_PROVIDER =
            PREFIX + "signer.module-instance-provider";

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

    // AsyncDB ----------------------------------------------------------------

    /** Property name of the async DB directory. */
    public static final String ASYNC_DB_PATH =
            PREFIX + "async-db.path";

    /** Property name of the async sender configuration file. */
    public static final String ASYNC_SENDER_CONFIGURATION_FILE =
            PREFIX + "async-db.sender-conf-file";

    // ------------------------------------------------------------------------

    public static final String CONFIGURATION_CLIENT_PORT =
            PREFIX + "configuration-client.port";

    public static final String CONFIGURATION_CLIENT_UPDATE_INTERVAL_SECONDS =
            PREFIX + "configuration-client.update-interval";

    // Center -----------------------------------------------------------------

    public static final String CENTER_DATABASE_PROPERTIES = PREFIX
            + "center.database-properties";

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

    /** Property name of the application log level of ee.cyber.sdsb,* */
    public static final String SDSB_LOG_LEVEL =
            PREFIX + "appLog.sdsb.level";

    // Proxy UI ---------------------------------------------------------------

    public static final String SERVICE_IMPORTER_COMMAND =
            PREFIX + "proxy-ui.service-importer-command";

    public static final String SERVICE_EXPORTER_COMMAND =
            PREFIX + "proxy-ui.service-exporter-command";

    public static final String SERVICE_MEDIATOR_ADDRESS =
            PREFIX + "proxy-ui.service-mediator-address";

    public static final String INTERNAL_SSL_EXPORTER_COMMAND =
            PREFIX + "proxy-ui.internal-ssl-exporter-command";

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

    public static final String CONF_FILE_USER_LOCAL =
            getConfPath() + "conf.d/local.ini";

    public static final String CONF_FILE_ADDON_PATH =
            getConfPath() + "conf.d/addons/";

    // --------------------------------------------------------------------- //

    private static final String DEFAULT_CONNECTOR_HOST = "0.0.0.0";

    public static String getConfPath() {
        return System.getProperty(CONF_PATH, DefaultFilepaths.CONF_PATH);
    }

    public static String getAsyncDBPath() {
        return System.getProperty(ASYNC_DB_PATH,
                DefaultFilepaths.ASYNC_DB_PATH);
    }

    public static String getAsyncSenderConfFile() {
        return System.getProperty(ASYNC_SENDER_CONFIGURATION_FILE,
                getConfPath()
                    + DefaultFilepaths.ASYNC_SENDER_CONFIGURATION_FILE);
    }

    public static String getLogPath() {
        return System.getProperty(LOG_PATH, DefaultFilepaths.LOG_PATH);
    }

    public static String getSDSBLogLevel() {
        return System.getProperty(SDSB_LOG_LEVEL, "DEBUG");
    }

    public static String getDatabasePropertiesFile() {
        return System.getProperty(DATABASE_PROPERTIES,
                getConfPath() + DefaultFilepaths.SERVER_DATABASE_PROPERTIES);
    }

    public static String getConfigurationAnchorFile() {
        return System.getProperty(CONFIGURATION_ANCHOR_FILE,
                getConfPath() + DefaultFilepaths.CONFIGURATION_ANCHOR_FILE);
    }

    public static String getConfigurationPath() {
        return System.getProperty(CONFIGURATION_PATH,
                getConfPath() + DefaultFilepaths.CONFIGURATION_PATH);
    }

    public static String getKeyConfFile() {
        return System.getProperty(KEY_CONFIGURATION_FILE,
                getConfPath() + DefaultFilepaths.KEY_CONFIGURATION_FILE);
    }

    public static String getDeviceConfFile() {
        return System.getProperty(DEVICE_CONFIGURATION_FILE,
                getConfPath() + DefaultFilepaths.DEVICE_CONFIGURATION_FILE);
    }

    public static String getJettyClientProxyConfFile() {
        return System.getProperty(JETTY_CLIENTPROXY_CONFIGURATION_FILE,
                getConfPath()
                    + DefaultFilepaths.JETTY_CLIENTPROXY_CONFIGURATION_FILE);
    }

    public static String getJettyServerProxyConfFile() {
        return System.getProperty(JETTY_SERVERPROXY_CONFIGURATION_FILE,
                getConfPath()
                    + DefaultFilepaths.JETTY_SERVERPROXY_CONFIGURATION_FILE);
    }

    public static String getLogReaderPath() {
        return System.getProperty(LOG_READER_PATH,
                DefaultFilepaths.SECURE_LOG_PATH);
    }

    public static String getTempFilesPath() {
        return System.getProperty(TEMP_FILES_PATH,
                DefaultFilepaths.TEMP_FILES_PATH);
    }

    public static String getOcspCachePath() {
        return System.getProperty(OCSP_CACHE_PATH,
                DefaultFilepaths.OCSP_CACHE_PATH);
    }

    public static String getConfBackupPath() {
        return System.getProperty(CONF_BACKUP_PATH,
                DefaultFilepaths.CONF_BACKUP_PATH);
    }

    public static String getV5ImportPath() {
        return System.getProperty(V5_IMPORT_PATH,
                DefaultFilepaths.V5_IMPORT_PATH);
    }

    public static String getConnectorHost() {
        return System.getProperty(PROXY_CONNECTOR_HOST,
                DEFAULT_CONNECTOR_HOST);
    }

    public static int getClientProxyHttpPort() {
        return Integer.parseInt(System.getProperty(PROXY_CLIENT_HTTP_PORT,
                Integer.toString(PortNumbers.CLIENT_HTTP_PORT)));
    }

    public static int getClientProxyHttpsPort() {
        return Integer.parseInt(System.getProperty(PROXY_CLIENT_HTTPS_PORT,
                Integer.toString(PortNumbers.CLIENT_HTTPS_PORT)));
    }

    public static int getClientProxyTimeout() {
        return Integer.parseInt(System.getProperty(PROXY_CLIENT_TIMEOUT,
                Integer.toString(300000))); // default timeout in milliseconds
    }

    public static int getServerProxyPort() {
        return Integer.parseInt(System.getProperty(PROXY_SERVER_PORT,
                Integer.toString(PortNumbers.PROXY_PORT)));
    }

    public static int getServerProxyListenPort() {
        return Integer.parseInt(System.getProperty(PROXY_SERVER_LISTEN_PORT,
                Integer.toString(PortNumbers.PROXY_PORT)));
    }

    public static int getServerProxyPort2() {
        return Integer.parseInt(System.getProperty(PROXY_SERVER_PORT,
                Integer.toString(PortNumbers.PROXY_PORT)));
    }

    public static String getServerProxyListenAddress() {
        return System.getProperty(PROXY_SERVER_LISTEN_ADDRESS,
                DEFAULT_CONNECTOR_HOST);
    }

    public static int getSignerPort() {
        return Integer.parseInt(System.getProperty(SIGNER_PORT,
                Integer.toString(PortNumbers.SIGNER_PORT)));
    }

    public static int getSignerClientTimeout() {
        return Integer.parseInt(System.getProperty(SIGNER_CLIENT_TIMEOUT,
                Integer.toString(60000))); // default timeout in milliseconds
    }

    public static int getConfigurationClientPort() {
        return Integer.parseInt(
                System.getProperty(CONFIGURATION_CLIENT_PORT,
                Integer.toString(PortNumbers.CONFIGURATION_CLIENT_PORT)));
    }

    public static int getConfigurationClientUpdateIntervalSeconds() {
        return Integer.parseInt(
                System.getProperty(CONFIGURATION_CLIENT_UPDATE_INTERVAL_SECONDS,
                Integer.toString(60))); // default time in seconds
    }

    public static int getOcspResponderPort() {
        return Integer.parseInt(System.getProperty(OCSP_RESPONDER_PORT,
                Integer.toString(PortNumbers.PROXY_OCSP_PORT)));
    }

    public static String getOcspResponderListenAddress() {
        return System.getProperty(OCSP_RESPONDER_LISTEN_ADDRESS,
                DEFAULT_CONNECTOR_HOST);
    }

    public static String getServiceImporterCommand() {
        return System.getProperty(SERVICE_IMPORTER_COMMAND);
    }

    public static String getServiceExporterCommand() {
        return System.getProperty(SERVICE_EXPORTER_COMMAND);
    }

    /** If true, SSL is used between ClientProxy and ServerProxy. */
    public static boolean isSslEnabled() {
        return "true".equalsIgnoreCase(
                System.getProperty(PROXY_SSL_SUPPORT, "true"));
    }

    public static String getCenterDatabasePropertiesFile() {
        return System.getProperty(CENTER_DATABASE_PROPERTIES,
                getConfPath() + DefaultFilepaths.SERVER_DATABASE_PROPERTIES);
    }

    /** If true, configuration of trusted anchors will be enabled. */
    public static boolean getCenterTrustedAnchorsAllowed() {
        return "true".equalsIgnoreCase(
               System.getProperty(CENTER_TRUSTED_ANCHORS_ALLOWED, "false"));
    }

    /** Returns the name of the signed internal configuration directory
     * that will be distributed to security servers inside instance. */
    public static String getCenterInternalDirectory() {
        return System.getProperty(CENTER_INTERNAL_DIRECTORY, "internalconf");
    }

    /** Returns the name of the signed external configuration directory
     * that will be distributed to security servers inside federation. */
    public static String getCenterExternalDirectory() {
        return System.getProperty(CENTER_EXTERNAL_DIRECTORY, "externalconf");
    }

    /**
     * Path to directory in central where both private and shared parameter
     * files are created for distribution.
     */
    public static String getCenterGeneratedConfDir() {
        return System.getProperty(CENTER_GENERATED_CONF_DIR,
                DefaultFilepaths.DISTRIBUTED_GLOBALCONF_PATH);
    }

    public static String getServiceMediatorAddress() {
        return System.getProperty(SERVICE_MEDIATOR_ADDRESS,
                "http://127.0.0.1:6669");
    }

    public static String getInternalSslExporterCommand() {
        return System.getProperty(INTERNAL_SSL_EXPORTER_COMMAND);
    }

    public static int getMonitorAgentAdminPort() {
        return Integer.parseInt(System.getProperty(
                MONITOR_AGENT_ADMIN_PORT,
                Integer.toString(PortNumbers.MONITOR_AGENT_ADMIN_PORT)));
    }

    public static int getProxyMonitorAgentSendingInterval() {
        return Integer.parseInt(System.getProperty(
                PROXY_MONITOR_AGENT_SENDING_INTERVAL, "180"));
    }

    public static String getMonitorAgentConfFile() {
        return System.getProperty(MONITOR_AGENT_CONFIGURATION_FILE,
                getConfPath()
                    + DefaultFilepaths.MONITOR_AGENT_CONFIGURATION_FILE);
    }

    public static int getZabbixConfiguratorClientTimeout() {
        return Integer.parseInt(System.getProperty(
                ZABBIX_CONFIGURATOR_CLIENT_TIMEOUT,
                Integer.toString(300000))); // Default timeout in milliseconds.
    }

    public static String getConfigurationProxyConfPath() {
        return System.getProperty(
                CONFIGURATION_PROXY_CONF_PATH,
                getConfPath() + "confproxy/");
    }

    public static String getConfigurationProxyDownloadScript() {
        return System.getProperty(
                CONFIGURATION_PROXY_DOWNLOAD_SCRIPT,
                "/usr/share/sdsb/scripts/download_instance_configuration.sh");
    }

    public static String getConfigurationProxyGeneratedConfPath() {
        return System.getProperty(
                CONFIGURATION_PROXY_GENERATED_CONF_PATH,
                DefaultFilepaths.DISTRIBUTED_GLOBALCONF_PATH);
    }

    public static String getConfigurationProxySignatureAlgorithmId() {
        return System.getProperty(
                CONFIGURATION_PROXY_SIGNATURE_ALGORITHM_ID,
                CryptoUtils.SHA512WITHRSA_ID);
    }

    public static String getConfigurationProxyHashAlgorithmUri() {
        return System.getProperty(
                CONFIGURATION_PROXY_HASH_ALGORITHM_URI,
                CryptoUtils.DEFAULT_DIGEST_ALGORITHM_URI);
    }

    public static String getConfigurationProxyAddress() {
        return System.getProperty(CONFIGURATION_PROXY_ADDRESS,
                DEFAULT_CONNECTOR_HOST);
    }

    public static int getProxyParamsCollectingInterval() {
        return Integer.parseInt(System.getProperty(
                PROXY_PARAMS_COLLECTING_INTERVAL, "60"));
    }

    public static String getNetStatsFile() {
        return System.getProperty(NET_STATS_FILE, "/proc/net/dev");
    }

    /** Returns true, if client proxy should verify client SSL cert. */
    public static boolean shouldVerifyClientCert() {
        return "true".equalsIgnoreCase(
                System.getProperty(PROXY_VERIFY_CLIENT_CERT, "true"));
    }

    public static boolean shouldLogBothMessages() {
        return "true".equalsIgnoreCase(
                System.getProperty(PROXY_LOG_BOTH_MESSAGES, "true"));
    }

    public static int getAntiDosMaxParallelConnections() {
        return Integer.parseInt(System.getProperty(
                ANTIDOS_MAX_PARALLEL_CONNECTIONS, "5000"));
    }

    // Set to > 1.0 to disable CPU load checking.
    public static double getAntiDosMaxCpuLoad() {
        return Double.parseDouble(System.getProperty(
                ANTIDOS_MAX_CPU_LOAD, "1.1"));
    }

    public static int getAntiDosMinFreeFileHandles() {
        return Integer.parseInt(System.getProperty(
                ANTIDOS_MIN_FREE_FILEHANDLES, "100"));
    }

    public static double getAntiDosMaxHeapUsage() {
        return Double.parseDouble(System.getProperty(
                ANTIDOS_MAX_HEAP_USAGE, "1.1"));
    }

    public static boolean isAntiDosEnabled() {
        return "true".equalsIgnoreCase(System.getProperty(
                ANTIDOS_ENABLED, "true"));
    }

}
