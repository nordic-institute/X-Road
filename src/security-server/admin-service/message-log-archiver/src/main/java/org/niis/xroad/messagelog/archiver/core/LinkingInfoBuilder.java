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
package org.niis.xroad.messagelog.archiver.core;

import ee.ria.xroad.common.crypto.Digests;
import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;
import ee.ria.xroad.common.util.EncoderUtils;

import lombok.Getter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Creates linking info for the log archive file.
 */
class LinkingInfoBuilder {
    private final DigestAlgorithm hashAlgoId;
    private DigestEntry lastArchive;

    @Getter
    private String lastDigest;

    private final List<DigestEntry> digestsForFiles = new ArrayList<>();

    LinkingInfoBuilder(DigestAlgorithm hashAlgoId) {
        this(hashAlgoId, DigestEntry.empty());
    }

    LinkingInfoBuilder(DigestAlgorithm hashAlgoId, DigestEntry lastArchive) {
        this.hashAlgoId = hashAlgoId;
        this.lastArchive = lastArchive;
        this.lastDigest = lastArchive.digest();
    }

    void addNextFile(String fileName, byte[] digest) throws IOException {
        String combinedDigests = lastDigest + EncoderUtils.encodeHex(digest);
        String currentDigest = hexDigest(combinedDigests.getBytes(StandardCharsets.UTF_8));

        digestsForFiles.add(new DigestEntry(currentDigest, fileName));

        lastDigest = currentDigest;
    }

    void reset(DigestEntry digestEntry) {
        this.lastArchive = digestEntry;
        this.lastDigest = lastArchive.digest();
        this.digestsForFiles.clear();
    }

    byte[] build() {
        StringBuilder builder = new StringBuilder();

        builder.append(getWritable(lastArchive.digest())).append(" ")
                .append(getWritable(lastArchive.fileName())).append(" ")
                .append(hashAlgoId.name()).append('\n');

        digestsForFiles.forEach(each ->
                builder.append(each.toLinkingInfoEntry()).append('\n')
        );

        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String hexDigest(byte[] fileBytes) throws IOException {
        return Digests.hexDigest(hashAlgoId, fileBytes);
    }

    private static String getWritable(String input) {
        return isBlank(input) ? "-" : input;
    }

}
