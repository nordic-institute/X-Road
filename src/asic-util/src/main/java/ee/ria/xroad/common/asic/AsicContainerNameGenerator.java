/*
 * The MIT License
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
package ee.ria.xroad.common.asic;

import static ee.ria.xroad.common.asic.AsicUtils.escapeString;
import static ee.ria.xroad.common.asic.AsicUtils.truncate;

/**
 * Generates filenames for ASiC containers.
 *
 * The filename consists of query id, query type and a sequence value
 */
@SuppressWarnings("checkstyle:MagicNumber")
public class AsicContainerNameGenerator {

    public static final String TYPE_RESPONSE = "response";
    public static final String TYPE_REQUEST = "request";

    private static final int MAX_QUERY_LENGTH = 226;

    /**
     * Generates a filename for an asic container with the format "queryid-type-seq.asice"
     *
     * @return the generated filename
     */
    public String getArchiveFilename(String queryId, boolean response, long seq) {
        return truncate(escapeString(queryId), MAX_QUERY_LENGTH)
                + '-'
                + (response ? TYPE_RESPONSE : TYPE_REQUEST)
                + '-'
                + Long.toUnsignedString(seq, 32)
                + ".asice";
    }
}
