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
