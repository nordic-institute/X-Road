package ee.cyber.sdsb.proxy.protocol;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.TeeInputStream;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.parser.AbstractContentHandler;
import org.apache.james.mime4j.parser.MimeStreamParser;
import org.apache.james.mime4j.stream.BodyDescriptor;
import org.apache.james.mime4j.stream.MimeConfig;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.operator.DigestCalculator;
import org.eclipse.jetty.http.HttpFields;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.conf.VerificationCtx;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.message.Soap;
import ee.cyber.sdsb.common.message.SoapFault;
import ee.cyber.sdsb.common.message.SoapMessageImpl;
import ee.cyber.sdsb.common.message.SoapParserImpl;
import ee.cyber.sdsb.common.signature.SignatureData;
import ee.cyber.sdsb.common.util.CryptoUtils;
import ee.cyber.sdsb.common.util.MessageFileNames;
import ee.cyber.sdsb.common.util.MimeTypes;
import ee.cyber.sdsb.common.util.MimeUtils;
import ee.cyber.sdsb.proxy.signedmessage.Verifier;

import static ee.cyber.sdsb.common.ErrorCodes.*;

public class ProxyMessageDecoder {

    private static final Logger LOG =
            LoggerFactory.getLogger(ProxyMessageDecoder.class);

    private final ProxyMessageConsumer callback;

    /** The verifier that verifies the signature. */
    private final Verifier verifier = new Verifier();

    /** Holds the content type. */
    private final String contentType;

    /** Indicates whether fault messages are allowed. */
    private final boolean faultAllowed;

    /** Parser does the main work. */
    private MimeStreamParser parser;

    /** The signature that is read from the message*/
    private SignatureData signature = new SignatureData(null, null, null);

    public ProxyMessageDecoder(ProxyMessageConsumer callback,
            String contentType) {
        this(callback, contentType, true);
    }

    public ProxyMessageDecoder(ProxyMessageConsumer callback,
            String contentType, boolean faultAllowed) {
        LOG.debug("new ProxyMessageDecoder({})", contentType);

        this.callback = callback;
        this.contentType = contentType.toLowerCase();
        this.faultAllowed = faultAllowed;
    }

    public void parse(InputStream is) throws Exception {
        LOG.debug("parse()");

        String baseContentType = HttpFields.valueParameters(contentType, null);
        if (faultAllowed && baseContentType.equals(MimeTypes.TEXT_XML)) {
            parseFault(is);
        } else if (baseContentType.equals(MimeTypes.MULTIPART_MIXED)) {
            parseMultipart(is);
        } else {
            throw new CodedException(X_INVALID_CONTENT_TYPE,
                    "Invalid content type: %s", baseContentType);
        }
    }

    public void verify(ClientId sender, SignatureData signature,
            VerificationCtx ctx) throws Exception {
        verifier.verify(sender, signature, ctx);
    }

    private void parseFault(InputStream is) throws Exception {
        Soap soap = new SoapParserImpl().parse(is);
        if (!(soap instanceof SoapFault)) {
            throw new CodedException(X_INVALID_MESSAGE,
                    "Expected fault message, but got reqular SOAP message");
        }

        callback.fault((SoapFault) soap);
    }

    private void parseMultipart(InputStream is) throws Exception {
        // Multipart content type requires boundary!
        if (!MimeUtils.hasBoundary(contentType.toLowerCase())) {
            throw new CodedException(X_INVALID_CONTENT_TYPE,
                    "Multipart content type is missing required boundary");
        }

        MimeConfig config = new MimeConfig();
        config.setHeadlessParsing(contentType);
        parser = new MimeStreamParser(config);

        parser.setContentHandler(new ContentHandler());
        parser.parse(is);
    }

    private enum NextPart {
        OCSP, SOAP, ATTACHMENT, HASH_CHAIN_RESULT, HASH_CHAIN, SIGNATURE, NONE
    }

    private class ContentHandler extends AbstractContentHandler {
        private NextPart nextPart = NextPart.OCSP;

        @Override
        public void startMultipart(BodyDescriptor bd) {
            // Do not parse SOAP attachments in the (optional) second
            // part of the message. We will create separate parser for them.
            parser.setFlat();
        }

        @Override
        public void body(BodyDescriptor bd, InputStream is)
                throws MimeException, IOException {
            LOG.debug("body({}), next = {}", bd.getMimeType(), nextPart);

            switch (nextPart) {
            case OCSP:
                if (MimeTypes.OCSP_RESPONSE.equalsIgnoreCase(
                        bd.getMimeType())) {
                    handleOcsp(bd, is);

                    nextPart = NextPart.SOAP;
                    break;
                }
                // Fall through, OCSP response is only sent from CP to SP.
            case SOAP:
                handleSoap(bd, is);

                nextPart = NextPart.ATTACHMENT;
                break;
            case ATTACHMENT:
                if (MimeTypes.MULTIPART_MIXED.equals(
                        MimeUtils.getBaseContentType(bd.getMimeType()))) {
                    handleAttachments(bd, is);

                    nextPart = NextPart.HASH_CHAIN_RESULT;
                    break;
                }
                // Fall through, perhaps there is a hash chain result.
            case HASH_CHAIN_RESULT:
                if (MimeTypes.HASH_CHAIN_RESULT.equalsIgnoreCase(
                        bd.getMimeType())) {
                    try {
                        handleHashChainResult(bd, is);
                    } catch (Exception e) {
                        throw translateException(e);
                    }

                    nextPart = NextPart.HASH_CHAIN;
                    break;
                }
                // Fall through, perhaps there is a hash chain.
            case HASH_CHAIN:
                if (MimeTypes.HASH_CHAIN.equalsIgnoreCase(bd.getMimeType())) {
                    try {
                        handleHashChain(bd, is);
                    } catch (Exception e) {
                        throw translateException(e);
                    }

                    nextPart = NextPart.SIGNATURE;
                    break;
                }
                // Otherwise it was signature after all. Fall through the case.
            case SIGNATURE:
                try {
                    handleSignature(bd, is);
                } catch (Exception e) {
                    throw translateException(e);
                }

                // We are not expecting anything more.
                nextPart = NextPart.NONE;
                break;
            case NONE:
                throw new CodedException(X_INVALID_MESSAGE,
                        "Extra content (%s) after signature", bd.getMimeType());
            }
        }
    }

    private void handleOcsp(BodyDescriptor bd, InputStream is) {
        try {
            LOG.debug("Looking for OCSP, got: {} {}", bd.getMimeType(),
                    bd.getCharset());
            byte[] buffer = IOUtils.toByteArray(is);
            OCSPResp response = new OCSPResp(buffer);
            callback.ocspResponse(response);
        } catch (Exception ex) {
            throw translateException(ex);
        }
    }

    private void handleSoap(BodyDescriptor bd, InputStream is) {
        try {
            LOG.debug("Looking for SOAP, got: {}, {}", bd.getMimeType(),
                    bd.getCharset());

            if (!MimeTypes.TEXT_XML.equalsIgnoreCase(bd.getMimeType())) {
                throw new CodedException(X_INVALID_CONTENT_TYPE,
                        "Invalid content type for SOAP message: %s",
                        bd.getMimeType());
            }

            Soap soap = new SoapParserImpl().parse(bd.getMimeType(),
                    bd.getCharset(), is);
            if (soap instanceof SoapFault) {
                callback.fault((SoapFault) soap);
            } else {
                callback.soap((SoapMessageImpl) soap);

                verifier.addHash(MessageFileNames.MESSAGE, getHashAlgoId(),
                        ((SoapMessageImpl) soap).getBytes());
            }
        } catch (Exception ex) {
            throw translateException(ex);
        }
    }

    private void handleAttachments(BodyDescriptor bd, InputStream is)
            throws MimeException, IOException {
        LOG.debug("Found attachments: {}, {}", bd.getMimeType(),
                bd.getBoundary());
        // Parse attachments via separate MimeStreamParser.
        parseAttachments(MimeUtils.mpMixedContentType(bd.getBoundary()), is);
    }

    private void parseAttachments(String contentType, InputStream is)
            throws MimeException, IOException {
        MimeConfig config = new MimeConfig();
        config.setHeadlessParsing(contentType);

        final MimeStreamParser attachmentParser = new MimeStreamParser(config);
        attachmentParser.setContentHandler(new AbstractContentHandler() {
            private int attachmentNo = 0;

            @Override
            public void startMultipart(BodyDescriptor bd) {
                attachmentParser.setFlat();
            }

            @Override
            public void body(BodyDescriptor bd, InputStream is)
                    throws IOException {
                LOG.debug("attachment body: {}", bd.getMimeType());
                try {
                    DigestCalculator dc =
                            CryptoUtils.createDigestCalculator(getHashAlgoId());
                    TeeInputStream proxyIs =
                            new TeeInputStream(is, dc.getOutputStream(), true);

                    callback.attachment(bd.getMimeType(), proxyIs, null);

                    verifier.addHash(
                            MessageFileNames.attachment(++attachmentNo),
                            getHashAlgoId(), dc.getDigest());
                } catch (Exception ex) {
                    throw translateException(ex);
                }
            }
        });

        attachmentParser.parse(is);
    }

    private void handleHashChainResult(BodyDescriptor bd, InputStream is)
            throws Exception {
        LOG.debug("handleHashChainResult()");

        String hashChainResult = IOUtils.toString(is);
        LOG.trace("HashChainResult: {}", hashChainResult);

        signature = new SignatureData(null, hashChainResult, null);
    }

    private void handleHashChain(BodyDescriptor bd, InputStream is)
            throws Exception {
        LOG.debug("handleHashChain()");

        String hashChain = IOUtils.toString(is);
        LOG.trace("HashChain: {}", hashChain);

        signature = new SignatureData(null, signature.getHashChainResult(),
                hashChain);
    }

    private void handleSignature(BodyDescriptor bd, InputStream is)
            throws Exception {
        LOG.debug("Looking for signature, got '{}'", bd.getMimeType());

        switch (bd.getMimeType() == null
                ? "" : bd.getMimeType().toLowerCase()) {
        case MimeTypes.SIGNATURE_BDOC:
            // We got signature, just as expected.
            // TODO: charset
            Charset charset = StandardCharsets.UTF_8;
            signature = new SignatureData(IOUtils.toString(is, charset),
                    signature.getHashChainResult(), signature.getHashChain());
            callback.signature(signature);
            break;
        case MimeTypes.TEXT_XML:
            LOG.debug("Got fault instead of signature");
            // It seems that signing failed and the other
            // party sent SOAP fault instead of signature.

            // Parse the fault message.
            Soap soap = new SoapParserImpl().parse(bd.getMimeType(), is);
            if (soap instanceof SoapFault) {
                callback.fault((SoapFault) soap);
                return; // The nextPart will be set to NONE
            }
            // If not fault message, fall through to
            // invalid message case.
        default:
            // Um, not what we expected.
            // The error reporting must use exceptions, otherwise
            // the parsing is not interrupted.
            throw new CodedException(X_INVALID_CONTENT_TYPE,
                    "Received invalid content type instead of signature: %s",
                    bd.getMimeType());
        }
    }

    private static String getHashAlgoId() {
        // TODO: make hash function configurable?
        return CryptoUtils.SHA512_ID;
    }
}
