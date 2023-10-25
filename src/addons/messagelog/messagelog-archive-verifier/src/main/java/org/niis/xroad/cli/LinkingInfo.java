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
package org.niis.xroad.cli;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@Getter
class LinkingInfo {
    private String prevDigest;
    private String prevArchFile;
    private String digestAlgoId;
    private final Map<String, String> contentLines;

    LinkingInfo(String[] fileLines) {
        contentLines = new HashMap<>();
        parse(fileLines);
    }

    public DigestCalculator digestCalculator() {
        return new DigestCalculator(digestAlgoId);
    }

    public String digestForFile(String fileName) {
        return contentLines.get(fileName);
    }

    public Set<String> fileNames() {
        return contentLines.keySet();
    }

    private void parse(String[] fileLines) {
        String[] firstLineParts = fileLines[0].split("\\s+");
        prevDigest = firstLineParts[0].equals("-") ? "" : firstLineParts[0];
        prevArchFile = firstLineParts[1].equals("-") ? "" : firstLineParts[1];
        digestAlgoId = firstLineParts[2];

        for (int i = 1; i < fileLines.length; i++) {
            String line = fileLines[i];
            String[] lineParts = line.split("\\s+");
            if (lineParts.length != 2) {
                throw new IllegalArgumentException("Invalid log archive format");
            }
            contentLines.put(lineParts[1], lineParts[0]);
        }
    }

}
