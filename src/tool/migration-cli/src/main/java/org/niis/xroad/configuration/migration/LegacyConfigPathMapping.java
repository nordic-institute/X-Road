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

import lombok.NoArgsConstructor;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Some properties have their paths changed. This class is used to map old paths to new paths.
 */
@SuppressWarnings("checkstyle:LineLength")
@NoArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public class LegacyConfigPathMapping {
    private static final Map<String, Set<String>> MAPPING = new HashMap<>();
    private static final Set<String> REMOVED_KEYS = new HashSet<>();

    static {
        addMapping("common.configuration-path", "common-global-conf.configuration-path");

        // configuration-client
        addMapping("proxy.configuration-anchor-file", "configuration-client.configuration-anchor-file");

        // proxy
        addMapping("proxy.ocsp-responder-port", "proxy.ocsp-responder.port");
        addMapping("proxy.ocsp-responder-listen-address", "proxy.ocsp-responder.listen-address");
        addMapping("proxy.ocsp-responder-client-connect-timeout", "proxy.ocsp-responder.client-connect-timeout");
        addMapping("proxy.ocsp-responder-client-read-timeout", "proxy.ocsp-responder.client-read-timeout");
        addMapping("proxy.jetty-ocsp-responder-configuration-file", "proxy.ocsp-responder.jetty-configuration-file");
        addMapping("proxy.server-listen-address", "proxy.server.listen-address");
        addMapping("proxy.server-listen-port", "proxy.server.listen-port");
        addMapping("proxy.server-connector-initial-idle-time", "proxy.server.connector-initial-idle-time");
        addMapping("proxy.server-support-clients-pooled-connections", "proxy.server.support-clients-pooled-connections");
        addMapping("proxy.server-min-supported-client-version", "proxy.server.min-supported-client-version");
        addMapping("proxy.jetty-serverproxy-configuration-file", "proxy.server.jetty-configuration-file");
        addMapping("proxy.connector-host", "proxy.client-proxy.connector-host");
        addMapping("proxy.client-http-port", "proxy.client-proxy.client-http-port");
        addMapping("proxy.client-https-port", "proxy.client-proxy.client-https-port");
        addMapping("proxy.jetty-clientproxy-configuration-file", "proxy.client-proxy.jetty-configuration-file");
        addMapping("proxy.client-connector-initial-idle-time", "proxy.client-proxy.client-connector-initial-idle-time");
        addMapping("proxy.client-tls-protocols", "proxy.client-proxy.client-tls-protocols");
        addMapping("proxy.client-tls-ciphers", "proxy.client-proxy.client-tls-ciphers");
        addMapping("proxy.client-httpclient-so-linger", "proxy.client-proxy.client-httpclient-so-linger");
        addMapping("proxy.client-timeout", "proxy.client-proxy.client-timeout");
        addMapping("proxy.pool-enable-connection-reuse", "proxy.client-proxy.pool-enable-connection-reuse");
        addMapping("proxy.client-httpclient-timeout", "proxy.client-proxy.client-httpclient-timeout");
        addMapping("proxy.pool-total-max-connections", "proxy.client-proxy.pool-total-max-connections");
        addMapping("proxy.pool-total-default-max-connections-per-route", "proxy.client-proxy.pool-total-default-max-connections-per-route");
        addMapping("proxy.pool-validate-connections-after-inactivity-of-millis", "proxy.client-proxy.pool-validate-connections-after-inactivity-of-millis");
        addMapping("proxy.client-idle-connection-monitor-interval", "proxy.client-proxy.client-idle-connection-monitor-interval");
        addMapping("proxy.client-idle-connection-monitor-timeout", "proxy.client-proxy.client-idle-connection-monitor-timeout");
        addMapping("proxy.client-use-idle-connection-monitor", "proxy.client-proxy.client-use-idle-connection-monitor");
        addMapping("proxy.client-fastest-connecting-ssl-uri-cache-period", "proxy.client-proxy.fastest-connecting-ssl-uri-cache-period");
        addMapping("proxy.client-use-fastest-connecting-ssl-socket-autoclose", "proxy.client-proxy.use-fastest-connecting-ssl-socket-autoclose");
        addMapping("proxy.ocsp-verifier-cache-period", "common-ocsp-verifier.cache-period");


        addMapping("proxy.server-conf-cache-period", "common-server-conf.cache-period");
        addMapping("proxy.server-conf-client-cache-size", "common-server-conf.client-cache-size");
        addMapping("proxy.server-conf-service-cache-size", "common-server-conf.service-cache-size");
        addMapping("proxy.server-conf-service-endpoints-cache-size", "common-server-conf.service-endpoints-cache-size");
        addMapping("proxy.server-conf-acl-cache-size", "common-server-conf.acl-cache-size");
        addMapping("proxy.grpc-port", "proxy.rpc.port");

        addMapping("proxy.backup-encryption-enabled", "backup-manager.backup-encryption-enabled");
        addMapping("proxy.backup-encryption-keyids", "backup-manager.backup-encryption-keyids");
        addMapping("configuration-client.proxy-configuration-backup-cron", "backup-manager.autobackup-cron-expression");

        // message-log-archiver
        addMapping("message-log.archive-max-filesize", "message-log-archiver.archive-max-filesize");
        addMapping("message-log.archive-path", "message-log-archiver.archive-path");
        addMapping("message-log.archive-transfer-command", "message-log-archiver.archive-transfer-command");
        addMapping("message-log.clean-interval", "message-log-archiver.clean-interval");
        addMapping("message-log.keep-records-for", "message-log-archiver.keep-records-for");
        addMapping("message-log.archive-transaction-batch", "message-log-archiver.archive-transaction-batch");
        addMapping("message-log.archive-transfer-command-parameters", "message-log-archiver.archive-transfer-command-parameters");

        // op-monitor
        addMapping("op-monitor-service.socket-timeout-seconds", "proxy.addon.op-monitor.connection.socket-timeout-seconds");
        addMapping("op-monitor-service.connection-timeout-seconds", "proxy.addon.op-monitor.connection.connection-timeout-seconds");

        addMapping("op-monitor.host", "proxy.addon.op-monitor.connection.host");
        addCopy("op-monitor.port", "proxy.addon.op-monitor.connection.port");
        addCopy("op-monitor.scheme", "proxy.addon.op-monitor.connection.scheme");
        addCopy("proxy.xroad-tls-ciphers", "op-monitor.xroad-tls-ciphers", "proxy.addon.op-monitor.connection.xroad-tls-ciphers");

        addMapping("op-monitor-buffer.size", "proxy.addon.op-monitor.buffer.size");
        addMapping("op-monitor-buffer.max-records-in-message", "proxy.addon.op-monitor.buffer.max-records-in-message");
        addMapping("op-monitor-buffer.sending-interval-seconds", "proxy.addon.op-monitor.buffer.sending-interval-seconds");
        addMapping("op-monitor-buffer.socket-timeout-seconds", "proxy.addon.op-monitor.buffer.socket-timeout-seconds");
        addMapping("op-monitor-buffer.connection-timeout-seconds", "proxy.addon.op-monitor.buffer.connection-timeout-seconds");

        // proxy-ui
        addMapping("proxy-ui-api.security-server-url", "proxy-ui-api.proxy-server-url");

        MAPPING.putAll(addDatabaseMapping("serverconf"));
        MAPPING.putAll(addDatabaseMapping("messagelog"));
        MAPPING.putAll(addDatabaseMapping("op-monitor"));

        // ----- removed keys ------------
        REMOVED_KEYS.add("proxy-ui-api.ssl-properties");
        REMOVED_KEYS.add("proxy.configuration-anchor-file");
        REMOVED_KEYS.add("proxy.database-properties");
        REMOVED_KEYS.add("signer.device-configuration-file");
    }

    private static void addMapping(String oldKey, String... newKeys) {
        MAPPING.put(oldKey, Set.of(newKeys));
    }

    private static void addCopy(String key, String... copyTo) {
        Set<String> copies = new HashSet<>(Arrays.asList(copyTo));
        copies.add(key);
        MAPPING.put(key, copies);
    }

    private static Map<String, Set<String>> addDatabaseMapping(String dbName) {
        Map<String, Set<String>> dbPropMapping = new HashMap<>();
        dbPropMapping.put(
                "%s.hibernate.connection.url".formatted(dbName),
                Set.of("db.%s.hibernate.connection.url".formatted(dbName)));

        dbPropMapping.put(
                "%s.hibernate.connection.username".formatted(dbName),
                Set.of("db.%s.hibernate.connection.username".formatted(dbName)));

        dbPropMapping.put(
                "%s.hibernate.connection.password".formatted(dbName),
                Set.of("db.%s.hibernate.connection.password".formatted(dbName)));

        dbPropMapping.put(
                "%s.hibernate.jdbc.use_streams_for_binary".formatted(dbName),
                Set.of("db.%s.hibernate.jdbc.use_streams_for_binary".formatted(dbName)));

        dbPropMapping.put(
                "%s.hibernate.connection.driver_class".formatted(dbName),
                Set.of("db.%s.hibernate.connection.driver_class".formatted(dbName)));

        dbPropMapping.put(
                "%s.hibernate.hikari.dataSource.currentSchema".formatted(dbName),
                Set.of("db.%s.hibernate.hikari.dataSource.currentSchema".formatted(dbName)));
        return dbPropMapping;
    }

    static Set<String> map(String oldPath) {
        return MAPPING.getOrDefault(oldPath, Set.of(oldPath));
    }

    static boolean shouldKeep(String key) {
        return !REMOVED_KEYS.contains(key);
    }

}
