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

import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;
import org.niis.xroad.common.messagelog.MessageLogArchivalProperties;
import org.niis.xroad.common.messagelog.MessageLogDatabaseEncryptionProperties;
import org.niis.xroad.common.messagelog.archive.GroupingStrategy;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@ConfigMapping(prefix = "xroad.proxy.message-log")
public interface ProxyMessageLogProperties {
    @WithName("enabled")
    @WithDefault("true")
    boolean enabled();

    @WithName("archiver")
    ArchiverProperties archiver();

    @WithName("database-encryption")
    DatabaseEncryptionProperties databaseEncryption();

    @WithName("timestamper")
    TimestamperProperties timestamper();

    @WithName("message-body-logging")
    @WithDefault("true")
    boolean messageBodyLogging();

    @WithName("max-loggable-message-body-size")
    @WithDefault("10485760")
    long maxLoggableMessageBodySize();

    @WithName("truncated-body-allowed")
    @WithDefault("false")
    boolean truncatedBodyAllowed();

    @WithName("hash-algo-id")
    @WithDefault("SHA-512")
    String hashAlgoIdStr();

    default DigestAlgorithm hashAlg() {
        return Optional.ofNullable(hashAlgoIdStr())
                .map(DigestAlgorithm::ofName)
                .orElse(DigestAlgorithm.SHA512);
    }

    interface DatabaseEncryptionProperties extends MessageLogDatabaseEncryptionProperties {
        @WithName("enabled")
        @WithDefault("false")
        boolean enabled();

        @Deprecated
        @WithName("messagelog-keystore")
        Optional<String> messagelogKeystoreStr();

        @Deprecated
        @WithName("messagelog-keystore-password")
        Optional<String> messagelogKeystorePasswordStr();

        @Deprecated
        @WithName("messagelog-key-id")
        Optional<String> messagelogKeyId();

        @Override
        default Optional<Path> messagelogKeystore() {
            return messagelogKeystoreStr().map(Paths::get);
        }

        @Override
        default Optional<char[]> messagelogKeystorePassword() {
            return messagelogKeystorePasswordStr().map(String::toCharArray);
        }
    }

    interface ArchiverProperties extends MessageLogArchivalProperties {
        @WithName("enabled")
        @WithDefault("true")
        boolean enabled();

        @WithName("encryption-enabled")
        @WithDefault("false")
        boolean encryptionEnabled();

        @WithName("archive-interval")
        @WithDefault("0 0 0/6 1/1 * ?")
        String archiveInterval();

        @WithName("clean-interval")
        @WithDefault("0 0 0/12 1/1 * ?")
        String cleanInterval();

        @WithName("clean-transaction-batch-size")
        @WithDefault("10000")
        int cleanTransactionBatchSize();

        @WithName("clean-keep-records-for")
        @WithDefault("30")
        int cleanKeepRecordsFor();

        @WithName("max-filesize")
        @WithDefault("33554432")
        int maxFilesize();

        @WithName("default-key-id")
        Optional<String> defaultKeyId();

        @WithName("grouping-strategy")
        @WithDefault("NONE")
        GroupingStrategy groupingStrategy();

        @WithName("grouping-keys")
        @WithDefault("")
        Map<String, Set<String>> grouping();

        @WithName("transaction-batch-size")
        @WithDefault("10000")
        int transactionBatchSize();

        @WithName("archive-path")
        @WithDefault("/var/lib/xroad")
        String archivePath();

        @WithName("archive-transfer-command")
        Optional<String> archiveTransferCommand();
    }

    interface TimestamperProperties {
        @WithName("client-connect-timeout")
        @WithDefault("20000")
        int clientConnectTimeout();

        @WithName("client-read-timeout")
        @WithDefault("60000")
        int clientReadTimeout();

        @WithName("timestamp-immediately")
        @WithDefault("false")
        boolean timestampImmediately();

        @WithName("records-limit")
        @WithDefault("10000")
        int recordsLimit();

        @WithName("retry-delay")
        @WithDefault("60")
        int retryDelay();

        @WithName("acceptable-timestamp-failure-period")
        @WithDefault("14400")
        int acceptableTimestampFailurePeriod();

    }
}
