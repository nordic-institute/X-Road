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
package org.niis.xroad.proxy.core.protocol;

import ee.ria.xroad.common.crypto.Digests;
import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;
import ee.ria.xroad.common.message.RestRequest;
import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.signature.SignatureData;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.common.util.MimeUtils;

import lombok.Getter;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.junit.Before;
import org.junit.Test;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.impl.ocsp.OcspVerifierFactory;
import org.niis.xroad.test.globalconf.TestGlobalConfFactory;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.niis.xroad.common.core.exception.ErrorCode.INTERNAL_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_CONTENT_TYPE;
import static org.niis.xroad.common.core.exception.ErrorCode.INVALID_MESSAGE;
import static org.niis.xroad.common.core.exception.ErrorCode.IO_ERROR;
import static org.niis.xroad.proxy.core.protocol.ProxyMessageDecoder.MAX_HASHCHAIN_PART_BYTES;
import static org.niis.xroad.proxy.core.protocol.ProxyMessageDecoder.MAX_SIGNATURE_PART_BYTES;

/**
 * Tests to verify correct proxy message decoder behavior.
 */
public class ProxyMessageDecoderTest {

    DummyMessageConsumer callback;

    GlobalConfProvider globalConfProvider;
    OcspVerifierFactory ocspVerifierFactory;

    /**
     * Initialize.
     */
    @Before
    public void initialize() {
        callback = new DummyMessageConsumer();
        globalConfProvider = TestGlobalConfFactory.create("dummy");
        ocspVerifierFactory = new OcspVerifierFactory();
    }

    /**
     * Test to ensure a normal message is decoded correctly.
     *
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void normalRequest() throws Exception {
        String contentType =
                MimeUtils.mpMixedContentType("xtop1357783211hcn1yiro");
        ProxyMessageDecoder decoder = createDecoder(contentType);
        decoder.parse(getMessage("normal.request"));

        assertNotNull(callback.getSoapMessage());
        assertNotNull(callback.getSignature());
    }

    /**
     * Test to ensure a SOAP fault request is decoded correctly.
     *
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void normalSoapFaultRequest() throws Exception {
        String contentType =
                MimeUtils.mpMixedContentType("xtop1357783211hcn1yiro");
        ProxyMessageDecoder decoder = createDecoder(contentType);
        decoder.parse(getMessage("normal-soapfault.request"));

        assertNotNull(callback.getSoapFault());
    }

    /**
     * Test to ensure a request with attachment with is decoded correctly.
     *
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void normalAttachmentRequest() throws Exception {
        String contentType =
                MimeUtils.mpMixedContentType("xtop569125687hcu8vfma");
        ProxyMessageDecoder decoder = createDecoder(contentType);
        decoder.parse(getMessage("attachment.request"));

        assertNotNull(callback.getSoapMessage());
        assertTrue(callback.hasAttachments());
        assertNotNull(callback.getSignature());
    }

    /**
     * Test to ensure a request with OCSP responses is decoded correctly.
     *
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void normalOcspRequest() throws Exception {
        String contentType =
                MimeUtils.mpMixedContentType("xtop1357783211hcn1yiro");
        ProxyMessageDecoder decoder = createDecoder(contentType);
        decoder.parse(getMessage("ocsp.request"));

        assertNotNull(callback.getSoapMessage());
        assertNotNull(callback.getOcspResponse());
        assertNotNull(callback.getSignature());
    }

    /**
     * Test to ensure an invalid message is decoded correctly.
     */
    @Test
    public void notProxyMessage() {
        ProxyMessageDecoder decoder = createDecoder(MimeTypes.TEXT_XML);
        XrdRuntimeException xrdException = assertThrows(XrdRuntimeException.class, () -> decoder.parse(getQuery("simple.query")));
        assertEquals(INVALID_MESSAGE.code(), xrdException.getErrorCode());

        assertNull(callback.getSoapMessage());
    }

    /**
     * Test to ensure a message with invalid content type is encoded correctly.
     */
    @Test
    public void invalidContentType() {
        String contentType = "tsdfsdfsdf";
        ProxyMessageDecoder decoder = createDecoder(contentType);
        XrdRuntimeException ex = assertThrows(XrdRuntimeException.class, () -> decoder.parse(null));
        assertEquals(INVALID_CONTENT_TYPE.code(), ex.getErrorCode());

        assertNull(callback.getSoapMessage());
    }

    /**
     * Test to ensure a null input is correctly handled.
     */
    @Test
    public void faultNotAllowed() {
        ProxyMessageDecoder decoder = createDecoder(MimeTypes.TEXT_XML);
        XrdRuntimeException ex = assertThrows(XrdRuntimeException.class, () -> decoder.parse(null));
        assertEquals(INTERNAL_ERROR.code(), ex.getErrorCode());

        assertNull(callback.getSoapMessage());
    }

    /**
     * Test to ensure a fault SOAP message is decoded correctly.
     *
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void parseFault() throws Exception {
        ProxyMessageDecoder decoder = createDecoder(MimeTypes.TEXT_XML);
        decoder.parse(getQuery("fault.query"));

        assertNotNull(callback.getSoapFault());
    }

    /**
     * Test to ensure an invalid attachment is decoded correctly.
     */
    @Test
    public void invalidContentTypeInMultipart() {
        ProxyMessageDecoder decoder = createDecoder(MimeTypes.MULTIPART_MIXED);
        XrdRuntimeException ex = assertThrows(XrdRuntimeException.class, () -> decoder.parse(getQuery("attachm-error.query")));
        assertEquals(INVALID_CONTENT_TYPE.code(), ex.getErrorCode());
    }

    /**
     * Test to ensure the decoder fails to parse a message with extra content.
     */
    @Test
    public void failsToParseWithExtraContent() {
        String contentType =
                MimeUtils.mpMixedContentType("xtop1357783211hcn1yiro");
        ProxyMessageDecoder decoder = createDecoder(contentType);
        XrdRuntimeException ex = assertThrows(XrdRuntimeException.class, () -> decoder.parse(getMessage("extracontent.request")));
        assertEquals(INVALID_MESSAGE.code(), ex.getErrorCode());
    }

    /**
     * Test to ensure a message with an invalid OCSP response is decoded correctly.
     */
    @Test
    public void invalidOcsp() {
        String contentType =
                MimeUtils.mpMixedContentType("xtop1357783211hcn1yiro");
        ProxyMessageDecoder decoder = createDecoder(contentType);
        XrdRuntimeException ex = assertThrows(XrdRuntimeException.class, () -> decoder.parse(getMessage("invalid-ocsp.request")));
        assertEquals(IO_ERROR.code(), ex.getErrorCode());

        assertNull(callback.getOcspResponse());
    }

    /**
     * Test to ensure the decoder fails to parse a message with invalid SOAP content type.
     */
    @Test
    public void invalidSoapContentType() {
        String contentType =
                MimeUtils.mpMixedContentType("xtop1357783211hcn1yiro");
        ProxyMessageDecoder decoder = createDecoder(contentType);
        XrdRuntimeException ex = assertThrows(XrdRuntimeException.class,
                () -> decoder.parse(getMessage("invalid-soap-contenttype.request")));
        assertEquals(INVALID_CONTENT_TYPE.code(), ex.getErrorCode());

        assertNull(callback.getSoapMessage());
    }

    /**
     * Test to ensure a message with a fault instead of signature is decoded correctly.
     *
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void faultInsteadOfSignature() throws Exception {
        String contentType =
                MimeUtils.mpMixedContentType("xtop1357783211hcn1yiro");
        ProxyMessageDecoder decoder = createDecoder(contentType);
        decoder.parse(getMessage("fault-signature.request"));

        assertNotNull(callback.getSoapFault());
        assertNull(callback.getSignature());
    }

    /**
     * Test to ensure a message with an invalid signature content type is decoded correctly.
     */
    @Test
    public void invalidContentTypeForSignature() {
        String contentType =
                MimeUtils.mpMixedContentType("xtop1357783211hcn1yiro");
        ProxyMessageDecoder decoder = createDecoder(contentType);
        XrdRuntimeException ex = assertThrows(XrdRuntimeException.class,
                () -> decoder.parse(getMessage("invalid-contenttype-signature.request")));
        assertEquals(INVALID_CONTENT_TYPE.code(), ex.getErrorCode());

        assertNull(callback.getSignature());
    }

    /**
     * Test to ensure a hashchain/hashchainresult part within the size cap is accepted.
     *
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void hashChainPartWithinCapDecodes() throws Exception {
        String boundary = "testboundary123";
        String contentType = MimeUtils.mpMixedContentType(boundary);
        ProxyMessageDecoder decoder = createDecoder(contentType);
        decoder.parse(buildHashChainMessage(boundary, "x".repeat(100), "y".repeat(100)));
        assertNotNull(callback.getSignature());
    }

    /**
     * Test to ensure an oversized hashchainresult part is rejected as a clean fault, not OOM.
     */
    @Test
    public void oversizedHashChainResultPartIsRejected() {
        String boundary = "testboundary123";
        String contentType = MimeUtils.mpMixedContentType(boundary);
        ProxyMessageDecoder decoder = createDecoder(contentType);
        String oversized = "x".repeat((int) MAX_HASHCHAIN_PART_BYTES + 1);
        XrdRuntimeException ex = assertThrows(XrdRuntimeException.class,
                () -> decoder.parse(buildHashChainMessage(boundary, oversized, "y".repeat(100))));
        assertEquals(IO_ERROR.code(), ex.getErrorCode());
    }

    /**
     * Test to ensure a signature part within the size cap is accepted.
     *
     * @throws Exception in case of any unexpected errors
     */
    @Test
    public void signaturePartWithinCapDecodes() throws Exception {
        String boundary = "testboundary123";
        String contentType = MimeUtils.mpMixedContentType(boundary);
        ProxyMessageDecoder decoder = createDecoder(contentType);
        decoder.parse(buildHashChainMessage(boundary, "x".repeat(100), "y".repeat(100), "<sig/>"));
        assertNotNull(callback.getSignature());
    }

    /**
     * Test to ensure an oversized signature part is rejected as a clean fault, not OOM. The oversized part is
     * generated lazily via a repeating-byte stream so the test never materializes the oversized content as a
     * String or byte array.
     */
    @Test
    public void oversizedSignaturePartIsRejected() {
        String boundary = "testboundary123";
        String contentType = MimeUtils.mpMixedContentType(boundary);
        ProxyMessageDecoder decoder = createDecoder(contentType);
        long oversizedLength = MAX_SIGNATURE_PART_BYTES + 1;
        InputStream message = buildOversizedSignatureMessage(boundary, "x".repeat(100), "y".repeat(100), oversizedLength);
        XrdRuntimeException ex = assertThrows(XrdRuntimeException.class, () -> decoder.parse(message));
        assertEquals(IO_ERROR.code(), ex.getErrorCode());
    }

    private static final String SOAP_MESSAGE = """
            <?xml version="1.0" encoding="utf-8"?>
            <SOAP-ENV:Envelope
                    xmlns:SOAP-ENV="http://schemas.xmlsoap.org/soap/envelope/"
                    xmlns:xroad="http://x-road.eu/xsd/xroad.xsd"
                    xmlns:id="http://x-road.eu/xsd/identifiers">
                <SOAP-ENV:Header>
                    <xroad:protocolVersion>4.0</xroad:protocolVersion>
                    <xroad:client id:objectType="MEMBER">
                        <id:xRoadInstance>EE</id:xRoadInstance>
                        <id:memberClass>BUSINESS</id:memberClass>
                        <id:memberCode>consumer</id:memberCode>
                    </xroad:client>
                    <xroad:service id:objectType="SERVICE">
                        <id:xRoadInstance>EE</id:xRoadInstance>
                        <id:memberClass>BUSINESS</id:memberClass>
                        <id:memberCode>producer</id:memberCode>
                        <id:serviceCode>getState</id:serviceCode>
                    </xroad:service>
                    <xroad:userId>EE:PIN:abc4567</xroad:userId>
                    <xroad:id>411d6755661409fed365ad8135f8210be07613da</xroad:id>
                    <xroad:issue/>
                </SOAP-ENV:Header>
                <SOAP-ENV:Body><xroad:getState>a</xroad:getState></SOAP-ENV:Body>
            </SOAP-ENV:Envelope>""";

    private static InputStream buildHashChainMessage(String boundary, String hashChainResult, String hashChain) {
        return buildHashChainMessage(boundary, hashChainResult, hashChain, "<sig/>");
    }

    private static InputStream buildHashChainMessage(String boundary, String hashChainResult, String hashChain,
                                                       String signatureBody) {
        var msg = buildPreSignaturePart(boundary, hashChainResult, hashChain)
                + signatureBody + "\r\n"
                + "--" + boundary + "--\r\n";
        return new ByteArrayInputStream(msg.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Builds a multipart message whose signature part is a lazily-generated repeated-byte stream of the given
     * length, so oversized signatures can be exercised without allocating the oversized content in memory.
     */
    private static InputStream buildOversizedSignatureMessage(String boundary, String hashChainResult, String hashChain,
                                                                long signatureLength) {
        byte[] header = buildPreSignaturePart(boundary, hashChainResult, hashChain).getBytes(StandardCharsets.UTF_8);
        byte[] trailer = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
        return new SequenceInputStream(Collections.enumeration(List.of(
                new ByteArrayInputStream(header),
                new RepeatingByteInputStream((byte) 'x', signatureLength),
                new ByteArrayInputStream(trailer))));
    }

    private static String buildPreSignaturePart(String boundary, String hashChainResult, String hashChain) {
        return "--" + boundary + "\r\n"
                + "Content-Type: text/xml\r\n\r\n"
                + SOAP_MESSAGE + "\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Type: " + MimeTypes.HASH_CHAIN_RESULT + "\r\n\r\n"
                + hashChainResult + "\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Type: " + MimeTypes.HASH_CHAIN + "\r\n\r\n"
                + hashChain + "\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Type: " + MimeTypes.SIGNATURE_BDOC + "\r\n\r\n";
    }

    /**
     * An InputStream that emits a fixed number of repeated bytes without allocating a backing array, so
     * oversized-input tests can exercise multi-megabyte parts with negligible heap footprint.
     */
    private static final class RepeatingByteInputStream extends InputStream {

        private final byte value;
        private long remaining;

        RepeatingByteInputStream(byte value, long length) {
            this.value = value;
            this.remaining = length;
        }

        @Override
        public int read() {
            if (remaining <= 0) {
                return -1;
            }
            remaining--;
            return value & 0xFF;
        }

        @Override
        public int read(byte[] b, int off, int len) {
            if (remaining <= 0) {
                return -1;
            }
            int toWrite = (int) Math.min(len, remaining);
            Arrays.fill(b, off, off + toWrite, value);
            remaining -= toWrite;
            return toWrite;
        }
    }

    private ProxyMessageDecoder createDecoder(String contentType) {
        return new ProxyMessageDecoder(globalConfProvider, ocspVerifierFactory, callback, contentType, true,
                getHashAlgoId());
    }

    private static InputStream getQuery(String fileName) throws Exception {
        return Files.newInputStream(Paths.get("src/test/queries/" + fileName));
    }

    private static InputStream getMessage(String fileName) throws Exception {
        return Files.newInputStream(Paths.get("src/test/proxymessages/" + fileName));
    }

    private DigestAlgorithm getHashAlgoId() {
        return Digests.DEFAULT_DIGEST_ALGORITHM;
    }

    @Getter
    private static final class DummyMessageConsumer implements ProxyMessageConsumer {

        private SoapMessageImpl soapMessage;
        private boolean hasAttachments;
        private OCSPResp ocspResponse;
        private SignatureData signature;
        private SoapFault soapFault;

        public boolean hasAttachments() {
            return hasAttachments;
        }

        @Override
        public void soap(SoapMessageImpl soap,
                         Map<String, String> additionalHeaders) {
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
                               Map<String, String> additionalHeaders) {
            this.hasAttachments = true;
        }

        @Override
        public void ocspResponse(OCSPResp resp) {
            this.ocspResponse = resp;
        }

        @Override
        public void signature(SignatureData sig) {
            this.signature = sig;
        }

        @Override
        public void fault(SoapFault fault) {
            this.soapFault = fault;
        }
    }
}
