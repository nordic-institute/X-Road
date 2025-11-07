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
package org.niis.xroad.messagelog.archiver.core.config;


import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;

import org.niis.xroad.common.messagelog.MessageLogArchivalProperties;
import org.niis.xroad.common.messagelog.MessageLogDatabaseEncryptionProperties;
import org.niis.xroad.common.messagelog.archive.GroupingStrategy;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record LogArchiverExecutionProperties(
        ArchiveEncryptionProperties archiveEncryption,
        DatabaseEncryptionProperties databaseEncryption,
        int cleanTransactionBatchSize,
        int cleanKeepRecordsFor,
        int archiveTransactionBatchSize,
        String archivePath,
        String archiveTransferCommand,
        DigestAlgorithm digestAlgorithm,
        long archiveMaxFilesize,
        String tmpDir
) {

    public Optional<String> archiveTransferCommandOpt() {
        return Optional.ofNullable(archiveTransferCommand);
    }

    /**
     * Archive encryption configuration
     */
    public record ArchiveEncryptionProperties(
            boolean enabled,
            Optional<String> defaultKeyId,
            GroupingStrategy groupingStrategy,
            Map<String, Set<String>> grouping
    ) implements MessageLogArchivalProperties {
    }

    /**
     * Database encryption configuration
     */
    public record DatabaseEncryptionProperties(
            boolean enabled,
            String messagelogKeystoreStr,
            String messagelogKeystorePasswordStr,
            String messagelogKeyIdStr
    ) implements MessageLogDatabaseEncryptionProperties {

        @Override
        public Optional<Path> messagelogKeystore() {
            return Optional.ofNullable(messagelogKeystoreStr).map(Paths::get);
        }

        @Override
        public Optional<char[]> messagelogKeystorePassword() {
            return Optional.ofNullable(messagelogKeystorePasswordStr).map(String::toCharArray);
        }

        @Override
        public Optional<String> messagelogKeyId() {
            return Optional.ofNullable(messagelogKeyIdStr);
        }
    }
}
