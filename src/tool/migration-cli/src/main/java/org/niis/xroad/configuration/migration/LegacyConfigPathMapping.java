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

package org.niis.xroad.configuration.migration;

import java.util.HashMap;
import java.util.Map;

/**
 * Some properties have their paths changed. This class is used to map old paths to new paths.
 */
@SuppressWarnings("checkstyle:LineLength")
public class LegacyConfigPathMapping {
    private static final Map<String, String> MAPPING = new HashMap<>();

    static {
        // configuration-client
        MAPPING.put("proxy.configuration-anchor-file", "configuration-client.configuration-anchor-file");

        // proxy
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

        MAPPING.put("proxy.server-conf-cache-period", "common.server-conf.cache-period");
        MAPPING.put("proxy.server-conf-client-cache-size", "common.server-conf.client-cache-size");
        MAPPING.put("proxy.server-conf-service-cache-size", "common.server-conf.service-cache-size");
        MAPPING.put("proxy.server-conf-acl-cache-size", "common.server-conf.acl-cache-size");

        MAPPING.put("proxy-ui-api.wsdl-validator-command", "proxy-ui-api.addon.wsdl-validator.command");

        // message-log-archiver
        MAPPING.put("xroad.message-log.archive-max-filesize", "xroad-message-log-archiver.archive-max-filesize");
        MAPPING.put("xroad.message-log.archive-path", "xroad-message-log-archiver.archive-path");
        MAPPING.put("xroad.message-log.archive-transfer-command", "xroad-message-log-archiver.archive-transfer-command");
        MAPPING.put("xroad.message-log.clean-interval", "xroad-message-log-archiver.clean-interval");
        MAPPING.put("xroad.message-log.keep-records-for", "xroad-message-log-archiver.keep-records-for");
        MAPPING.put("xroad.message-log.archive-transaction-batch", "xroad-message-log-archiver.archive-transaction-batch");
        MAPPING.put("xroad.message-log.archive-transfer-command-parameters", "xroad-message-log-archiver.archive-transfer-command-parameters");

    }

    String map(String oldPath) {
        return MAPPING.getOrDefault(oldPath, oldPath);
    }
}
