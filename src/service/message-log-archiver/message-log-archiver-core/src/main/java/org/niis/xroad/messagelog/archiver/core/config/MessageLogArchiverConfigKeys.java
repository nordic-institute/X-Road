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

import org.niis.xroad.common.properties.config.ConfigKey;
import org.niis.xroad.common.properties.config.ConfigKeyProvider;
import org.niis.xroad.common.properties.config.Scope;

/** DSL config keys for the {@code xroad.message-log-archiver} namespace. */
@SuppressWarnings("checkstyle:MagicNumber")
public final class MessageLogArchiverConfigKeys implements ConfigKeyProvider {

    private static final Scope ARCHIVER = Scope.of("xroad.message-log-archiver", "message-log-archiver");

    private static final MessageLogArchiverConfigKeys INSTANCE = new MessageLogArchiverConfigKeys();

    /** {@code xroad.message-log-archiver.clean-transaction-batch-size} */
    static final ConfigKey<Integer> CLEAN_TRANSACTION_BATCH_SIZE = ARCHIVER
            .integer("clean-transaction-batch-size")
            .withDefaultValue(10000)
            .build();

    /** {@code xroad.message-log-archiver.clean-keep-records-for} */
    static final ConfigKey<Integer> CLEAN_KEEP_RECORDS_FOR = ARCHIVER
            .integer("clean-keep-records-for")
            .withDefaultValue(30)
            .build();

    /** {@code xroad.message-log-archiver.max-filesize} */
    static final ConfigKey<Integer> MAX_FILESIZE = ARCHIVER
            .integer("max-filesize")
            .withDefaultValue(33554432)
            .build();

    /** {@code xroad.message-log-archiver.transaction-batch-size} */
    static final ConfigKey<Integer> TRANSACTION_BATCH_SIZE = ARCHIVER
            .integer("transaction-batch-size")
            .withDefaultValue(10000)
            .build();

    /** {@code xroad.message-log-archiver.archive-path} */
    static final ConfigKey<String> ARCHIVE_PATH = ARCHIVER
            .string("archive-path")
            .withDefaultValue("/var/lib/xroad")
            .build();

    /** {@code xroad.message-log-archiver.archive-transfer-command} — optional, no default. */
    static final ConfigKey<String> ARCHIVE_TRANSFER_COMMAND = ARCHIVER
            .string("archive-transfer-command")
            .build();

    /** {@code xroad.message-log-archiver.hash-algo-id} */
    static final ConfigKey<String> HASH_ALGO_ID = ARCHIVER
            .string("hash-algo-id")
            .withDefaultValue("SHA-512")
            .build();

    private MessageLogArchiverConfigKeys() {
    }

    /** @return the provider singleton. */
    public static MessageLogArchiverConfigKeys instance() {
        return INSTANCE;
    }

    @Override
    public Scope scope() {
        return ARCHIVER;
    }
}
