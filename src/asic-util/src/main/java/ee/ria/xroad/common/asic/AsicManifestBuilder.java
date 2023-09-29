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

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * Builds an ASiC manifest XML file (according to ASiC XML schema) with the specified entries.
 */
class AsicManifestBuilder {

    private final List<UriWithMimeTypeAndDigestInfo> dataObjectReferences = new ArrayList<>();

    private UriWithMimeType sigReference;

    void setSigReference(String uri, String mimeType) {
        sigReference = new UriWithMimeType(uri, mimeType);
    }

    void addDataObjectReference(String uri, String mimeType, String digestMethod, String digestValue) {
        dataObjectReferences.add(new UriWithMimeTypeAndDigestInfo(uri, mimeType, digestMethod, digestValue));
    }

    String build() {
        String sigRef = getSigReference(sigReference.getUri(), sigReference.getMimeType());

        StringBuilder dataObjectRefs = new StringBuilder();

        for (UriWithMimeTypeAndDigestInfo u : dataObjectReferences) {
            dataObjectRefs.append(getDataObjectReference(u.getUri(), u.getMimeType(), u.getDigestMethod(),
                    u.getDigestValue()));
        }

        return getBaseDocument(sigRef, dataObjectRefs.toString());
    }

    private static String getSigReference(String uri, String mimeType) {
        return "<asic:SigReference URI=\"" + uri + "\"" + (mimeType != null ? " MimeType=\"" + mimeType + "\"" : "")
                + "/>";
    }

    private static String getDataObjectReference(String uri, String mimeType, String digestMethod, String digestValue) {
        return "<asic:DataObjectReference URI=\"" + uri + "\""
                + (mimeType != null ? " MimeType=\"" + mimeType + "\"" : "") + ">\n"
                + "<ds:DigestMethod Algorithm=\"" + digestMethod + "\" />\n"
                + "<ds:DigestValue>" + digestValue + "</ds:DigestValue>\n"
                + "</asic:DataObjectReference>";
    }

    private static String getBaseDocument(String sigReference, String dataObjectReference) {
        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n"
                + "<asic:ASiCManifest xmlns:asic=\"http://uri.etsi.org/2918/v1.2.1#\" "
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
