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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class MessageArchiveExtractor {
    private final List<AsicContainer> asicContainers = new ArrayList<>();
    private final String archiveFile;
    private LinkingInfo linkingInfo;
    private String previousDigest;

    public MessageArchiveExtractor(final String archiveFile) {
        this.archiveFile = archiveFile;
        this.linkingInfo = null;
        this.previousDigest = null;
    }

    public ExtractionResult extract() {
        try (ZipFile zipFile = new ZipFile(archiveFile)) {
            extractLinkingInfo(zipFile);

            zipFile.stream()
                    .filter(this::isAsic)
                    .forEach(entry -> processAsic(zipFile, entry));
        } catch (IOException | InvalidLogArchiveException e) {
            throw new InvalidLogArchiveException("File '" + archiveFile + "' cannot be extracted - it may not be a valid zip file.", e);
        }

        return new ExtractionResult(asicContainers, linkingInfo, previousDigest);
    }

    private void processAsic(ZipFile zipFile, ZipEntry entry) {
        String fileName = entry.getName();
        String digest = linkingInfo.digestCalculator().chainDigest(readFile(zipFile, entry), previousDigest);

        asicContainers.add(new AsicContainer(fileName, digest));
        previousDigest = digest;
    }

    private boolean isAsic(ZipEntry entry) {
        return entry.getName().endsWith(".asice");
    }

    private void extractLinkingInfo(ZipFile zipFile) throws InvalidLogArchiveException {
        ZipEntry linkingInfoEntry = zipFile.getEntry("linkinginfo");

        if (linkingInfoEntry == null) {
            throw new InvalidLogArchiveException("Linking info not found in archive file: " + zipFile.getName());
        }

        String[] linkingInfoLines = new String(readFile(zipFile, linkingInfoEntry)).split("\\r?\\n");

        linkingInfo = new LinkingInfo(linkingInfoLines);
        previousDigest = linkingInfo.getPrevDigest();
    }

    private byte[] readFile(ZipFile zipFile, ZipEntry entry) {
        try {
            return zipFile.getInputStream(entry).readAllBytes();
        } catch (IOException e) {
            throw new InvalidLogArchiveException("Failed to extract content from archive file: " + zipFile.getName(), e);
        }
    }

    public static class InvalidLogArchiveException extends RuntimeException {
        public InvalidLogArchiveException(String message, Throwable throwable) {
            super(message, throwable);
        }

        public InvalidLogArchiveException(String message) {
            super(message);
        }
    }


    public record ExtractionResult(List<AsicContainer> asicContainers,
                                   LinkingInfo linkingInfo,
                                   String lastDigest) {
    }

    public record AsicContainer(String name, String digest) {
    }
}
