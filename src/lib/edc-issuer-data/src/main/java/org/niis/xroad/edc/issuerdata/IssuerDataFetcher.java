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

import org.eclipse.edc.spi.result.Result;

import java.time.Instant;
import java.util.Optional;

/**
 * Downloads and reads one kind of data an issuer publishes under a key (for example a statuslist under its URL).
 *
 * @param <P> the parsed form of the data
 */
public interface IssuerDataFetcher<P> {

    /**
     * @return the kind that this fetcher's rows are stored under.
     */
    IssuerDataKind kind();

    /**
     * Downloads the raw data. A retryable failure means the issuer did not answer.
     */
    Result<Download> download(String key);

    /**
     * Reads downloaded data and verifies it, including its signature when it has one. Verification happens only here,
     * at download.
     */
    Result<P> parse(String key, String payload);

    /**
     * Reads data that was verified when it was downloaded and has been stored since. The default reads it like
     * {@link #parse}; a fetcher whose data has a signature overrides it to read the data without checking the
     * signature again.
     */
    default Result<P> parseStored(String key, String payload) {
        return parse(key, payload);
    }

    /**
     * @return the time the data itself says it stops being valid; empty when the data says nothing.
     */
    default Optional<Instant> ownExpiry(P parsed) {
        return Optional.empty();
    }

    /**
     * Raw downloaded data.
     *
     * @param payload       the response body
     * @param requestSentAt the time the successful HTTP request was sent
     */
    record Download(String payload, Instant requestSentAt) {
    }
}
