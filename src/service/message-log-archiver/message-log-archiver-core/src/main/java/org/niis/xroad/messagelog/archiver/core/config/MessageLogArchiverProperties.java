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

import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.properties.config.XRoadConfig;

import java.util.Optional;

import static org.niis.xroad.messagelog.archiver.core.config.MessageLogArchiverConfigKeys.ARCHIVE_PATH;
import static org.niis.xroad.messagelog.archiver.core.config.MessageLogArchiverConfigKeys.ARCHIVE_TRANSFER_COMMAND;
import static org.niis.xroad.messagelog.archiver.core.config.MessageLogArchiverConfigKeys.CLEAN_KEEP_RECORDS_FOR;
import static org.niis.xroad.messagelog.archiver.core.config.MessageLogArchiverConfigKeys.CLEAN_TRANSACTION_BATCH_SIZE;
import static org.niis.xroad.messagelog.archiver.core.config.MessageLogArchiverConfigKeys.HASH_ALGO_ID;
import static org.niis.xroad.messagelog.archiver.core.config.MessageLogArchiverConfigKeys.MAX_FILESIZE;
import static org.niis.xroad.messagelog.archiver.core.config.MessageLogArchiverConfigKeys.TRANSACTION_BATCH_SIZE;

/** Message-log archiver configuration ({@code xroad.message-log-archiver.*}). */
@RequiredArgsConstructor
public class MessageLogArchiverProperties {

    private final XRoadConfig xRoadConfig;

    /** @return clean transaction batch size */
    public int cleanTransactionBatchSize() {
        return xRoadConfig.value(CLEAN_TRANSACTION_BATCH_SIZE);
    }

    /** @return number of days to keep records */
    public int cleanKeepRecordsFor() {
        return xRoadConfig.value(CLEAN_KEEP_RECORDS_FOR);
    }

    /** @return maximum archive file size in bytes */
    public int maxFilesize() {
        return xRoadConfig.value(MAX_FILESIZE);
    }

    /** @return transaction batch size for archiving */
    public int transactionBatchSize() {
        return xRoadConfig.value(TRANSACTION_BATCH_SIZE);
    }

    /** @return path to the archive directory */
    public String archivePath() {
        return xRoadConfig.value(ARCHIVE_PATH);
    }

    /** @return optional archive transfer command */
    public Optional<String> archiveTransferCommand() {
        return Optional.ofNullable(xRoadConfig.value(ARCHIVE_TRANSFER_COMMAND));
    }

    /** @return hash algorithm identifier string */
    public String hashAlgoIdStr() {
        return xRoadConfig.value(HASH_ALGO_ID);
    }

    /** @return digest algorithm derived from the hash algo ID string */
    public DigestAlgorithm hashAlg() {
        return Optional.ofNullable(hashAlgoIdStr())
                .map(DigestAlgorithm::ofName)
                .orElse(DigestAlgorithm.SHA512);
    }
}
