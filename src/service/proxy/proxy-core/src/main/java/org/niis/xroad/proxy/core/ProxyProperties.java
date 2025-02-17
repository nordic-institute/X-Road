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

package org.niis.xroad.proxy.core;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.util.List;
import java.util.Optional;

@ConfigMapping(prefix = "xroad.proxy")
public interface ProxyProperties {

    ServerProperties server();
    ClientProxyProperties clientProxy();
    OcspResponderProperties ocspResponder();
    ProxyAddonProperties addOn();

    @WithName("health-check-port")
    @WithDefault("0")
    int healthCheckPort();

    @WithName("backup-encryption-enabled")
    @WithDefault("false")
    boolean backupEncryptionEnabled();

    @WithName("backup-encryption-keyids")
    Optional<List<String>> backupEncryptionKeyids();

    @WithName("health-check-interface")
    @WithDefault("0.0.0.0")
    String healthCheckInterface();

    @WithName("hsm-health-check-enabled")
    @WithDefault("false")
    boolean hsmHealthCheckEnabled();

    @ConfigMapping(prefix = "xroad.proxy.client-proxy")
    interface ClientProxyProperties {
        @WithName("connector-host")
        @WithDefault("0.0.0.0")
        String connectorHost();

        @WithName("client-http-port")
        @WithDefault("8080")
        int clientHttpPort();

        @WithName("client-https-port")
        @WithDefault("8443")
        int clientHttpsPort();

        @WithName("jetty-configuration-file")
        @WithDefault("classpath:jetty/clientproxy.xml")
        String jettyConfigurationFile();

        @WithName("client-connector-initial-idle-time")
        @WithDefault("30000")
        int clientConnectorInitialIdleTime();

        @WithName("client-httpclient-so-linger")
        @WithDefault("-1")
        int clientHttpclientSoLinger();

        @WithName("client-httpclient-timeout")
        @WithDefault("0")
        int clientHttpclientTimeout();

        @WithName("pool-total-max-connections")
        @WithDefault("10000")
        int poolTotalMaxConnections();

        @WithName("pool-total-default-max-connections-per-route")
        @WithDefault("2500")
        int poolTotalDefaultMaxConnectionsPerRoute();

        @WithName("pool-validate-connections-after-inactivity-of-millis")
        @WithDefault("2000")
        int poolValidateConnectionsAfterInactivityOfMillis();

        @WithName("client-idle-connection-monitor-interval")
        @WithDefault("30000")
        int clientIdleConnectionMonitorInterval();

        @WithName("client-idle-connection-monitor-timeout")
        @WithDefault("60000")
        int clientIdleConnectionMonitorTimeout();

        @WithName("client-use-idle-connection-monitor")
        @WithDefault("true")
        boolean clientUseIdleConnectionMonitor();
    }

    @ConfigMapping(prefix = "xroad.proxy.server")
    interface ServerProperties {
        @WithName("listen-address")
        @WithDefault("0.0.0.0")
        String listenAddress();

        @WithName("listen-port")
        @WithDefault("5500")
        int listenPort();

        @WithName("connector-initial-idle-time")
        @WithDefault("30000")
        int connectorInitialIdleTime();

        @WithName("jetty-configuration-file")
        @WithDefault("classpath:jetty/serverproxy.xml")
        String jettyConfigurationFile();

        @WithName("support-clients-pooled-connections")
        @WithDefault("false")
        boolean serverSupportClientsPooledConnections();

    }

    @ConfigMapping(prefix = "xroad.proxy.ocsp-responder")
    interface OcspResponderProperties {
        @WithName("listen-address")
        @WithDefault("0.0.0.0")
        String listenAddress();

        @WithName("port")
        @WithDefault("5577")
        int port();

        @WithName("client-connect-timeout")
        @WithDefault("20000")
        int clientConnectTimeout();

        @WithName("client-read-timeout")
        @WithDefault("30000")
        int clientReadTimeout();

        @WithName("jetty-configuration-file")
        @WithDefault("classpath:jetty/ocsp-responder.xml")
        String jettyConfigurationFile();
    }

    @ConfigMapping(prefix = "xroad.proxy.addon")
    interface ProxyAddonProperties {
        @WithName("proxy-monitor")
        ProxyAddonProxyMonitorProperties proxyMonitor();

        @WithName("meta-services")
        ProxyAddonMetaservicesProperties metaservices();

        @WithName("message-log")
        ProxyAddonMessageLogProperties messageLog();

        @WithName("op-monitor")
        ProxyAddonOpMonitorProperties opMonitor();

        interface ProxyAddonProxyMonitorProperties {
            @WithName("enabled")
            @WithDefault("true")
            boolean enabled();
        }

        interface ProxyAddonMessageLogProperties {
            @WithName("enabled")
            @WithDefault("true")
            boolean enabled();
        }

        interface ProxyAddonMetaservicesProperties {
            @WithName("enabled")
            @WithDefault("true")
            boolean enabled();
        }

        interface ProxyAddonOpMonitorProperties {
            @WithName("enabled")
            @WithDefault("false")
            boolean enabled();
        }

    }
}
