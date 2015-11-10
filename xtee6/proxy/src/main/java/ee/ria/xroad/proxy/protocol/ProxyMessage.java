package ee.ria.xroad.proxy.protocol;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.cert.ocsp.OCSPResp;

import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.message.SoapMessageEncoder;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.signature.SignatureData;
import ee.ria.xroad.common.util.CachingStream;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.common.util.MimeUtils;
import ee.ria.xroad.common.util.MultipartEncoder;

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

    /**
     * Constructs new proxy message with the original message content type.
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
                ? originalContentType != null
                    ? originalContentType : encoder.getContentType()
                : MimeUtils.TEXT_XML_UTF8;
    }

    /**
     * @return content of the cached message.
     * @throws Exception in case of any errors
     */
    public InputStream getSoapContent() throws Exception {
        if (isMimeEncodedSoap()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MultipartEncoder mp =
                    new MultipartEncoder(out, originalMimeBoundary);
            mp.startPart(getSoap().getContentType(),
                    MimeUtils.toHeaders(soapPartHeaders));
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
        if (!hasBeenConsumed) {
            try {
                getSoapContent().close();
            } catch (Exception ignored) {
                log.warn("Error closing SOAP content input stream");
            }
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
    public void soap(SoapMessageImpl soap,
            Map<String, String> additionalHeaders) throws Exception {
        log.trace("Read SOAP message");

        this.soapMessage = soap;
        this.soapPartHeaders = additionalHeaders;
    }

    @Override
    public void attachment(String contentType, InputStream content,
            Map<String, String> additionalHeaders) throws Exception {
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
        return new SoapMessageEncoder(attachmentCache, originalMimeBoundary);
    }

    // Returns true, if this the original message was a MIME-encoded SOAP
    // message without any attachments (special case).
    private boolean isMimeEncodedSoap() {
        return MimeTypes.MULTIPART_RELATED.equalsIgnoreCase(
                MimeUtils.getBaseContentType(originalContentType))
                && !hasAttachments();
    }
}
