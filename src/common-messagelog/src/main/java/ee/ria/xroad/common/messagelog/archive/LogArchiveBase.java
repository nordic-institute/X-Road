/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package ee.ria.xroad.common.messagelog.archive;

import ee.ria.xroad.common.messagelog.LogRecord;

/**
 * Interface for accessing message log database for archive-related purposes.
 */
public interface LogArchiveBase {
    /**
     * Saves digest entry corresponding to created log archive.
     *
     * @param lastArchive metadata of last archived entry.
     * @throws Exception if archiving fails.
     */
    void markArchiveCreated(DigestEntry lastArchive) throws Exception;

    /**
     * Marks log record (either message or timestamp) as archived.
     *
     * @param logRecord the log record to be marked as archived.
     * @throws Exception if marking records as archived fails.
     */
    void markRecordArchived(final LogRecord logRecord) throws Exception;

    /**
     * Returns metadata of last archived entry.
     *
     * @return digest and file name of last archive.
     * @throws Exception if loading last archive fails.
     */
    DigestEntry loadLastArchive() throws Exception;
}
