package ee.cyber.sdsb.common;

/**
 * This interface contains global constants, such as port numbers
 * and configuration locations.
 */
public interface PortNumbers {
    /** Client proxy listens for HTTP queries. */
    public static final int CLIENT_HTTP_PORT = 8080;

    /** Client proxy listens for HTTPS queries. */
    public static final int CLIENT_HTTPS_PORT = 8443;

    /** Port for connection between client and server proxy. */
    public static final int PROXY_PORT = 5500;

    /** Server proxy listens for OCSP requests. */
    public static final int PROXY_OCSP_PORT = 5577;

    /** Admin port for proxy. */
    public static final int ADMIN_PORT = 5566;

    /** Signer listens for HTTP queries. */
    public static final int SIGNER_PORT = 5558;

    /** Center-Service HTTP port. */
    public static final int CENTER_SERVICE_HTTP_PORT = 3333;

    /** Center-Service HTTPS port. */
    public static final int CENTER_SERVICE_HTTPS_PORT = 3443;

    /** Port for Distributed Files Client. */
    public static final int CONFIGURATION_CLIENT_PORT = 5665;

    /** Admin port for proxy monitor agent */
    public static final int PROXY_MONITOR_AGENT_ADMIN_PORT = 5588;
}
