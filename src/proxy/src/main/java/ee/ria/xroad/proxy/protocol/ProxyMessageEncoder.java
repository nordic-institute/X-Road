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

import ee.ria.xroad.common.message.RestRequest;
import ee.ria.xroad.common.message.RestResponse;
import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.signature.SignatureData;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.MessageFileNames;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.common.util.MultipartEncoder;
import ee.ria.xroad.proxy.conf.SigningCtx;
import ee.ria.xroad.proxy.signedmessage.Signer;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.input.TeeInputStream;
import org.apache.commons.io.output.CountingOutputStream;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.operator.DigestCalculator;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static ee.ria.xroad.common.ErrorCodes.translateException;
import static ee.ria.xroad.common.util.CryptoUtils.createDigestCalculator;
import static ee.ria.xroad.common.util.MimeUtils.randomBoundary;
import static ee.ria.xroad.common.util.MimeUtils.toHeaders;

/**
 * Encodes proxy SOAP messages from an output stream.
 */
@Slf4j
public class ProxyMessageEncoder implements ProxyMessageConsumer {

    private final String hashAlgoId;

    private final MultipartEncoder mpEncoder;

    private final Signer signer = new Signer();

    private final String topBoundary;
    private final String attachmentBoundary;

    private int attachmentNo = 0;
    private boolean inAttachmentPart = false;

    @Getter
    private long attachmentsByteCount = 0;
    @Getter
    private byte[] restBodyDigest;

    /**
     * Creates the encoder instance.
     *
     * @param out        Writer that will receive the encoded message.
     * @param hashAlgoId hash algorithm id used when hashing parts
     * @throws IllegalArgumentException if hashAlgoId is null
     */
    public ProxyMessageEncoder(OutputStream out, String hashAlgoId)
            throws IllegalArgumentException {
        this.hashAlgoId = hashAlgoId;

        if (hashAlgoId == null) {
            throw new IllegalArgumentException(
                    "Hash algorithm id cannot be null");
        }

        String uniquePart = randomBoundary();

        topBoundary = "xtop" + uniquePart;
        attachmentBoundary = "xatt" + uniquePart;

        mpEncoder = new MultipartEncoder(out, topBoundary);
    }

    /**
     * Creates the encoder instance.
     *
     * @param out        Writer that will receive the encoded message.
     * @param hashAlgoId hash algorithm id used when hashing parts
     * @throws IllegalArgumentException if hashAlgoId is null
     */
    public ProxyMessageEncoder(OutputStream out, String hashAlgoId, String topBoundary)
            throws IllegalArgumentException {
        this.hashAlgoId = hashAlgoId;

        if (hashAlgoId == null) {
            throw new IllegalArgumentException(
                    "Hash algorithm id cannot be null");
        }

        this.topBoundary = topBoundary;
        attachmentBoundary = "xatt" + randomBoundary();
        mpEncoder = new MultipartEncoder(out, topBoundary);
    }


    /**
     * @return content type for the encoded message
     */
    public String getContentType() {
        return mpEncoder.getContentType();
    }

    /**
     * @return the signature
     */
    public SignatureData getSignature() {
        return signer.getSignatureData();
    }

    @Override
    public void ocspResponse(OCSPResp resp) {
        try {
            byte[] responseEncoded = resp.getEncoded();
            log.trace("writeOcspResponse({} bytes)", responseEncoded.length);
            mpEncoder.startPart(MimeTypes.OCSP_RESPONSE);
            mpEncoder.write(responseEncoded);
        } catch (Exception ex) {
            throw translateException(ex);
        }
    }

    @Override
    public void signature(SignatureData signature) throws Exception {
        log.trace("signature()");

        endAttachments();

        if (signature.isBatchSignature()) {
            hashChain(signature.getHashChainResult(), signature.getHashChain());
        }

        signature(signature.getSignatureXml());
    }

    /**
     * Encode rest request
     */
    public void restRequest(RestRequest request) {
        try {
            final byte[] message = request.getMessageBytes();
            signer.addPart(MessageFileNames.MESSAGE,
                    hashAlgoId,
                    request.getHash(),
                    message);

            mpEncoder.startPart("application/x-road-rest-request");
            mpEncoder.write(message);

        } catch (Exception ex) {
            throw translateException(ex);
        }
    }

    /**
     * Encode rest message body
     *
     * @param content
     * @throws Exception
     */
    @Override
    public void restBody(InputStream content) throws Exception {
        restBody(new byte[0], 0, content);
    }

    /**
     * Encode rest message body
     * @param head first byte(s)
     * @param rest rest of the body
     * @throws Exception
     */
    public void restBody(byte[] head, int count, InputStream rest) throws Exception {
        final DigestCalculator calc = createDigestCalculator(hashAlgoId);
        final CountingOutputStream cos = new CountingOutputStream(calc.getOutputStream());

        cos.write(head, 0, count);
        final TeeInputStream proxyIs = new TeeInputStream(rest, cos, true);

        mpEncoder.startPart("application/x-road-rest-body");
        mpEncoder.write(head, 0, count);
        mpEncoder.write(proxyIs);

        restBodyDigest = calc.getDigest();
        signer.addPart(MessageFileNames.attachment(++attachmentNo), hashAlgoId, restBodyDigest);
        attachmentsByteCount += cos.getByteCount();
    }

    /**
     * Encode rest response (without body)
     */
    public void restResponse(RestResponse response) {
        try {
            final byte[] message = response.getMessageBytes();
            signer.addPart(MessageFileNames.MESSAGE,
                    hashAlgoId,
                    CryptoUtils.calculateDigest(hashAlgoId, message),
                    message);

            mpEncoder.startPart("application/x-road-rest-response");
            mpEncoder.write(message);

        } catch (Exception ex) {
            throw translateException(ex);
        }
    }

    @Override
    public void soap(SoapMessageImpl message,
            Map<String, String> additionalHeaders) throws Exception {
        if (log.isTraceEnabled()) {
            log.trace("writeSoapMessage({})", message.getXml());
        }

        byte[] data = message.getBytes();
        try {
            mpEncoder.startPart(message.getContentType(),
                    toHeaders(additionalHeaders));
            mpEncoder.write(data);

            signer.addMessagePart(hashAlgoId, message);
        } catch (Exception ex) {
            throw translateException(ex);
        }
    }

    @Override
    public void attachment(String contentType, InputStream content,
            Map<String, String> additionalHeaders) throws Exception {
        log.trace("writeAttachment({})", contentType);

        if (!inAttachmentPart) {
            mpEncoder.startNested(attachmentBoundary);
            inAttachmentPart = true;
        }

        DigestCalculator calc = createDigestCalculator(hashAlgoId);
        CountingOutputStream cos = new CountingOutputStream(
                calc.getOutputStream());
        TeeInputStream proxyIs = new TeeInputStream(content, cos, true);

        mpEncoder.startPart(contentType, toHeaders(additionalHeaders));
        mpEncoder.write(proxyIs);

        attachmentsByteCount += cos.getByteCount();

        signer.addPart(MessageFileNames.attachment(++attachmentNo),
                hashAlgoId, calc.getDigest());
    }

    @Override
    public void fault(SoapFault fault) throws Exception {
        fault(fault.getXml());
    }

    /**
     * Write the SOAP fault XML string to the output stream.
     *
     * @param faultXml SOAP fault XML string
     * @throws Exception in case of any errors
     */
    public void fault(String faultXml) throws Exception {
        log.trace("writeFault({})", faultXml);

        // We assume that the SOAP message is already sent.
        // Therefore, we will write the message either before or after
        // attachments. The signature will also presumably arrive
        // as one chunk.
        if (inAttachmentPart) {
            // We were currently streaming attachments. Just close the
            // part and write the message.
            endAttachments();
        }

        // We arrived here either before attachments or before signature.
        // In any case, it is a good time to write a SOAP error message.
        mpEncoder.startPart(MimeTypes.TEXT_XML_UTF8);
        mpEncoder.write(faultXml.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Signs all the parts. Call after adding SOAP message and attachments.
     *
     * @param securityCtx signing context to use when signing the parts
     * @throws Exception in case of any errors
     */
    public void sign(SigningCtx securityCtx) throws Exception {
        log.trace("sign()");

        signer.sign(securityCtx);
    }

    /**
     * Writes the signature to stream. Call after sing().
     *
     * @throws IOException in case of any errors
     */
    public void writeSignature() throws IOException {
        log.trace("writeSignature()");

        endAttachments();

        // If the signature is a batch signature, then encode the
        // hash chain result and corresponding hash chain immediately before
        // the signature document.
        SignatureData sd = signer.getSignatureData();

        if (sd.isBatchSignature()) {
            hashChain(sd.getHashChainResult(), sd.getHashChain());
        }

        signature(sd.getSignatureXml());
    }

    /**
     * Closes the writer and flushes streams.
     *
     * @throws IOException if an I/O error occurred
     */
    public void close() throws IOException {
        log.trace("close()");

        endAttachments();

        mpEncoder.close();
    }

    private void signature(String signatureData) throws IOException {
        mpEncoder.startPart(MimeTypes.SIGNATURE_BDOC);
        mpEncoder.write(signatureData.getBytes(StandardCharsets.UTF_8));
    }

    private void hashChain(String hashChainResult, String hashChain) throws IOException {
        mpEncoder.startPart(MimeTypes.HASH_CHAIN_RESULT);
        mpEncoder.write(hashChainResult.getBytes(StandardCharsets.UTF_8));

        mpEncoder.startPart(MimeTypes.HASH_CHAIN);
        mpEncoder.write(hashChain.getBytes(StandardCharsets.UTF_8));
    }

    protected void endAttachments() throws IOException {
        log.trace("endAttachments()");

        if (inAttachmentPart) {
            mpEncoder.endNested();
            inAttachmentPart = false;
        }
    }

    public int getAttachmentCount() {
        return attachmentNo;
    }

}
