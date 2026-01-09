/*
 * The MIT License
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.proxy.core.serverproxy;

import lombok.experimental.UtilityClass;

import javax.xml.stream.XMLStreamException;

import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;

@UtilityClass
public class RequestHashInjector {

    private static final String REQUEST_HASH_TEMPLATE = "<%s:requestHash algorithmId=\"%s\">%s</%s:requestHash>";

    public static byte[] inject(
            String hashAlgorithm,
            String hash,
            byte[] xmlBytes,
            String charset,
            int queryIdStart, int queryIdEnd, int reqHashStart, int reqHashEnd,
            String xrdPrefix) throws XMLStreamException {
        try {
            var xml = new BomIgnore(Charset.forName(charset).decode(ByteBuffer.wrap(xmlBytes)));

            final var modified = new StringBuilder();
            final var indent = new StringBuilder();

            for (int i = 0; i < xml.length(); i++) {
                if (i >= reqHashStart && i < reqHashEnd) {
                    continue;
                } else if (i == reqHashEnd) {
                    while (Character.isWhitespace(modified.charAt(modified.length() - 1))) {
                        modified.deleteCharAt(modified.length() - 1);
                    }
                } else if (i == queryIdStart) {
                    var idx = modified.length() - 1;
                    while (Character.isWhitespace(modified.charAt(idx))) {
                        indent.insert(0, modified.charAt(idx));
                        idx--;
                    }
                } else if (i == queryIdEnd) {
                    modified.append(indent);
                    modified.append(buildRequestHashElement(xrdPrefix, hashAlgorithm, hash));
                }
                modified.append(xml.charAt(i));
            }

            return modified.toString().getBytes(charset);
        } catch (UnsupportedEncodingException e) {
            throw new XMLStreamException(e);
        }
    }

    private static String buildRequestHashElement(String xrdPrefix, String hashAlgorithm, String hash) {
        return REQUEST_HASH_TEMPLATE.formatted(xrdPrefix, hashAlgorithm, hash, xrdPrefix);
    }

    static final class BomIgnore {
        private static final char BOM = '\uFEFF';

        private final CharBuffer xml;
        private final int bomOffset;

        BomIgnore(CharBuffer xml) {
            this.xml = xml;
            this.bomOffset = xml.charAt(0) == BOM ? 1 : 0;
        }

        public char charAt(int idx) {
            return xml.charAt(idx + bomOffset);
        }

        public int length() {
            return xml.length() - bomOffset;
        }
    }

}
