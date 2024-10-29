package org.niis.xroad.configuration.migration;

import java.util.HashMap;
import java.util.Map;

/**
 * Some properties have their paths changed. This class is used to map old paths to new paths.
 */
public class LegacyConfigPathMapping {
    private static final Map<String, String> MAPPING = new HashMap<>();

    static {
        MAPPING.put("proxy.configuration-anchor-file", "configuration-client.configuration-anchor-file");

        MAPPING.put("proxy.ocsp-responder-port", "proxy.ocsp-responder.port");
        MAPPING.put("proxy.ocsp-responder-listen-address", "proxy.ocsp-responder.listen-address");
        MAPPING.put("proxy.ocsp-responder-client-connect-timeout", "proxy.ocsp-responder.client-connect-timeout");
        MAPPING.put("proxy.ocsp-responder-client-read-timeout", "proxy.ocsp-responder.client-read-timeout");
        MAPPING.put("proxy.jetty-ocsp-responder-configuration-file", "proxy.ocsp-responder.jetty-configuration-file");
        MAPPING.put("proxy.server-listen-address", "proxy.server.listen-address");
        MAPPING.put("proxy.server-listen-port", "proxy.server.listen-port");
        MAPPING.put("proxy.server-connector-initial-idle-time", "proxy.server.connector-initial-idle-time");
        MAPPING.put("proxy.server-support-clients-pooled-connections", "proxy.server.support-clients-pooled-connections");
        MAPPING.put("proxy.jetty-serverproxy-configuration-file", "proxy.server.jetty-configuration-file");
        MAPPING.put("proxy.connector-host", "proxy.client-proxy.connector-host");
        MAPPING.put("proxy.client-http-port", "proxy.client-proxy.client-http-port");
        MAPPING.put("proxy.client-https-port", "proxy.client-proxy.client-https-port");
        MAPPING.put("proxy.jetty-clientproxy-configuration-file", "proxy.client-proxy.jetty-configuration-file");
        MAPPING.put("proxy.client-connector-initial-idle-time", "proxy.client-proxy.client-connector-initial-idle-time");
        MAPPING.put("proxy.client-tls-protocols", "proxy.client-proxy.client-tls-protocols");
        MAPPING.put("proxy.client-tls-ciphers", "proxy.client-proxy.client-tls-ciphers");
        MAPPING.put("proxy.client-httpclient-so-linger", "proxy.client-proxy.client-httpclient-so-linger");
        MAPPING.put("proxy.client-httpclient-timeout", "proxy.client-proxy.client-httpclient-timeout");
        MAPPING.put("proxy.pool-total-max-connections", "proxy.client-proxy.pool-total-max-connections");
        MAPPING.put("proxy.pool-total-default-max-connections-per-route", "proxy.client-proxy.pool-total-default-max-connections-per-route");
        MAPPING.put("proxy.pool-validate-connections-after-inactivity-of-millis", "proxy.client-proxy.pool-validate-connections-after-inactivity-of-millis");
        MAPPING.put("proxy.client-idle-connection-monitor-interval", "proxy.client-proxy.client-idle-connection-monitor-interval");
        MAPPING.put("proxy.client-idle-connection-monitor-timeout", "proxy.client-proxy.client-idle-connection-monitor-timeout");
        MAPPING.put("proxy.client-use-idle-connection-monitor", "proxy.client-proxy.client-use-idle-connection-monitor");
    }

    String map(String oldPath) {
        return MAPPING.getOrDefault(oldPath, oldPath);
    }
}
