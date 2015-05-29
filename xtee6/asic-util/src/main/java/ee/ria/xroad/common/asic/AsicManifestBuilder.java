package ee.ria.xroad.common.asic;

import java.util.ArrayList;
import java.util.List;

import lombok.Data;

/**
 * Builds an ASiC manifest XML file (according to ASiC XML schema)
 * with the specified entries.
 */
class AsicManifestBuilder {

    private final List<UriWithMimeTypeAndDigestInfo> dataObjectReferences =
            new ArrayList<>();

    private UriWithMimeType sigReference;

    void setSigReference(String uri, String mimeType) {
        sigReference = new UriWithMimeType(uri, mimeType);
    }

    void addDataObjectReference(String uri, String mimeType,
            String digestMethod, String digestValue) {
        dataObjectReferences.add(new UriWithMimeTypeAndDigestInfo(uri,
                mimeType, digestMethod, digestValue));
    }

    String build() {
        String sigRef = getSigReference(sigReference.getUri(),
                sigReference.getMimeType());

        StringBuilder dataObjectRefs = new StringBuilder();
        for (UriWithMimeTypeAndDigestInfo u : dataObjectReferences) {
            dataObjectRefs.append(
                    getDataObjectReference(u.getUri(), u.getMimeType(),
                            u.getDigestMethod(), u.getDigestValue()));
        }

        return getBaseDocument(sigRef, dataObjectRefs.toString());
    }

    private static String getSigReference(String uri, String mimeType) {
        return "<asic:SigReference URI=\"" + uri + "\""
           + (mimeType != null ? " MimeType=\"" + mimeType + "\"" : "") + "/>";
    }

    private static String getDataObjectReference(String uri, String mimeType,
            String digestMethod, String digestValue) {
        return "<asic:DataObjectReference URI=\"" + uri + "\""
            + (mimeType != null ? " MimeType=\"" + mimeType + "\"" : "") + ">\n"
            + "<ds:DigestMethod Algorithm=\"" + digestMethod + "\" />\n"
            + "<ds:DigestValue>" + digestValue + "</ds:DigestValue>\n"
            + "</asic:DataObjectReference>";
    }

    private static String getBaseDocument(String sigReference,
            String dataObjectReference) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
            + "<asic:ASiCManifest "
            + "xmlns:asic=\"http://uri.etsi.org/2918/v1.2.1#\" "
            + "xmlns:ds=\"http://www.w3.org/2000/09/xmldsig#\">\n"
            + sigReference + "\n" + dataObjectReference + "\n"
            + "</asic:ASiCManifest>";
    }

    @Data
    private static class UriWithMimeType {
        private final String uri;
        private final String mimeType;
    }

    @Data
    private static class UriWithMimeTypeAndDigestInfo {
        private final String uri;
        private final String mimeType;
        private final String digestMethod;
        private final String digestValue;
    }
}
