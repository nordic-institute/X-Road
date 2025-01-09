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

package org.niis.xroad.proxy;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithName;

import java.util.List;
import java.util.Optional;


@ConfigMapping(prefix = "xroad.proxy")
public interface ProxyProperties {

    ServerProperties server();

    ClientProxyProperties clientProxy();

    OcspResponderProperties ocspResponder();


    @WithName("verify-client-cert")
    boolean verifyClientCert();

    @WithName("database-properties")
    String databaseProperties();

    @WithName("server-port")
    int serverPort();

    @WithName("pool-enable-connection-reuse")
    boolean poolEnableConnectionReuse();

    @WithName("client-fastest-connecting-ssl-uri-cache-period")
    int clientFastestConnectingSslUriCachePeriod();

    @WithName("hsm-health-check-enabled")
    boolean hsmHealthCheckEnabled();

    @WithName("enforce-client-is-cert-validity-period-check")
    boolean enforceClientIsCertValidityPeriodCheck();

    @WithName("client-timeout")
    int clientTimeout();

    @WithName("client-use-fastest-connecting-ssl-socket-autoclose")
    boolean clientUseFastestConnectingSslSocketAutoclose();

    @WithName("backup-encryption-enabled")
    boolean backupEncryptionEnabled();

    @WithName("backup-encryption-keyids")
    Optional<List<String>> backupEncryptionKeyids();

    @ConfigMapping(prefix = "xroad.proxy.client-proxy")
    interface ClientProxyProperties {
        @WithName("connector-host")
        String connectorHost();

        @WithName("client-http-port")
        int clientHttpPort();

        @WithName("client-https-port")
        int clientHttpsPort();

        @WithName("jetty-configuration-file")
        String jettyConfigurationFile();

        @WithName("client-connector-initial-idle-time")
        int clientConnectorInitialIdleTime();

        @WithName("client-tls-protocols")
        List<String> clientTlsProtocols();

        @WithName("client-tls-ciphers")
        List<String> clientTlsCiphers();

        @WithName("jetty-max-header-size")
        int jettyMaxHeaderSize();

        @WithName("client-httpclient-so-linger")
        int clientHttpclientSoLinger();

        @WithName("client-httpclient-timeout")
        int clientHttpclientTimeout();

        @WithName("pool-total-max-connections")
        int poolTotalMaxConnections();

        @WithName("pool-total-default-max-connections-per-route")
        int poolTotalDefaultMaxConnectionsPerRoute();

        @WithName("pool-validate-connections-after-inactivity-of-millis")
        int poolValidateConnectionsAfterInactivityOfMillis();

        @WithName("client-idle-connection-monitor-interval")
        int clientIdleConnectionMonitorInterval();

        @WithName("client-idle-connection-monitor-timeout")
        int clientIdleConnectionMonitorTimeout();

        @WithName("client-use-idle-connection-monitor")
        boolean clientUseIdleConnectionMonitor();
    }

    @ConfigMapping(prefix = "xroad.proxy.server")
    interface ServerProperties {
        @WithName("listen-address")
        String listenAddress();

        @WithName("listen-port")
        int listenPort();

        @WithName("connector-initial-idle-time")
        int connectorInitialIdleTime();

        @WithName("support-clients-pooled-connections")
        boolean serverSupportClientsPooledConnections();

        @WithName("jetty-configuration-file")
        String jettyConfigurationFile();
    }

    @ConfigMapping(prefix = "xroad.proxy.ocsp-responder")
    interface OcspResponderProperties {
        @WithName("listen-address")
        String listenAddress();

        @WithName("port")
        int port();

        @WithName("client-connect-timeout")
        int clientConnectTimeout();

        @WithName("client-read-timeout")
        int clientReadTimeout();

        @WithName("jetty-configuration-file")
        String jettyConfigurationFile();
    }
}
