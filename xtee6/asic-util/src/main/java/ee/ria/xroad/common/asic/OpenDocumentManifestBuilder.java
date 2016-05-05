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

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * Builds a manifest XML (OpenDocument package manifest) file
 * with the specified entries.
 */
class OpenDocumentManifestBuilder {

    private final List<Entry> entries = new ArrayList<>();

    void addFile(String fileName, String mimeType) {
        entries.add(new Entry(fileName, mimeType));
    }

    String build() {
        StringBuilder fileEntries = new StringBuilder();

        for (Entry e : entries) {
            fileEntries.append(getFileEntry(e.getFileName(), e.getMediaType()));
        }

        return getBaseDocument(fileEntries.toString());
    }

    private static String getFileEntry(String fileName, String mediaType) {
        return "<manifest:file-entry "
            + "manifest:media-type=\"" + mediaType + "\" "
            + "manifest:full-path=\"" + fileName + "\" />\n";
    }

    private static String getBaseDocument(String fileEntries) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<manifest:manifest xmlns:manifest=\""
            + "urn:oasis:names:tc:opendocument:xmlns:manifest:1.0\">\n"
            + fileEntries + "</manifest:manifest>\n";
    }

    @Data
    private static class Entry {
        private final String fileName;
        private final String mediaType;
    }
}
