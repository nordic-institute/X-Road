package ee.cyber.xroad.mediator;

import ee.cyber.sdsb.common.SystemProperties;

public final class MediatorSystemProperties {

    /** The prefix for all properties. */
    private static final String PREFIX = "ee.cyber.xroad.";

    public static final String SDSB_PROXY_ADDRESS =
            PREFIX + "proxy.sdsbAddress";

    public static final String XROAD_PROXY_ADDRESS =
            PREFIX + "proxy.xroadAddress";

    public static final String XROAD_URIPROXY_ADDRESS =
            PREFIX + "proxy.xroadUriProxyAddress";

    public static final String CLIENT_MEDIATOR_HTTP_PORT =
            PREFIX + "clientMediator.httpPort";

    public static final String CLIENT_MEDIATOR_HTTPS_PORT =
            PREFIX + "clientMediator.httpsPort";

    public static final String SERVICE_MEDIATOR_HTTP_PORT =
            PREFIX + "serviceMediator.httpPort";

    public static final String SERVICE_MEDIATOR_HTTPS_PORT =
            PREFIX + "serviceMediator.httpsPort";

    public static final String IDENTIFIER_MAPPING_FILE =
            PREFIX + "identifierMappingFile";

    /** Property name of the application log level of ee.cyber.xroad,* */
    public static final String XROAD_LOG_LEVEL = PREFIX + "appLog.xroad.level";

    // --------------------------------------------------------------------- //

    public static String getSdsbProxyAddress() {
        return System.getProperty(SDSB_PROXY_ADDRESS, "http://localhost:8060");
    }

    public static String getXroadProxyAddress() {
        return System.getProperty(XROAD_PROXY_ADDRESS,
                "http://localhost:80/cgi-bin/consumer_proxy");
    }

    public static String getXroadUriProxyAddress() {
        return System.getProperty(XROAD_URIPROXY_ADDRESS,
                "http://localhost:80/cgi-bin/uriproxy");
    }

    public static String getClientMediatorConnectorHost() {
        return "0.0.0.0";
    }

    public static int getClientMediatorHttpPort() {
        return getIntProperty(CLIENT_MEDIATOR_HTTP_PORT, 8080);
    }

    public static int getClientMediatorHttpsPort() {
        return getIntProperty(CLIENT_MEDIATOR_HTTPS_PORT, 8443);
    }

    public static String getServiceMediatorConnectorHost() {
        return "localhost";
    }

    public static int getServiceMediatorHttpPort() {
        return getIntProperty(SERVICE_MEDIATOR_HTTP_PORT, 8090);
    }

    public static int getServiceMediatorHttpsPort() {
        return getIntProperty(SERVICE_MEDIATOR_HTTPS_PORT, 8444);
    }

    public static String getIdentifierMappingFile() {
        return System.getProperty(IDENTIFIER_MAPPING_FILE,
                SystemProperties.getConfPath() + "identifiermapping.xml");
    }

    public static String getXroadLogLevel() {
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
