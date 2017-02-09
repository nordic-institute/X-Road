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
package ee.ria.xroad.common.asic;

import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Generates unique filenames for a set of ASiC containers.
 */
@RequiredArgsConstructor
public class AsicContainerNameGenerator {

    private static final int MAX_QUERY_LENGTH = 225;
    private final Supplier<String> randomGenerator;
    private final int maxAttempts;
    private final List<String> existingFilenames = new ArrayList<>();

    /**
     * Attempts to generate a unique filename with a random part and given
     * static parts, formatted as "{static#1}-...-{static#N}-{random}.asice".
     * @return the generated filename
     */
    public String getArchiveFilename(String queryId, String queryType) {
        String result = createFilenameWithRandom(queryId, queryType);

        int attempts = 0;
        while (existingFilenames.contains(result) && attempts++ < maxAttempts) {
            result = createFilenameWithRandom(queryId, queryType);
        }

        existingFilenames.add(result);
        return result;
    }

    public String createFilenameWithRandom(String queryId, String queryType) {
        String processedQueryId = AsicUtils.truncate(AsicUtils.escapeString(queryId), MAX_QUERY_LENGTH);
        return String.format("%s-%s", processedQueryId, queryType) + String.format("-%s.asice", randomGenerator.get());
    }

}
