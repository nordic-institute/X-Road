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

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import lombok.SneakyThrows;

import ee.ria.xroad.common.util.CryptoUtils;

import static org.apache.commons.lang3.StringUtils.isBlank;

/**
 * Creates linking info for the log archive file.
 */
class LinkingInfoBuilder {
    private final String hashAlgoId;
    private final LogArchiveBase archiveBase;

    private DigestEntry lastArchive;

    private String lastDigest;

    @Getter
    private String createdArchiveLastDigest;

    @Getter
    private List<DigestEntry> digestsForFiles = new ArrayList<>();

    LinkingInfoBuilder(String hashAlgoId, LogArchiveBase archiveBase) {
        this.hashAlgoId = hashAlgoId;
        this.archiveBase = archiveBase;

        updateLastArchive();

        this.lastDigest = lastArchive.getDigest();
    }

    void addNextFile(String fileName, byte[] fileBytes) {
        String combinedDigests = lastDigest + hexDigest(fileBytes);
        String currentDigest =
                hexDigest(combinedDigests.getBytes(StandardCharsets.UTF_8));

        digestsForFiles.add(new DigestEntry(currentDigest, fileName));

        lastDigest = currentDigest;
    }

    void afterArchiveCreated() {
        digestsForFiles = new ArrayList<>();
        createdArchiveLastDigest = lastDigest;
        lastDigest = lastArchive.getDigest();
    }

    void afterArchiveSaved() {
        updateLastArchive();
    }

    byte[] build() {
        StringBuilder builder = new StringBuilder();

        builder.append(getWritable(lastArchive.getDigest())).append(" ")
                .append(getWritable(lastArchive.getFileName())).append(" ")
                .append(hashAlgoId).append('\n');

        digestsForFiles.forEach(each ->
            builder.append(each.toLinkingInfoEntry()).append('\n')
        );

        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }


    @SneakyThrows
    private String hexDigest(byte[] fileBytes) {
        return CryptoUtils.hexDigest(hashAlgoId, fileBytes);
    }

    private static String getWritable(String input) {
        return isBlank(input) ? "-" : input;
    }

    @SneakyThrows
    private void updateLastArchive() {
        this.lastArchive = archiveBase.loadLastArchive();
    }
}
