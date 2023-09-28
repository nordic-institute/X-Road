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
package ee.ria.xroad.proxy.protocol;

import ee.ria.xroad.common.message.MultipartSoapMessageEncoder;
import ee.ria.xroad.common.message.RestRequest;
import ee.ria.xroad.common.message.RestResponse;
import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.message.SoapMessageEncoder;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.signature.SignatureData;
import ee.ria.xroad.common.util.CacheInputStream;
import ee.ria.xroad.common.util.CachingStream;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.common.util.MimeUtils;
import ee.ria.xroad.common.util.MultipartEncoder;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.bouncycastle.cert.ocsp.OCSPResp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads in all of the proxy message, extracts the parts and is later able
 * to convert the message to SOAP. Note: any attachments are cached in the
 * file system.
 *
 * To load the message pass this object to a proxy message producer that
 * fills in the parts. After that, you can query the message parts and
 * the encoded SOAP message.
 */
@Slf4j
public class ProxyMessage implements ProxyMessageConsumer {

    public static final int REST_BODY_LIMIT = 8192; //store up to limit bytes into memory
    private final List<OCSPResp> ocspResponses = new ArrayList<>();

    private final String originalContentType;
    private final String originalMimeBoundary;

    private SoapMessageImpl soapMessage;
    private SignatureData signature;
    private SoapFault fault;

    protected Map<String, String> soapPartHeaders;

    protected CachingStream attachmentCache;
    protected SoapMessageEncoder encoder;

    private boolean hasBeenConsumed;
    private RestRequest restMessage;
    private RestResponse restResponse;

    /**
     * Constructs new proxy message with the original message content type.
     *
     * @param originalContentType the original content type.
     */
    public ProxyMessage(String originalContentType) {
        this.originalContentType = originalContentType;
        this.originalMimeBoundary = MimeUtils.getBoundary(originalContentType);
    }

    /**
     * @return SOAP part of the message.
     */
    public SoapMessageImpl getSoap() {
        return soapMessage;
    }

    public RestRequest getRest() {
        return restMessage;
    }

    public RestResponse getRestResponse() {
        return restResponse;
    }

    /**
     * @return the message signature
     */
    public SignatureData getSignature() {
        return signature;
    }

    /**
     * @return TLS OCSP responses
     */
    public List<OCSPResp> getOcspResponses() {
        return ocspResponses;
    }

    /**
     * @return SOAP fault if this message is a fault, null otherwise
     */
    public SoapFault getFault() {
        return fault;
    }

    /**
     * @return content type of the cached message.
     */
    public String getSoapContentType() {
        return isMimeEncodedSoap() || hasAttachments()
                ? (originalContentType != null ? originalContentType : encoder.getContentType())
                : MimeTypes.TEXT_XML_UTF8;
    }

    /**
     * @return content of the cached message.
     * @throws Exception in case of any errors
     */
    public InputStream getSoapContent() throws Exception {
        if (isMimeEncodedSoap()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MultipartEncoder mp = new MultipartEncoder(out, originalMimeBoundary);
            mp.startPart(getSoap().getContentType(), MimeUtils.toHeaders(soapPartHeaders));
            mp.write(getSoap().getBytes());
            mp.close();

            return new ByteArrayInputStream(out.toByteArray());
        } else if (hasAttachments()) {
            // Finish writing to the attachment cache.
            encoder.close();

            hasBeenConsumed = true;

            return attachmentCache.getCachedContents();
        } else {
            return new ByteArrayInputStream(soapMessage.getBytes());
        }
    }

    /**
     * Finalize SOAP message processing.
     */
    public void consume() {
        if (hasAttachments() && !hasBeenConsumed) {
            try {
                encoder.close();

                hasBeenConsumed = true;
            } catch (Exception ignored) {
                log.warn("Error closing SOAP encoder: {}", ignored);
            }
        }

        if (attachmentCache != null) {
            attachmentCache.consume();
        }
    }

    @Override
    public void ocspResponse(OCSPResp ocspResponse) throws Exception {
        log.trace("Read TLS OCSP response");

        this.ocspResponses.add(ocspResponse);
    }

    @Override
    public void signature(SignatureData signatureData) throws Exception {
        log.trace("Read signature");

        this.signature = signatureData;
    }

    @Override
    public void soap(SoapMessageImpl soap, Map<String, String> additionalHeaders) throws Exception {
        log.trace("Read SOAP message");

        this.soapMessage = soap;
        this.soapPartHeaders = additionalHeaders;
    }

    @Override
    public void rest(RestRequest message) throws Exception {
        log.trace("Rest request");
        this.restMessage = message;
    }

    @Override
    public void rest(RestResponse message) throws Exception {
        log.trace("Rest response");
        this.restResponse = message;
    }

    @Override
    public void restBody(InputStream content) throws Exception {
        assert (attachmentCache == null);
        attachmentCache = new CachingStream();
        IOUtils.copyLarge(content, attachmentCache);
    }

    @Override
    public void attachment(String contentType, InputStream content, Map<String, String> additionalHeaders)
            throws Exception {
        log.trace("Attachment: {}", contentType);

        if (!hasAttachments()) {
            attachmentCache = new CachingStream();
            encoder = createEncoder();

            // Write the SOAP before attachments
            encoder.soap(soapMessage, soapPartHeaders);
        }

        encoder.attachment(contentType, content, additionalHeaders);
    }

    @Override
    public void fault(SoapFault soapFault) throws Exception {
        log.trace("Read fault");

        this.fault = soapFault;
    }

    protected boolean hasAttachments() {
        return encoder != null;
    }

    protected SoapMessageEncoder createEncoder() {
        return new MultipartSoapMessageEncoder(attachmentCache, originalMimeBoundary);
    }

    // Returns true, if this the original message was a MIME-encoded SOAP
    // message without any attachments (special case).
    private boolean isMimeEncodedSoap() {
        return MimeTypes.MULTIPART_RELATED.equalsIgnoreCase(MimeUtils.getBaseContentType(originalContentType))
                && !hasAttachments();
    }


    public boolean hasRestBody() {
        return attachmentCache != null && (restMessage != null || restResponse != null);
    }
    /**
     * Get rest body as inputstream.
     */
    public CacheInputStream getRestBody() {
        if (attachmentCache != null) {
            return attachmentCache.getCachedContents();
        }
        return null;
    }
}
