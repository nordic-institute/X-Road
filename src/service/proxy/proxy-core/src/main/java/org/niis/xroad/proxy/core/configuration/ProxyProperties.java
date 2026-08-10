/*
 * The MIT License
 *
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

package org.niis.xroad.proxy.core.configuration;

import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.properties.config.XRoadConfig;
import org.niis.xroad.proxy.core.addon.opmonitoring.OpMonitorBufferProperties;
import org.niis.xroad.proxy.core.addon.opmonitoring.OpMonitorConnectionProperties;

import java.time.Duration;
import java.util.Optional;

import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.ADDON_META_SERVICES_ENABLED;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.ADDON_OP_MONITOR_ENABLED;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.ADDON_PROXY_MONITOR_ENABLED;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.ADMIN_PORT;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.BATCH_SIGNING_ENABLED;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.CLIENT_PROXY_CLIENT_CONNECTOR_INITIAL_IDLE_TIME;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.CLIENT_PROXY_CLIENT_HTTPCLIENT_SO_LINGER;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.CLIENT_PROXY_CLIENT_HTTPCLIENT_TIMEOUT;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.CLIENT_PROXY_CLIENT_HTTPS_PORT;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.CLIENT_PROXY_CLIENT_HTTP_PORT;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.CLIENT_PROXY_CLIENT_IDLE_CONNECTION_MONITOR_INTERVAL;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.CLIENT_PROXY_CLIENT_IDLE_CONNECTION_MONITOR_TIMEOUT;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.CLIENT_PROXY_CLIENT_TIMEOUT;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.CLIENT_PROXY_CLIENT_TLS_CIPHERS;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.CLIENT_PROXY_CLIENT_TLS_PROTOCOLS;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.CLIENT_PROXY_CLIENT_USE_IDLE_CONNECTION_MONITOR;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.CLIENT_PROXY_CONNECTOR_HOST;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.CLIENT_PROXY_ENABLE_REQUEST_RETRY;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.CLIENT_PROXY_FASTEST_CONNECTING_SSL_URI_CACHE_PERIOD;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.CLIENT_PROXY_FASTEST_CONNECTING_SSL_URI_UNUSABLE_PERIOD;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.CLIENT_PROXY_JETTY_CONFIGURATION_FILE;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.CLIENT_PROXY_POOL_ENABLE_CONNECTION_REUSE;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.CLIENT_PROXY_POOL_TOTAL_DEFAULT_MAX_CONNECTIONS_PER_ROUTE;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.CLIENT_PROXY_POOL_TOTAL_MAX_CONNECTIONS;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.CLIENT_PROXY_POOL_VALIDATE_CONNECTIONS_AFTER_INACTIVITY_OF_MILLIS;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.CLIENT_PROXY_USE_FASTEST_CONNECTING_SSL_SOCKET_AUTOCLOSE;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.DSP_ENABLED;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.ENFORCE_CLIENT_IS_CERT_VALIDITY_PERIOD_CHECK;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.HEALTH_CHECK_ENABLED;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.HEALTH_CHECK_INTERFACE;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.HEALTH_CHECK_PORT;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.HSM_HEALTH_CHECK_ENABLED;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.LOG_CLIENT_CERT;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.MESSAGE_SIGN_DIGEST_NAME;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.OCSP_RESPONDER_CLIENT_CONNECT_TIMEOUT;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.OCSP_RESPONDER_CLIENT_READ_TIMEOUT;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.OCSP_RESPONDER_JETTY_CONFIGURATION_FILE;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.OCSP_RESPONDER_LISTEN_ADDRESS;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.OCSP_RESPONDER_PORT;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.SERVER_CONNECTOR_INITIAL_IDLE_TIME;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.SERVER_JETTY_CONFIGURATION_FILE;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.SERVER_LISTEN_ADDRESS;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.SERVER_LISTEN_PORT;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.SERVER_MIN_SUPPORTED_CLIENT_VERSION;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.SERVER_PORT;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.SERVER_SUPPORT_CLIENTS_POOLED_CONNECTIONS;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.SSL_ENABLED;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.STRICT_IDENTIFIER_CHECKS;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.VERIFY_CLIENT_CERT;
import static org.niis.xroad.common.properties.config.keys.ProxyConfigKeys.XROAD_TLS_CIPHERS;

@RequiredArgsConstructor
public class ProxyProperties {

    private final XRoadConfig xRoadConfig;
    private final ServerProperties serverProperties;
    private final ClientProxyProperties clientProxyProperties;
    private final Addon addon;

    public ProxyProperties(XRoadConfig xRoadConfig) {
        this.xRoadConfig = xRoadConfig;
        this.serverProperties = new ServerProperties(xRoadConfig);
        this.clientProxyProperties = new ClientProxyProperties(xRoadConfig);
        this.addon = new Addon(xRoadConfig);
    }

    public ServerProperties server() {
        return serverProperties;
    }

    public ClientProxyProperties clientProxy() {
        return clientProxyProperties;
    }

    public Addon addon() {
        return addon;
    }

    public int adminPort() {
        return xRoadConfig.value(ADMIN_PORT);
    }

    public boolean dspEnabled() {
        return xRoadConfig.value(DSP_ENABLED);
    }

    public boolean sslEnabled() {
        return xRoadConfig.value(SSL_ENABLED);
    }

    public boolean healthCheckEnabled() {
        return xRoadConfig.value(HEALTH_CHECK_ENABLED);
    }

    public int healthCheckPort() {
        return xRoadConfig.value(HEALTH_CHECK_PORT);
    }

    public String healthCheckInterface() {
        return xRoadConfig.value(HEALTH_CHECK_INTERFACE);
    }

    public boolean hsmHealthCheckEnabled() {
        return xRoadConfig.value(HSM_HEALTH_CHECK_ENABLED);
    }

    public String messageSignDigestName() {
        return xRoadConfig.value(MESSAGE_SIGN_DIGEST_NAME);
    }

    public boolean verifyClientCert() {
        return xRoadConfig.value(VERIFY_CLIENT_CERT);
    }

    public boolean logClientCert() {
        return xRoadConfig.value(LOG_CLIENT_CERT);
    }

    public boolean enforceClientIsCertValidityPeriodCheck() {
        return xRoadConfig.value(ENFORCE_CLIENT_IS_CERT_VALIDITY_PERIOD_CHECK);
    }

    public int serverProxyPort() {
        return xRoadConfig.value(SERVER_PORT);
    }

    public String[] xroadTlsCiphers() {
        return xRoadConfig.value(XROAD_TLS_CIPHERS);
    }

    public boolean batchSigningEnabled() {
        return xRoadConfig.value(BATCH_SIGNING_ENABLED);
    }

    public boolean strictIdentifierChecks() {
        return xRoadConfig.value(STRICT_IDENTIFIER_CHECKS);
    }

    @RequiredArgsConstructor
    public static class ClientProxyProperties {

        private final XRoadConfig xRoadConfig;

        public String connectorHost() {
            return xRoadConfig.value(CLIENT_PROXY_CONNECTOR_HOST);
        }

        public int clientHttpPort() {
            return xRoadConfig.value(CLIENT_PROXY_CLIENT_HTTP_PORT);
        }

        public int clientHttpsPort() {
            return xRoadConfig.value(CLIENT_PROXY_CLIENT_HTTPS_PORT);
        }

        public String jettyConfigurationFile() {
            return xRoadConfig.value(CLIENT_PROXY_JETTY_CONFIGURATION_FILE);
        }

        public int clientConnectorInitialIdleTime() {
            return xRoadConfig.value(CLIENT_PROXY_CLIENT_CONNECTOR_INITIAL_IDLE_TIME);
        }

        public int clientProxyTimeout() {
            return xRoadConfig.value(CLIENT_PROXY_CLIENT_TIMEOUT);
        }

        public int clientHttpclientSoLinger() {
            return xRoadConfig.value(CLIENT_PROXY_CLIENT_HTTPCLIENT_SO_LINGER);
        }

        public int clientHttpclientTimeout() {
            return xRoadConfig.value(CLIENT_PROXY_CLIENT_HTTPCLIENT_TIMEOUT);
        }

        public int poolTotalMaxConnections() {
            return xRoadConfig.value(CLIENT_PROXY_POOL_TOTAL_MAX_CONNECTIONS);
        }

        public int poolTotalDefaultMaxConnectionsPerRoute() {
            return xRoadConfig.value(CLIENT_PROXY_POOL_TOTAL_DEFAULT_MAX_CONNECTIONS_PER_ROUTE);
        }

        public int poolValidateConnectionsAfterInactivityOfMillis() {
            return xRoadConfig.value(CLIENT_PROXY_POOL_VALIDATE_CONNECTIONS_AFTER_INACTIVITY_OF_MILLIS);
        }

        public int clientIdleConnectionMonitorInterval() {
            return xRoadConfig.value(CLIENT_PROXY_CLIENT_IDLE_CONNECTION_MONITOR_INTERVAL);
        }

        public int clientIdleConnectionMonitorTimeout() {
            return xRoadConfig.value(CLIENT_PROXY_CLIENT_IDLE_CONNECTION_MONITOR_TIMEOUT);
        }

        public boolean clientUseIdleConnectionMonitor() {
            return xRoadConfig.value(CLIENT_PROXY_CLIENT_USE_IDLE_CONNECTION_MONITOR);
        }

        public int clientProxyFastestConnectingSslUriCachePeriod() {
            return xRoadConfig.value(CLIENT_PROXY_FASTEST_CONNECTING_SSL_URI_CACHE_PERIOD);
        }

        public Duration clientProxyFastestConnectingSslUriUnusablePeriod() {
            return xRoadConfig.value(CLIENT_PROXY_FASTEST_CONNECTING_SSL_URI_UNUSABLE_PERIOD);
        }

        public boolean useSslSocketAutoClose() {
            return xRoadConfig.value(CLIENT_PROXY_USE_FASTEST_CONNECTING_SSL_SOCKET_AUTOCLOSE);
        }

        public String[] clientTlsProtocols() {
            return xRoadConfig.value(CLIENT_PROXY_CLIENT_TLS_PROTOCOLS);
        }

        public String[] clientTlsCiphers() {
            return xRoadConfig.value(CLIENT_PROXY_CLIENT_TLS_CIPHERS);
        }

        public boolean poolEnableConnectionReuse() {
            return xRoadConfig.value(CLIENT_PROXY_POOL_ENABLE_CONNECTION_REUSE);
        }

        public boolean enableRequestRetry() {
            return xRoadConfig.value(CLIENT_PROXY_ENABLE_REQUEST_RETRY);
        }
    }

    @RequiredArgsConstructor
    public static class ServerProperties {

        private final XRoadConfig xRoadConfig;

        public String listenAddress() {
            return xRoadConfig.value(SERVER_LISTEN_ADDRESS);
        }

        public int listenPort() {
            return xRoadConfig.value(SERVER_LISTEN_PORT);
        }

        public int connectorInitialIdleTime() {
            return xRoadConfig.value(SERVER_CONNECTOR_INITIAL_IDLE_TIME);
        }

        public String jettyConfigurationFile() {
            return xRoadConfig.value(SERVER_JETTY_CONFIGURATION_FILE);
        }

        public boolean serverSupportClientsPooledConnections() {
            return xRoadConfig.value(SERVER_SUPPORT_CLIENTS_POOLED_CONNECTIONS);
        }

        public Optional<String> minSupportedClientVersion() {
            return xRoadConfig.valueOpt(SERVER_MIN_SUPPORTED_CLIENT_VERSION);
        }
    }

    @RequiredArgsConstructor
    public static class OcspResponderProperties {
        private final XRoadConfig xRoadConfig;

        public String listenAddress() {
            return xRoadConfig.value(OCSP_RESPONDER_LISTEN_ADDRESS);
        }

        public int port() {
            return xRoadConfig.value(OCSP_RESPONDER_PORT);
        }

        public int clientConnectTimeout() {
            return xRoadConfig.value(OCSP_RESPONDER_CLIENT_CONNECT_TIMEOUT);
        }

        public int clientReadTimeout() {
            return xRoadConfig.value(OCSP_RESPONDER_CLIENT_READ_TIMEOUT);
        }

        public String jettyConfigurationFile() {
            return xRoadConfig.value(OCSP_RESPONDER_JETTY_CONFIGURATION_FILE);
        }
    }

    @RequiredArgsConstructor
    public static class Addon {

        private final XRoadConfig xRoadConfig;
        private final ProxyAddonProxyMonitorProperties proxyAddonProxyMonitorProperties;
        private final ProxyAddonMetaservicesProperties proxyAddonMetaservicesProperties;
        private final ProxyAddonOpMonitorProperties proxyAddonOpMonitorProperties;

        public Addon(XRoadConfig xRoadConfig) {
            this.xRoadConfig = xRoadConfig;
            this.proxyAddonProxyMonitorProperties = new ProxyAddonProxyMonitorProperties(xRoadConfig);
            this.proxyAddonMetaservicesProperties = new ProxyAddonMetaservicesProperties(xRoadConfig);
            this.proxyAddonOpMonitorProperties = new ProxyAddonOpMonitorProperties(xRoadConfig);
        }

        public ProxyAddonProxyMonitorProperties proxyMonitor() {
            return proxyAddonProxyMonitorProperties;
        }

        public ProxyAddonMetaservicesProperties metaservices() {
            return proxyAddonMetaservicesProperties;
        }

        public ProxyAddonOpMonitorProperties opMonitor() {
            return proxyAddonOpMonitorProperties;
        }

        @RequiredArgsConstructor
        public static class ProxyAddonProxyMonitorProperties {

            private final XRoadConfig xRoadConfig;

            public boolean enabled() {
                return xRoadConfig.value(ADDON_PROXY_MONITOR_ENABLED);
            }
        }

        @RequiredArgsConstructor
        public static class ProxyAddonMetaservicesProperties {

            private final XRoadConfig xRoadConfig;

            public boolean enabled() {
                return xRoadConfig.value(ADDON_META_SERVICES_ENABLED);
            }
        }

        public static class ProxyAddonOpMonitorProperties {

            private final XRoadConfig xRoadConfig;
            private final OpMonitorConnectionProperties opMonitorConnectionProperties;
            private final OpMonitorBufferProperties opMonitorBufferProperties;

            public ProxyAddonOpMonitorProperties(XRoadConfig xRoadConfig) {
                this.xRoadConfig = xRoadConfig;
                this.opMonitorConnectionProperties = new OpMonitorConnectionProperties(xRoadConfig);
                this.opMonitorBufferProperties = new OpMonitorBufferProperties(xRoadConfig);
            }

            public boolean enabled() {
                return xRoadConfig.value(ADDON_OP_MONITOR_ENABLED);
            }

            public OpMonitorBufferProperties buffer() {
                return opMonitorBufferProperties;
            }

            public OpMonitorConnectionProperties connection() {
                return opMonitorConnectionProperties;
            }

        }

    }

}
