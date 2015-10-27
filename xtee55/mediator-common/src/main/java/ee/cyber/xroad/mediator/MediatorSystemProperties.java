package ee.cyber.xroad.mediator;

import ee.ria.xroad.common.SystemProperties;

/**
 * Contains constants for mediator system properties.
 */
public final class MediatorSystemProperties {

    private static final int DEFAULT_CLIENT_MEDIATOR_HTTPS_PORT = 8443;

    private static final int DEFAULT_CLIENT_MEDIATOR_HTTP_PORT = 8080;

    /** The prefix for all properties. */
    public static final String PREFIX = "xtee55.";

    public static final String XROAD_PROXY_ADDRESS =
            PREFIX + "proxy.xroad-address";

    public static final String V5_XROAD_PROXY_ADDRESS =
            PREFIX + "proxy.v5-xroad-address";

    public static final String V5_XROAD_URIPROXY_ADDRESS =
            PREFIX + "proxy.v5-xroad-uriproxy-address";

    public static final String CLIENT_MEDIATOR_HTTP_PORT =
            PREFIX + "client-mediator.http-port";

    public static final String CLIENT_MEDIATOR_HTTPS_PORT =
            PREFIX + "client-mediator.https-port";

    public static final String IDENTIFIER_MAPPING_FILE =
            PREFIX + "common.identifier-mapping-file";

    /** Property name of the application log level of ee.cyber.xroad,* */
    public static final String XROAD_LOG_LEVEL = PREFIX + "appLog.xroad.level";

    // --------------------------------------------------------------------- //

    public static final String CONF_FILE_CLIENT_MEDIATOR =
            SystemProperties.getConfPath() + "conf.d/client-mediator.ini";

    public static final String CONF_FILE_SERVICE_IMPORTER =
            SystemProperties.getConfPath() + "conf.d/service-importer.ini";

    // --------------------------------------------------------------------- //

    private MediatorSystemProperties() {
    }

    /**
     * @return the X-Road 6.0 proxy address
     */
    public static String getXRoadProxyAddress() {
        return System.getProperty(XROAD_PROXY_ADDRESS, "http://localhost:8060");
    }

    /**
     * @return the X-Road 5.0 proxy address
     */
    public static String getV5XRoadProxyAddress() {
        return System.getProperty(V5_XROAD_PROXY_ADDRESS,
                "http://localhost:80/cgi-bin/consumer_proxy");
    }

    /**
     * @return the X-Road 5.0 uriproxy address
     */
    public static String getV5XRoadUriProxyAddress() {
        return System.getProperty(V5_XROAD_URIPROXY_ADDRESS,
                "http://localhost:80/cgi-bin/uriproxy");
    }

    /**
     * @return the client mediator address
     */
    public static String getClientMediatorConnectorHost() {
        return "0.0.0.0";
    }

    /**
     * @return the client mediator HTTP port
     */
    public static int getClientMediatorHttpPort() {
        return getIntProperty(CLIENT_MEDIATOR_HTTP_PORT,
                DEFAULT_CLIENT_MEDIATOR_HTTP_PORT);
    }

    /**
     * @return the client mediator HTTPS port
     */
    public static int getClientMediatorHttpsPort() {
        return getIntProperty(CLIENT_MEDIATOR_HTTPS_PORT,
                DEFAULT_CLIENT_MEDIATOR_HTTPS_PORT);
    }

    /**
     * @return the identifier mapping filepath
     */
    public static String getIdentifierMappingFile() {
        return System.getProperty(IDENTIFIER_MAPPING_FILE);
    }

    /**
     * @return the logging level
     */
    public static String getXRoadLogLevel() {
        return System.getProperty(XROAD_LOG_LEVEL, "DEBUG");
    }

    // --------------------------------------------------------------------- //

    private static int getIntProperty(String key, int defaultValue) {
        try {
            return Integer.parseInt(System.getProperty(key,
                    Integer.toString(defaultValue)));
        } catch (NumberFormatException e) {
            System.err.println("Failed to read property '" + key + "':" + e);
            return defaultValue;
        }
    }
}
