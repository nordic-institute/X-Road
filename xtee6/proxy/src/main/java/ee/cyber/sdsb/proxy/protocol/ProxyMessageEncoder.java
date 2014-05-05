package ee.cyber.sdsb.proxy.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.apache.commons.io.input.TeeInputStream;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.operator.DigestCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.ErrorCodes;
import ee.cyber.sdsb.common.message.SoapFault;
import ee.cyber.sdsb.common.message.SoapMessageImpl;
import ee.cyber.sdsb.common.signature.SignatureData;
import ee.cyber.sdsb.common.util.CryptoUtils;
import ee.cyber.sdsb.common.util.MessageFileNames;
import ee.cyber.sdsb.common.util.MimeTypes;
import ee.cyber.sdsb.common.util.MultipartEncoder;
import ee.cyber.sdsb.proxy.conf.SigningCtx;
import ee.cyber.sdsb.proxy.signedmessage.Signer;

import static ee.cyber.sdsb.common.util.CryptoUtils.createDigestCalculator;
import static ee.cyber.sdsb.common.util.MimeUtils.TEXT_XML_UTF8;
import static ee.cyber.sdsb.common.util.MimeUtils.toHeaders;

public class ProxyMessageEncoder implements ProxyMessageConsumer {

    private static final Logger LOG =
            LoggerFactory.getLogger(ProxyMessageEncoder.class);

    private final MultipartEncoder mpEncoder;

    private final Signer signer = new Signer();

    private final String topBoundary;
    private final String attachmentBoundary;

    private int attachmentNo = 1;
    private boolean inAttachmentPart = false;

    /**
     * Creates the encoder instance.
     * @param out Writer that will receive the encoded message.
     */
    public ProxyMessageEncoder(OutputStream out) throws Exception {
        String uniquePart = System.identityHashCode(this) +
                Long.toString(System.currentTimeMillis(), 36);

        topBoundary = "xtop" + uniquePart;
        attachmentBoundary = "xatt" + uniquePart;

        mpEncoder = new MultipartEncoder(out, topBoundary);
    }

    /** Returns content type for the encoded message. */
    public String getContentType() {
        return mpEncoder.getContentType();
    }

    @Override
    public void ocspResponse(OCSPResp resp) throws Exception {
        byte[] responseEncoded = resp.getEncoded();

        LOG.debug("writeOcspResponse({} bytes)", responseEncoded.length);

        try {
            mpEncoder.startPart(MimeTypes.OCSP_RESPONSE);
            mpEncoder.write(responseEncoded);
        } catch (Exception ex) {
            throw ErrorCodes.translateException(ex);
        }
    }

    @Override
    public void signature(SignatureData signature) throws Exception {
        LOG.debug("signature()");

        endAttachments();

        if (signature.isBatchSignature()) {
            hashChain(signature.getHashChainResult(), signature.getHashChain());
        }

        signature(signature.getSignatureXml());
    }

    @Override
    public void soap(SoapMessageImpl message) throws Exception {
        String soapMessage = message.getXml();

        LOG.debug("writeSoapMessage({})", soapMessage);

        byte[] data = message.getBytes();
        try {
            // TODO: handle xml+xop!
            mpEncoder.startPart(TEXT_XML_UTF8);
            mpEncoder.write(data);

            signer.addPart(MessageFileNames.MESSAGE, getHashAlgoId(), data);
        } catch (Exception ex) {
            throw ErrorCodes.translateException(ex);
        }
    }

    @Override
    public void attachment(String contentType, InputStream content,
            Map<String, String> additionalHeaders) throws Exception {
        LOG.debug("writeAttachment({})", contentType);

        if (!inAttachmentPart) {
            mpEncoder.startNested(attachmentBoundary);
            inAttachmentPart = true;
        }

        DigestCalculator calc = createDigestCalculator(getHashAlgoId());
        TeeInputStream proxyIs =
                new TeeInputStream(content, calc.getOutputStream(), true);

        mpEncoder.startPart(contentType, toHeaders(additionalHeaders));
        mpEncoder.write(proxyIs);
        signer.addPart(MessageFileNames.attachment(attachmentNo++),
                getHashAlgoId(), calc.getDigest());
    }

    @Override
    public void fault(SoapFault fault) throws Exception {
        fault(fault.getXml());
    }

    public void fault(String faultXml) throws Exception {
        LOG.debug("writeFault({})", faultXml);

        // We assume that the SOAP message is already sent.
        // Therefore, we will write the message either before or after
        // attachments. The signature will also presumably arrive
        // as one chunk.
        if (!inAttachmentPart) {
            // We arrived here either before attachments or before signature.
            // In any case, it is a good time to write a SOAP error message.
        } else {
            // We were currently streaming attachments. Just close the
            // part and write the message.
            endAttachments();
        }

        mpEncoder.startPart(TEXT_XML_UTF8);
        mpEncoder.write(faultXml.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Signs all the parts and writes the signature to stream.
     * Call after adding SOAP message and attachments.
     */
    public void sign(SigningCtx securityCtx) throws Exception {
        LOG.debug("sign()");

        endAttachments();

        signer.sign(securityCtx);

        // If the signature is a batch signature, then encode the
        // hash chain result and corresponding hash chain immediately before
        // the signature document.
        SignatureData sd = signer.getSignatureData();
        if (sd.isBatchSignature()) {
            hashChain(sd.getHashChainResult(), sd.getHashChain());
        }

        signature(sd.getSignatureXml());
    }

    /** Closes the writer and flushes streams. */
    public void close() throws IOException {
        LOG.debug("close()");

        endAttachments();

        mpEncoder.close();
    }

    private void signature(String signatureData) throws Exception {
        mpEncoder.startPart(MimeTypes.SIGNATURE_BDOC);
        mpEncoder.write(new StringReader(signatureData));
    }

    private void hashChain(String hashChainResult, String hashChain)
            throws Exception {
        mpEncoder.startPart(MimeTypes.HASH_CHAIN_RESULT);
        mpEncoder.write(new StringReader(hashChainResult));

        mpEncoder.startPart(MimeTypes.HASH_CHAIN);
        mpEncoder.write(new StringReader(hashChain));
    }

    protected void endAttachments() throws IOException {
        LOG.debug("endAttachments()");

        if (inAttachmentPart) {
            mpEncoder.endNested();
            inAttachmentPart = false;
        }
    }

    private static String getHashAlgoId() {
        // TODO: make hash function configurable?
        return CryptoUtils.DEFAULT_DIGEST_ALGORITHM_ID;
    }
}
