/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
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
package ee.ria.xroad.proxy.protocol;
import ee.ria.xroad.common.ErrorCodes;
import ee.ria.xroad.common.ExpectedCodedException;
import ee.ria.xroad.common.message.RestRequest;
import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.signature.SignatureData;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.common.util.MimeUtils;

import org.bouncycastle.cert.ocsp.OCSPResp;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests to verify correct proxy message decoder behavior.
 */
public class ProxyMessageDecoderTest {

    DummyMessageConsumer callback;

    /**
     * Initialize.
     */
    @Before
    public void initialize() {
        callback = new DummyMessageConsumer();
    }

    @Rule
    public ExpectedCodedException thrown = ExpectedCodedException.none();

    /**
     * Test to ensure a normal message is decoded correctly.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void normalRequest() throws Exception {
        String contentType =
                MimeUtils.mpMixedContentType("xtop1357783211hcn1yiro");
        ProxyMessageDecoder decoder = createDecoder(contentType);
        decoder.parse(getMessage("normal.request"));

        assertNotNull(callback.getMessage());
        assertNotNull(callback.getSignature());
    }

    /**
     * Test to ensure a SOAP fault request is decoded correctly.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void normalSoapFaultRequest() throws Exception {
        String contentType =
                MimeUtils.mpMixedContentType("xtop1357783211hcn1yiro");
        ProxyMessageDecoder decoder = createDecoder(contentType);
        decoder.parse(getMessage("normal-soapfault.request"));

        assertNotNull(callback.getFault());
    }

    /**
     * Test to ensure a request with attachment with is decoded correctly.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void normalAttachmentRequest() throws Exception {
        String contentType =
                MimeUtils.mpMixedContentType("xtop569125687hcu8vfma");
        ProxyMessageDecoder decoder = createDecoder(contentType);
        decoder.parse(getMessage("attachment.request"));

        assertNotNull(callback.getMessage());
        assertTrue(callback.hasAttachments());
        assertNotNull(callback.getSignature());
    }

    /**
     * Test to ensure a request with OCSP responses is decoded correctly.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void normalOcspRequest() throws Exception {
        String contentType =
                MimeUtils.mpMixedContentType("xtop1357783211hcn1yiro");
        ProxyMessageDecoder decoder = createDecoder(contentType);
        decoder.parse(getMessage("ocsp.request"));

        assertNotNull(callback.getMessage());
        assertNotNull(callback.getOcspResponse());
        assertNotNull(callback.getSignature());
    }

    /**
     * Test to ensure an invalid message is decoded correctly.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void notProxyMessage() throws Exception {
        thrown.expectError(ErrorCodes.X_INVALID_MESSAGE);

        ProxyMessageDecoder decoder = createDecoder(MimeTypes.TEXT_XML);
        decoder.parse(getQuery("simple.query"));

        assertNull(callback.getMessage());
    }

    /**
     * Test to ensure a message with invalid content type is encoded correctly.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void invalidContentType() throws Exception {
        thrown.expectError(ErrorCodes.X_INVALID_CONTENT_TYPE);

        String contentType = "tsdfsdfsdf";
        ProxyMessageDecoder decoder = createDecoder(contentType);
        decoder.parse(null);

        assertNull(callback.getMessage());
    }

    /**
     * Test to ensure a null input is correctly handled.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void faultNotAllowed() throws Exception {
        thrown.expectError(ErrorCodes.X_INTERNAL_ERROR);

        ProxyMessageDecoder decoder = createDecoder(MimeTypes.TEXT_XML);
        decoder.parse(null);

        assertNull(callback.getMessage());
    }

    /**
     * Test to ensure a fault SOAP message is decoded correctly.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void parseFault() throws Exception {
        ProxyMessageDecoder decoder = createDecoder(MimeTypes.TEXT_XML);
        decoder.parse(getQuery("fault.query"));

        assertNotNull(callback.getFault());
    }

    /**
     * Test to ensure an invalid attachment is decoded correctly.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void invalidContentTypeInMultipart() throws Exception {
        thrown.expectError(ErrorCodes.X_INVALID_CONTENT_TYPE);

        ProxyMessageDecoder decoder = createDecoder(MimeTypes.MULTIPART_MIXED);
        decoder.parse(getQuery("attachm-error.query"));

        assertNotNull(callback.getMessage());
        assertFalse(callback.hasAttachments());
    }

    /**
     * Test to ensure the decoder fails to parse a message with extra content.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void failsToParseWithExtraContent() throws Exception {
        thrown.expectError(ErrorCodes.X_INVALID_MESSAGE);

        String contentType =
                MimeUtils.mpMixedContentType("xtop1357783211hcn1yiro");
        ProxyMessageDecoder decoder = createDecoder(contentType);
        decoder.parse(getMessage("extracontent.request"));
    }

    /**
     * Test to ensure a message with an invalid OCSP response is decoded correctly.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void invalidOcsp() throws Exception {
        thrown.expectError(ErrorCodes.X_IO_ERROR);

        String contentType =
                MimeUtils.mpMixedContentType("xtop1357783211hcn1yiro");
        ProxyMessageDecoder decoder = createDecoder(contentType);
        decoder.parse(getMessage("invalid-ocsp.request"));

        assertNotNull(callback.getMessage());
        assertNull(callback.getOcspResponse());
    }

    /**
     * Test to ensure the decoder fails to parse a message with invalid SOAP content type.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void invalidSoapContentType() throws Exception {
        thrown.expectError(ErrorCodes.X_INVALID_CONTENT_TYPE);

        String contentType =
                MimeUtils.mpMixedContentType("xtop1357783211hcn1yiro");
        ProxyMessageDecoder decoder = createDecoder(contentType);
        decoder.parse(getMessage("invalid-soap-contenttype.request"));

        assertNull(callback.getMessage());
    }

    /**
     * Test to ensure a message with a fault instead of signature is decoded correctly.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void faultInsteadOfSignature() throws Exception {
        String contentType =
                MimeUtils.mpMixedContentType("xtop1357783211hcn1yiro");
        ProxyMessageDecoder decoder = createDecoder(contentType);
        decoder.parse(getMessage("fault-signature.request"));

        assertNotNull(callback.getFault());
        assertNull(callback.getSignature());
    }

    /**
     * Test to ensure a message with an invalid signature content type is decoded correctly.
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void invalidContentTypeForSignature() throws Exception {
        thrown.expectError(ErrorCodes.X_INVALID_CONTENT_TYPE);

        String contentType =
                MimeUtils.mpMixedContentType("xtop1357783211hcn1yiro");
        ProxyMessageDecoder decoder = createDecoder(contentType);
        decoder.parse(getMessage("invalid-contenttype-signature.request"));

        assertNull(callback.getSignature());
    }

    private ProxyMessageDecoder createDecoder(String contentType) {
        return new ProxyMessageDecoder(callback, contentType, true,
                getHashAlgoId());
    }

    private static InputStream getQuery(String fileName) throws Exception {
        return Files.newInputStream(Paths.get("src/test/queries/" + fileName));
    }

    private static InputStream getMessage(String fileName) throws Exception {
        return Files.newInputStream(Paths.get("src/test/proxymessages/" + fileName));
    }

    private String getHashAlgoId() {
        return CryptoUtils.DEFAULT_DIGEST_ALGORITHM_ID;
    }

    private static class DummyMessageConsumer implements ProxyMessageConsumer {

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
        public void soap(SoapMessageImpl soap,
                         Map<String, String> additionalHeaders) throws Exception {
            this.soapMessage = soap;
        }

        @Override
        public void rest(RestRequest message) {

        }

        @Override
        public void restBody(InputStream content) {

        }

        @Override
        public void attachment(String contentType, InputStream content,
                               Map<String, String> additionalHeaders) throws Exception {
            this.hasAttachments = true;
        }

        @Override
        public void ocspResponse(OCSPResp resp) {
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
