package ee.cyber.sdsb.proxy.protocol;

import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.commons.io.input.ReaderInputStream;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.message.SoapFault;
import ee.cyber.sdsb.common.message.SoapMessageEncoder;
import ee.cyber.sdsb.common.message.SoapMessageImpl;
import ee.cyber.sdsb.common.signature.SignatureData;
import ee.cyber.sdsb.common.util.MimeUtils;

/**
 * Reads in all of the proxy message, extracts the parts and is later able
 * to convert the message to SOAP. Note: any attachments are cached in the
 * file system.
 *
 * To load the message pass this object to a proxy message producer that
 * fills in the parts. After that, you can query the message parts and
 * the encoded SOAP message.
 */
public class ProxyMessage implements ProxyMessageConsumer {

    private static final Logger LOG =
            LoggerFactory.getLogger(ProxyMessage.class);

    private OCSPResp ocspResponse;
    private SoapMessageImpl soapMessage;
    private SignatureData signature;
    private SoapFault fault;

    private CachingStream attachmentCache;
    private SoapMessageEncoder encoder;

    /** Returns SOAP part of the message. */
    public SoapMessageImpl getSoap() {
        return soapMessage;
    }

    public SignatureData getSignature() {
        return signature;
    }

    public OCSPResp getOcspResponse() {
        return ocspResponse;
    }

    public SoapFault getFault() {
        return fault;
    }

    /** Returns content type of the cached message. */
    public String getSoapContentType() {
        return hasAttachments() ? encoder.getContentType()
                : MimeUtils.TEXT_XML_UTF8;
    }

    /** Returns content of the cached message. */
    public InputStream getSoapContent() throws Exception {
        if (hasAttachments()) {
            // Finish writing to the attachment cache.
            encoder.close();
            return attachmentCache.getCachedContents();
        } else {
            // By default write in UTF-8.
            return new ReaderInputStream(
                    new StringReader(soapMessage.getXml()),
                    StandardCharsets.UTF_8);
        }
    }

    @Override
    public void ocspResponse(OCSPResp ocspResponse) throws Exception {
        LOG.debug("Read SSL OCSP response");

        this.ocspResponse = ocspResponse;
    }

    @Override
    public void signature(SignatureData signature) throws Exception {
        LOG.debug("Read signature");

        this.signature = signature;
    }

    @Override
    public void soap(SoapMessageImpl soapMessage) throws Exception {
        LOG.debug("Read SOAP message");

        this.soapMessage = soapMessage;
    }

    @Override
    public void attachment(String contentType, InputStream content,
            Map<String, String> additionalHeaders) throws Exception {
        LOG.debug("Attachment: {}", contentType);

        if (encoder == null) {
            attachmentCache = new CachingStream();
            encoder = new SoapMessageEncoder(attachmentCache);

            // Write the SOAP before attachments
            encoder.soap(soapMessage);
        }

        encoder.attachment(contentType, content, additionalHeaders);
    }

    @Override
    public void fault(SoapFault fault) throws Exception {
        LOG.debug("Read fault");

        this.fault = fault;
    }

    private boolean hasAttachments() {
        return encoder != null;
    }
}
