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
package ee.ria.xroad.proxy.protocol;

import java.io.InputStream;
import java.util.Map;

import org.bouncycastle.cert.ocsp.OCSPResp;

import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.signature.SignatureData;

/**
 * Describes the proxy message parts that are sent between client
 * and server proxy. The proxy message consists of a SOAP XML message and
 * optional attachments and the signature. The OCSP response is only used
 * in SSL mode to send the OCSP response of the client proxy SSL certificate.
 */
public interface ProxyMessageConsumer {

    /**
     * Called when SOAP message is parsed.
     * @param message the SOAP message
     * @throws Exception if an error occurs
     */
    void soap(SoapMessageImpl message) throws Exception;

    /**
     * Called when an attachment is received.
     * @param contentType the content type of the attachment
     * @param content the input stream holding the attachment data
     * @param additionalHeaders any additional headers for the attachment
     * @throws Exception if an error occurs
     */
    void attachment(String contentType, InputStream content,
            Map<String, String> additionalHeaders) throws Exception;

    /***
     * Called when an OCSP response arrives.
     * @param resp the response
     * @throws Exception if an error occurs
     */
    void ocspResponse(OCSPResp resp) throws Exception;

    /***
     * Called when a signature arrives.
     * @param signature the signature
     * @throws Exception if an error occurs
     */
    void signature(SignatureData signature) throws Exception;

    /**
     * Called when a fault is encountered.
     * @param fault the fault message
     * @throws Exception if an error occurs
     */
    void fault(SoapFault fault) throws Exception;
}
