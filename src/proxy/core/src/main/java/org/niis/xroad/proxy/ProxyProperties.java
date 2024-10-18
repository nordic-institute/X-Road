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

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.rpc.RpcServerProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@Getter
@RequiredArgsConstructor
@ConfigurationProperties(prefix = "xroad.proxy")
public class ProxyProperties {
    private final ServerProperties server;
    private final RpcServerProperties grpcServer;
    private final ClientProxyProperties clientProxy;
    private final OcspResponderProperties ocspResponder;

    private final boolean verifyClientCert;  // verify-client-cert: true
    private final String databaseProperties;  // database-properties: /etc/xroad/db.properties
    private final int serverPort;  // server-port: 5500
    private final boolean poolEnableConnectionReuse;  // pool-enable-connection-reuse: false

    private final int clientFastestConnectingSslUriCachePeriod;  // client-fastest-connecting-ssl-uri-cache-period: 3600
    private final boolean hsmHealthCheckEnabled; // hsm-health-check-enabled: false
    private final boolean enforceClientIsCertValidityPeriodCheck;  // enforce-client-is-cert-validity-period-check: false
    // todo: should go to ClientProxyProperties?
    private final int clientTimeout;  // client-timeout: 30000
    private final boolean clientUseFastestConnectingSslSocketAutoclose;  // client-use-fastest-connecting-ssl-socket-autoclose: true
    private final boolean backupEncryptionEnabled;  // backup-encryption-enabled: false
    private final List<String> backupEncryptionKeyids; // backup-encryption-keyids:[]

    public record ClientProxyProperties(
            String connectorHost,  // connector-host: 0.0.0.0
            int clientHttpPort,  // client-http-port: 8080
            int clientHttpsPort,  // client-https-port: 8443
            String jettyConfigurationFile,  // jetty-clientproxy-configuration-file: /etc/xroad/jetty/clientproxy.xml
            int clientConnectorInitialIdleTime, // client-connector-initial-idle-time: 30000
            String[] clientTlsProtocols, //client-tls-protocols: "TLSv1.2"
            String[] clientTlsCiphers, //client-tls-ciphers: <long list>
            int jettyMaxHeaderSize,
            int clientHttpclientSoLinger,  // client-httpclient-so-linger: -1
            int clientHttpclientTimeout,  // client-httpclient-timeout: 0
            int poolTotalMaxConnections, //pool-total-max-connections 10000
            int poolTotalDefaultMaxConnectionsPerRoute, //pool-total-default-max-connections-per-route: 2500
            int poolValidateConnectionsAfterInactivityOfMillis, // pool-validate-connections-after-inactivity-of-millis: 2000
            int clientIdleConnectionMonitorInterval, //client-idle-connection-monitor-interval: 30000
            int clientIdleConnectionMonitorTimeout, // client-idle-connection-monitor-timeout: 60000
            boolean clientUseIdleConnectionMonitor  // client-use-idle-connection-monitor: true
    ) {
    }

    public record ServerProperties(
            String listenAddress,  // server-listen-address: 0.0.0.0
            int listenPort,  // server-listen-port: 5500
            int connectorInitialIdleTime,   //server-connector-initial-idle-time 30000
            boolean serverSupportClientsPooledConnections, //server-support-clients-pooled-connections: false
            String jettyConfigurationFile  // jetty-serverproxy-configuration-file: /etc/xroad/jetty/serverproxy.xml
    ) {
    }

    public record OcspResponderProperties(
            String listenAddress,  // ocsp-responder-listen-address: 0.0.0.0
            int port,  // ocsp-responder-port: 5577
            int clientConnectTimeout,  // ocsp-responder-client-connect-timeout: 20000
            int clientReadTimeout,  // ocsp-responder-client-read-timeout: 30000
            String jettyConfigurationFile //jetty-ocsp-responder-configuration-file:/etc/xroad/jetty/ocsp-responder.xml
    ) {
    }
}
