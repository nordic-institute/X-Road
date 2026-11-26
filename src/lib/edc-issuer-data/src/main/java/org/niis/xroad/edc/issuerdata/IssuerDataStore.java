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

package org.niis.xroad.edc.issuerdata;

import org.eclipse.edc.spi.result.StoreResult;

import java.time.Instant;

/**
 * Persistent last good copies of issuer data, shared by all nodes that use the same database.
 */
public interface IssuerDataStore {

    /**
     * Stores the data unless the row already holds a download that is not older. A skipped write is a success.
     * Never throws.
     */
    StoreResult<Void> write(IssuerDataKind kind, String key, StoredIssuerData data);

    /**
     * Reads the row. Never throws; {@code notFound} means the row does not exist.
     */
    StoreResult<StoredIssuerData> read(IssuerDataKind kind, String key);

    /**
     * Deletes every row whose deadline is before {@code now}, whatever its content. Rows another node is replacing
     * at the same moment are left to that node. Never throws.
     *
     * @return the number of deleted rows
     */
    StoreResult<Integer> deleteExpired(Instant now);
}
