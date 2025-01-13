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

import ee.ria.xroad.common.message.AttachmentStream;
import ee.ria.xroad.common.message.MultipartSoapMessageEncoder;
import ee.ria.xroad.common.message.RestRequest;
import ee.ria.xroad.common.message.RestResponse;
import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.signature.SignatureData;
import ee.ria.xroad.common.util.CacheInputStream;
import ee.ria.xroad.common.util.CachingStream;
import ee.ria.xroad.common.util.HeaderValueUtils;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.common.util.MimeUtils;
import ee.ria.xroad.common.util.MultipartEncoder;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.bouncycastle.cert.ocsp.OCSPResp;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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

    @Getter
    private final List<OCSPResp> ocspResponses = new ArrayList<>();

    private final String originalContentType;
    private final String originalMimeBoundary;

    private SoapMessageImpl soapMessage;
    @Getter
    private SignatureData signature;

    @Getter
    private SoapFault fault;

    private Map<String, String> soapPartHeaders;

    private CachingStream restBodyCache;

    private final List<Attachment> attachmentCache = new ArrayList<>();

    private RestRequest restMessage;
    @Getter
    private RestResponse restResponse;

    /**
     * Constructs new proxy message with the original message content type.
     *
     * @param originalContentType the original content type.
     */
    public ProxyMessage(String originalContentType) {
        this.originalContentType = originalContentType;
        this.originalMimeBoundary = HeaderValueUtils.getBoundary(originalContentType);
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

    /**
     * @return content type of the cached message.
     */
    public String getSoapContentType() {
        return isMimeEncodedSoap() || hasAttachments()
                ? originalContentType           // cannot be null here, because isMimeEncodedSoap() would throw before
                : MimeTypes.TEXT_XML_UTF8;
    }

    /**
     * @return content of the cached message.
     * @throws IOException in case of any errors
     * @deprecated use {@link #writeSoapContent(OutputStream)} instead
     */
    @Deprecated
    public InputStream getSoapContent() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        writeSoapContent(out);
        return new ByteArrayInputStream(out.toByteArray());
    }

    public void writeSoapContent(OutputStream out) throws IOException {
        if (isMimeEncodedSoap()) {
            MultipartEncoder mp = new MultipartEncoder(out, originalMimeBoundary);
            mp.startPart(getSoap().getContentType(), MimeUtils.toHeaders(soapPartHeaders));
            mp.write(getSoap().getBytes());
            mp.close();
        } else if (hasAttachments()) {
            MultipartSoapMessageEncoder multipartEncoder = new MultipartSoapMessageEncoder(out, originalMimeBoundary);
            // Write the SOAP before attachments
            multipartEncoder.soap(soapMessage, soapPartHeaders);
            for (Attachment attachment : attachmentCache) {
                multipartEncoder.attachment(attachment.contentType, attachment.content.getCachedContents(), attachment.additionalHeaders);
            }
            // Finish writing to the attachment cache.
            multipartEncoder.close();
        } else {
            out.write(soapMessage.getBytes());
        }
    }

    /**
     * Finalize SOAP message processing.
     */
    public void consume() {
        if (restBodyCache != null) {
            restBodyCache.consume();
        }

        for (var attachment : attachmentCache) {
            attachment.content.consume();
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
        assert (restBodyCache == null);
        restBodyCache = new CachingStream();
        IOUtils.copyLarge(content, restBodyCache);
    }

    @Override
    public void attachment(String contentType, InputStream content, Map<String, String> additionalHeaders)
            throws Exception {
        log.trace("Attachment: {}", contentType);

        CachingStream attachmentCacheStream = new CachingStream();
        IOUtils.copyLarge(content, attachmentCacheStream);
        attachmentCache.add(new Attachment(contentType, attachmentCacheStream, additionalHeaders));
    }

    @Override
    public void fault(SoapFault soapFault) throws Exception {
        log.trace("Read fault");

        this.fault = soapFault;
    }

    protected boolean hasAttachments() {
        return !attachmentCache.isEmpty();
    }

    // Returns true, if this the original message was a MIME-encoded SOAP
    // message without any attachments (special case).
    private boolean isMimeEncodedSoap() {
        return MimeTypes.MULTIPART_RELATED.equalsIgnoreCase(MimeUtils.getBaseContentType(originalContentType))
                && !hasAttachments();
    }


    public boolean hasRestBody() {
        return restBodyCache != null && (restMessage != null || restResponse != null);
    }

    /**
     * Get rest body as inputstream.
     */
    public CacheInputStream getRestBody() {
        if (restBodyCache != null) {
            return restBodyCache.getCachedContents();
        }
        return null;
    }

    public List<AttachmentStream> getAttachments() {
        return attachmentCache.stream().map(Attachment::getAttachmentStream).toList();
    }


    private record Attachment(String contentType, CachingStream content, Map<String, String> additionalHeaders) {
        AttachmentStream getAttachmentStream() {
            return new AttachmentStream() {
                @Override
                public InputStream getStream() {
                    return content.getCachedContents();
                }

                @Override
                public long getSize() {
                    return content.size();
                }
            };
        }
    }
}
