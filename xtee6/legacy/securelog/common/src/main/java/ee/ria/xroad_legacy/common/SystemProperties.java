package ee.ria.xroad_legacy.common;


/**
 * Contains system-wide constants for system properties.
 */
public final class SystemProperties {
    /** The prefix for all properties. */
    private static final String PREFIX = "ee.ria.xroad_legacy.";

    /** Property name of controlling SSL support between Proxies. */
    public static final String PROXY_SSL_SUPPORT =
            PREFIX + "proxy.sslEnabled";

    /** Property name of the Proxy's global configuration file. */
    public static final String GLOBAL_CONFIGURATION_FILE =
            PREFIX + "proxy.globalConfFile";

    /** Property indicating whether GlobalConf distributor is enabled. */
    public static final String PROXY_GLOBAL_CONF_DISTRIBUTOR_ENABLED =
            PREFIX + "proxy.globalConfDistributor.enabled";

    /** Property name of the Proxy's local configuration file. */
    public static final String SERVER_CONFIGURATION_DATABASE_PROPERTIES =
            PREFIX + "proxy.databaseProperties";

    /** Property name of the key configuration file. */
    public static final String KEY_CONFIGURATION_FILE =
            PREFIX + "key.configurationFile";

    /** Property name of the device configuration file. */
    public static final String DEVICE_CONFIGURATION_FILE =
            PREFIX + "device.configurationFile";

    /** Property name of the Proxy's connector host name. */
    public static final String PROXY_CONNECTOR_HOST =
            PREFIX + "proxy.connectorHost";

    /** Property name of the Client Proxy's port number. */
    public static final String PROXY_CLIENT_HTTP_PORT =
            PREFIX + "proxy.clientHttpPort";

    /** Property name of the Client Proxy's port number. */
    public static final String PROXY_CLIENT_HTTPS_PORT =
            PREFIX + "proxy.clientHttpsPort";

    /** Property name of the Client Proxy's timeout (milliseconds). */
    public static final String PROXY_CLIENT_TIMEOUT =
            PREFIX + "proxy.clientTimout";

    /** Property name of the Server Proxy's port number. */
    public static final String PROXY_SERVER_PORT =
            PREFIX + "proxy.serverPort";

    /** Property name of the Signer's port number. */
    public static final String SIGNER_PORT =
            PREFIX + "signer.port";

    /** Property name of the SignerClient's timeout. */
    public static final String SIGNER_CLIENT_TIMEOUT =
            PREFIX + "signer.client.timeout";

    /** Property name of the cached OCSP response path. */
    public static final String OCSP_CACHE_PATH =
            PREFIX + "proxy.ocspCachePath";

    /** Property name of the configuration files path. */
    public static final String CONF_PATH =
            PREFIX + "conf.path";

    /** Property name of the log folder for Log Reader. */
    public static final String LOG_READER_PATH =
            PREFIX + "logReader.path";

    /** Property name of the application log file path. */
    public static final String LOG_PATH =
            PREFIX + "appLog.path";

    /** Property name of the application log level of ee.ria.xroad,* */
    public static final String XROAD_LOG_LEVEL =
            PREFIX + "appLog.xroad.level";

    /** Property name of the secure log file path and file name. */
    public static final String SECURE_LOG_FILE =
            PREFIX + "secureLog.path";

    /** Property name of the temporary files path. */
    public static final String TEMP_FILES_PATH =
            PREFIX + "tempFiles.path";

    /** Property name of the async DB directory. */
    public static final String ASYNC_DB_PATH =
            PREFIX + "asyncdb.path";

    /** Property name of the async sender configuration file. */
    public static final String ASYNC_SENDER_CONFIGURATION_FILE =
            PREFIX + "asyncdb.senderConfFile";

    public static final String MONITORING_AGENT_URI =
            PREFIX + "monitoringagent.uri";

    public static final String SIGNER_MODULE_INSTANCE_PROVIDER =
            PREFIX + "signer.moduleInstanceProvider";

    public static final String DISTRIBUTED_FILES_CLIENT_PORT =
            PREFIX + "distributedfiles.clientPort";

    public static final String DISTRIBUTED_FILES_SIGNATURE_FRESHNESS =
            PREFIX + "distributedfiles.signatureFreshness";

    public static final String SERVICE_IMPORTER_COMMAND =
            PREFIX + "serviceImporter.command";

    public static final String SERVICE_EXPORTER_COMMAND =
            PREFIX + "serviceExporter.command";

    public static final String CENTER_DISTRIBUTED_FILE =
            PREFIX + "center.distributedFile";

    public static final String SERVICE_MEDIATOR_ADDRESS =
            PREFIX + "serviceMediator.address";

    public static final String INTERNAL_SSL_EXPORTER_COMMAND =
            PREFIX + "internalSslExporter.command";

    // --------------------------------------------------------------------- //

    private static final String DEFAULT_CONNECTOR_HOST = "0.0.0.0";

    private SystemProperties() {
    }

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

    public static String getXROADLogLevel() {
        return System.getProperty(XROAD_LOG_LEVEL, "DEBUG");
    }

    public static String getServerConfDatabaseProperties() {
        return System.getProperty(SERVER_CONFIGURATION_DATABASE_PROPERTIES,
                getConfPath() + DefaultFilepaths.SERVER_DATABASE_PROPERTIES);
    }

    public static String getGlobalConfFile() {
        return System.getProperty(GLOBAL_CONFIGURATION_FILE,
                getConfPath() + DefaultFilepaths.GLOBAL_CONFIGURATION_FILE);
    }

    public static String getKeyConfFile() {
        return System.getProperty(KEY_CONFIGURATION_FILE,
                getConfPath() + DefaultFilepaths.KEY_CONFIGURATION_FILE);
    }

    public static String getDeviceConfFile() {
        return System.getProperty(DEVICE_CONFIGURATION_FILE,
                getConfPath() + DefaultFilepaths.DEVICE_CONFIGURATION_FILE);
    }

    public static String getSecureLogFile() {
        return System.getProperty(SECURE_LOG_FILE,
                DefaultFilepaths.SECURE_LOG_FILE);
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

    public static int getSignerPort() {
        return Integer.parseInt(System.getProperty(SIGNER_PORT,
                Integer.toString(PortNumbers.SIGNER_PORT)));
    }

    public static int getSignerClientTimeout() {
        return Integer.parseInt(System.getProperty(SIGNER_CLIENT_TIMEOUT,
                Integer.toString(15000))); // default timeout in milliseconds
    }

    public static int getDistributedFilesClientPort() {
        return Integer.parseInt(
                System.getProperty(DISTRIBUTED_FILES_CLIENT_PORT,
                Integer.toString(PortNumbers.DISTRIBUTED_FILES_CLIENT_PORT)));
    }

    public static int getDistributedFilesAdminPort() {
        return getDistributedFilesClientPort() + 1;
    }

    public static int getDistributedFilesSignatureFreshness() {
        return Integer.parseInt(
                System.getProperty(DISTRIBUTED_FILES_SIGNATURE_FRESHNESS,
                Integer.toString(5))); // default time in minutes
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
                System.getProperty(PROXY_SSL_SUPPORT, "false"));
    }

    /** Returns true, if GlobalConf distributor is enabled. */
    public static boolean isDistributorEnabled() {
        return "true".equalsIgnoreCase(
                System.getProperty(PROXY_GLOBAL_CONF_DISTRIBUTOR_ENABLED,
                        "true"));
    }

    /** Returns where the system copies signed configuration file
     * that will be distributed to securoty servers. */
    public static String getCenterDistributedFile() {
        return System.getProperty(CENTER_DISTRIBUTED_FILE,
                "/var/lib/xroad/public/conf");
    }

    public static String getServiceMediatorAddress() {
        return System.getProperty(SERVICE_MEDIATOR_ADDRESS,
                "http://127.0.0.1:6669");
    }

    public static String getInternalSslExporterCommand() {
        return System.getProperty(INTERNAL_SSL_EXPORTER_COMMAND);
    }
}
