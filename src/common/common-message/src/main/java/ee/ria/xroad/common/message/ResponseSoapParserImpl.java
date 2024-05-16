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

package ee.ria.xroad.common.message;

import org.apache.commons.lang3.ArrayUtils;
import org.xml.sax.Attributes;
import org.xml.sax.helpers.AttributesImpl;

import javax.xml.namespace.QName;

import java.io.Writer;

import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.util.CryptoUtils.encodeBase64;
import static ee.ria.xroad.common.util.CryptoUtils.getDigestAlgorithmURI;

/**
 * Soap parser that adds the request message hash to the response message header.
 */
public final class ResponseSoapParserImpl extends SaxSoapParserImpl {

    private boolean inHeader;
    private boolean inBody;
    private boolean inExistingRequestHash;
    private boolean bufferFlushed = true;

    private char[] headerElementTabs;

    private char[] bufferedChars;
    private int bufferedOffset;
    private int bufferedLength;

    private final byte[] requestHash;

    public ResponseSoapParserImpl(byte[] requestHash) {
        this.requestHash = requestHash;
    }

    // force usage of processed XML since we need to write the request hash
    @Override
    protected boolean isProcessedXmlRequired() {
        return true;
    }

    @Override
    protected SoapHeaderHandler getSoapHeaderHandler(SoapHeader header) {
        return new SoapHeaderHandler(header) {
            @Override
            protected void openTag() {
                super.openTag();
                inHeader = true;
            }

            @Override
            protected void closeTag() {
                super.closeTag();
                inHeader = false;
            }
        };
    }

    @Override
    protected void writeEndElementXml(String prefix, QName element, Attributes attributes, Writer writer) {
        if (inHeader && element.equals(QNAME_XROAD_REQUEST_HASH)) {
            inExistingRequestHash = false;
        } else {
            writeBufferedCharacters(writer);
            super.writeEndElementXml(prefix, element, attributes, writer);
        }

        if (inHeader && element.equals(QNAME_XROAD_QUERY_ID)) {
            try {
                String hash = encodeBase64(requestHash);

                AttributesImpl hashAttrs = new AttributesImpl(attributes);
                String algoId = getDigestAlgorithmURI(SoapUtils.getHashAlgoId());
                hashAttrs.addAttribute("", "", ATTR_ALGORITHM_ID, "xs:string", algoId);

                char[] tabs = headerElementTabs != null ? headerElementTabs : new char[0];
                super.writeCharactersXml(tabs, 0, tabs.length, writer);
                super.writeStartElementXml(prefix, QNAME_XROAD_REQUEST_HASH, hashAttrs, writer);
                super.writeCharactersXml(hash.toCharArray(), 0, hash.length(), writer);
                super.writeEndElementXml(prefix, QNAME_XROAD_REQUEST_HASH, hashAttrs, writer);
            } catch (Exception e) {
                throw translateException(e);
            }
        }
    }

    @Override
    protected void writeStartElementXml(String prefix, QName element, Attributes attributes, Writer writer) {
        if (inHeader && element.equals(QNAME_XROAD_REQUEST_HASH)) {
            inExistingRequestHash = true;
        } else {
            if (!inBody && element.equals(QNAME_SOAP_BODY)) {
                inBody = true;
            }

            writeBufferedCharacters(writer);
            super.writeStartElementXml(prefix, element, attributes, writer);
        }
    }

    private void writeBufferedCharacters(Writer writer) {
        // Write the characters we ignored at the last characters event
        if (!bufferFlushed) {
            super.writeCharactersXml(bufferedChars, bufferedOffset, bufferedLength, writer);
            bufferFlushed = true;
        }
    }

    @Override
    protected void writeCharactersXml(char[] characters, int start, int length, Writer writer) {
        if (inHeader && headerElementTabs == null) {
            String value = new String(characters, start, length);

            if (value.trim().isEmpty()) {
                headerElementTabs = value.toCharArray();
            }
        }

        // When writing characters outside of the SOAP body, delay this
        // operation until the next event, sometimes we don't want to write
        // these characters, like when we're discarding a header
        if (!inBody && bufferFlushed) {
            bufferCharacters(characters, start, length);
        } else if (!inExistingRequestHash) {
            writeBufferedCharacters(writer);
            super.writeCharactersXml(characters, start, length, writer);
        }
    }

    private void bufferCharacters(char[] characters, int start, int length) {
        if (bufferedChars == null || bufferedChars.length < characters.length) {
            bufferedChars = ArrayUtils.clone(characters);
        } else {
            System.arraycopy(characters, start, bufferedChars, start, length);
        }

        bufferedOffset = start;
        bufferedLength = length;
        bufferFlushed = false;
    }
}
