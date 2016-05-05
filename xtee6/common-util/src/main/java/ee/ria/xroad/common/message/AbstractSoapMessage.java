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
package ee.ria.xroad.common.message;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import javax.xml.soap.SOAPMessage;

/**
 * Base class for SOAP messages.
 * @param <T> generic type of the SOAP message header.
 */
@Getter
@RequiredArgsConstructor
public abstract class AbstractSoapMessage<T> implements SoapMessage {

    private final byte[] bytes;
    private final String charset;
    private final T header;
    private final SOAPMessage soap;
    private final boolean isResponse;
    private final boolean isRpcEncoded;

    /**
     * Gets the SOAP header instance for this message.
     * @return T
     */
    public T getHeader() {
        return header;
    }

    @Override
    public boolean isRequest() {
        return !isResponse;
    }

    @Override
    public String getXml() throws Exception {
        return new String(bytes, charset);
    }
}
