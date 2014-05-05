package ee.cyber.sdsb.proxy.protocol;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Map;

import org.bouncycastle.cert.ocsp.OCSPResp;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

import ee.cyber.sdsb.common.ErrorCodes;
import ee.cyber.sdsb.common.ExpectedCodedException;
import ee.cyber.sdsb.common.message.SoapFault;
import ee.cyber.sdsb.common.message.SoapMessageImpl;
import ee.cyber.sdsb.common.signature.SignatureData;
import ee.cyber.sdsb.common.util.MimeTypes;
import ee.cyber.sdsb.common.util.MimeUtils;

import static org.junit.Assert.*;

@Ignore("Messages must be fixed with new signatures etc.")
public class ProxyMessageDecoderTest {

    DummyMessageConsumer callback;

    @Before
    public void initialize() throws Exception {
        callback = new DummyMessageConsumer();
    }

    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    @Test
    public void normalRequest() throws Exception {
        String contentType =
                MimeUtils.mpMixedContentType("xtop1357783211hcn1yiro");
        ProxyMessageDecoder decoder = new ProxyMessageDecoder(callback,
                contentType, true);
        decoder.parse(getMessage("normal.request"));

        assertNotNull(callback.getMessage());
        assertNotNull(callback.getSignature());
    }

    @Test
    public void normalSoapFaultRequest() throws Exception {
        String contentType =
                MimeUtils.mpMixedContentType("xtop1357783211hcn1yiro");
        ProxyMessageDecoder decoder = new ProxyMessageDecoder(callback,
                contentType, true);
        decoder.parse(getMessage("normal-soapfault.request"));

        assertNotNull(callback.getFault());
    }

    @Test
    public void normalAttachmentRequest() throws Exception {
        String contentType =
                MimeUtils.mpMixedContentType("xtop569125687hcu8vfma");
        ProxyMessageDecoder decoder = new ProxyMessageDecoder(callback,
                contentType, true);
        decoder.parse(getMessage("attachment.request"));

        assertNotNull(callback.getMessage());
        assertTrue(callback.hasAttachments());
        assertNotNull(callback.getSignature());
    }

    @Test
    public void normalOcspRequest() throws Exception {
        String contentType =
                MimeUtils.mpMixedContentType("xtop1357783211hcn1yiro");
        ProxyMessageDecoder decoder = new ProxyMessageDecoder(callback,
                contentType, true);
        decoder.parse(getMessage("ocsp.request"));

        assertNotNull(callback.getMessage());
        assertNotNull(callback.getOcspResponse());
        assertNotNull(callback.getSignature());
    }

    @Test
    public void invalidSignature() throws Exception {
        thrown.expectError(ErrorCodes.X_INVALID_XML);

        String contentType =
                MimeUtils.mpMixedContentType("xtop1357783211hcn1yiro");
        ProxyMessageDecoder decoder = new ProxyMessageDecoder(callback,
                contentType, true);
        decoder.parse(getMessage("invalid-signature.request"));

        assertNull(callback.getSignature());
    }

    @Test
    public void notProxyMessage() throws Exception {
        thrown.expectError(ErrorCodes.X_INVALID_MESSAGE);

        ProxyMessageDecoder decoder = new ProxyMessageDecoder(callback,
                MimeTypes.TEXT_XML, true);
        decoder.parse(getQuery("simple.query"));

        assertNull(callback.getMessage());
    }

    @Test
    public void invalidContentType() throws Exception {
        thrown.expectError(ErrorCodes.X_INVALID_CONTENT_TYPE);

        String contentType = "tsdfsdfsdf";
        ProxyMessageDecoder decoder = new ProxyMessageDecoder(callback,
                contentType, true);
        decoder.parse(null);

        assertNull(callback.getMessage());
    }

    @Test
    public void faultNotAllowed() throws Exception {
        thrown.expectError(ErrorCodes.X_INVALID_CONTENT_TYPE);

        ProxyMessageDecoder decoder = new ProxyMessageDecoder(callback,
                MimeTypes.TEXT_XML, false);
        decoder.parse(null);

        assertNull(callback.getMessage());
    }

    @Test
    public void parseFault() throws Exception {
        ProxyMessageDecoder decoder = new ProxyMessageDecoder(callback,
                MimeTypes.TEXT_XML, true);
        decoder.parse(getQuery("fault.query"));

        assertNotNull(callback.getFault());
    }

    @Test
    public void invalidContentTypeInMultipart() throws Exception {
        thrown.expectError(ErrorCodes.X_INVALID_CONTENT_TYPE);

        ProxyMessageDecoder decoder = new ProxyMessageDecoder(callback,
                MimeTypes.MULTIPART_MIXED, true);
        decoder.parse(getQuery("attachm-error.query"));

        assertNotNull(callback.getMessage());
        assertFalse(callback.hasAttachments());
    }

    @Test
    public void failsToParseWithExtraContent() throws Exception {
        thrown.expectError(ErrorCodes.X_INVALID_MESSAGE);

        String contentType =
                MimeUtils.mpMixedContentType("xtop1357783211hcn1yiro");
        ProxyMessageDecoder decoder = new ProxyMessageDecoder(callback,
                contentType, true);
        decoder.parse(getMessage("extracontent.request"));
    }

    @Test
    public void invalidOcsp() throws Exception {
        thrown.expectError(ErrorCodes.X_IO_ERROR);

        String contentType =
                MimeUtils.mpMixedContentType("xtop1357783211hcn1yiro");
        ProxyMessageDecoder decoder = new ProxyMessageDecoder(callback,
                contentType, true);
        decoder.parse(getMessage("invalid-ocsp.request"));

        assertNotNull(callback.getMessage());
        assertNull(callback.getOcspResponse());
    }

    @Test
    public void invalidSoapContentType() throws Exception {
        thrown.expectError(ErrorCodes.X_INVALID_CONTENT_TYPE);

        String contentType =
                MimeUtils.mpMixedContentType("xtop1357783211hcn1yiro");
        ProxyMessageDecoder decoder = new ProxyMessageDecoder(callback,
                contentType, true);
        decoder.parse(getMessage("invalid-soap-contenttype.request"));

        assertNull(callback.getMessage());
    }

    @Test
    public void faultInsteadOfSignature() throws Exception {
        String contentType =
                MimeUtils.mpMixedContentType("xtop1357783211hcn1yiro");
        ProxyMessageDecoder decoder = new ProxyMessageDecoder(callback,
                contentType, true);
        decoder.parse(getMessage("fault-signature.request"));

        assertNotNull(callback.getFault());
        assertNull(callback.getSignature());
    }

    @Test
    public void invalidContentTypeForSignature() throws Exception {
        thrown.expectError(ErrorCodes.X_INVALID_CONTENT_TYPE);

        String contentType =
                MimeUtils.mpMixedContentType("xtop1357783211hcn1yiro");
        ProxyMessageDecoder decoder = new ProxyMessageDecoder(callback,
                contentType, true);
        decoder.parse(getMessage("invalid-contenttype-signature.request"));

        assertNull(callback.getSignature());
    }

    private static InputStream getQuery(String fileName) throws Exception {
        return new FileInputStream("src/test/queries/" + fileName);
    }

    private static InputStream getMessage(String fileName) throws Exception {
        return new FileInputStream("src/test/proxymessages/" + fileName);
    }

    private class DummyMessageConsumer implements ProxyMessageConsumer {

        private SoapMessageImpl soapMessage;
        private boolean hasAttachments;
        private OCSPResp ocspResponse;
        private SignatureData signature;
        private SoapFault soapFault;

        public SoapMessageImpl getMessage() {
            return soapMessage;
        }

        public boolean hasAttachments() {
            return hasAttachments;
        }

        public OCSPResp getOcspResponse() {
            return ocspResponse;
        }

        public SignatureData getSignature() {
            return signature;
        }

        public SoapFault getFault() {
            return soapFault;
        }

        @Override
        public void soap(SoapMessageImpl soap) throws Exception {
            this.soapMessage = soap;
        }

        @Override
        public void attachment(String contentType, InputStream content,
                Map<String, String> additionalHeaders) throws Exception {
            this.hasAttachments = true;
        }

        @Override
        public void ocspResponse(OCSPResp resp) throws Exception {
            this.ocspResponse = resp;
        }

        @Override
        public void signature(SignatureData sig) throws Exception {
            this.signature = sig;
        }

        @Override
        public void fault(SoapFault fault) throws Exception {
            this.soapFault = fault;
        }
    }
}
