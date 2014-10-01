package ee.cyber.sdsb.proxy.protocol;

import java.io.IOException;
import java.io.InputStream;

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
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.message.Soap;
import ee.cyber.sdsb.common.message.SoapFault;
import ee.cyber.sdsb.common.message.SoapMessageImpl;
import ee.cyber.sdsb.common.message.SoapParserImpl;
import ee.cyber.sdsb.common.signature.SignatureData;
import ee.cyber.sdsb.common.util.CryptoUtils;
import ee.cyber.sdsb.common.util.MessageFileNames;
import ee.cyber.sdsb.common.util.MimeUtils;
import ee.cyber.sdsb.proxy.signedmessage.Verifier;

import static ee.cyber.sdsb.common.ErrorCodes.*;
import static ee.cyber.sdsb.common.util.MimeTypes.*;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jetty.http.MimeTypes.TEXT_XML;

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

    /** The hash algorithm id used when hashing parts. */
    private final String hashAlgoId;

    /** Parser does the main work. */
    private MimeStreamParser parser;

    /** The signature that is read from the message*/
    private SignatureData signature = new SignatureData(null, null, null);

    public ProxyMessageDecoder(ProxyMessageConsumer callback,
            String contentType, String hashAlgoId) {
        this(callback, contentType, true, hashAlgoId);
    }

    public ProxyMessageDecoder(ProxyMessageConsumer callback,
            String contentType, boolean faultAllowed, String hashAlgoId) {
        LOG.trace("new ProxyMessageDecoder({}, {})", contentType, hashAlgoId);

        this.callback = callback;
        this.contentType = contentType;
        this.faultAllowed = faultAllowed;
        this.hashAlgoId = hashAlgoId;
    }

    public void parse(InputStream is) throws Exception {
        LOG.trace("parse()");

        String baseContentType = HttpFields.valueParameters(contentType, null);
        if (faultAllowed && baseContentType.equalsIgnoreCase(TEXT_XML)) {
            parseFault(is);
        } else if (baseContentType.equalsIgnoreCase(MULTIPART_MIXED)) {
            parseMultipart(is);
        } else {
            throw new CodedException(X_INVALID_CONTENT_TYPE,
                    "Invalid content type: %s", baseContentType);
        }
    }

    public void verify(ClientId sender, SignatureData signature)
            throws Exception {
        verifier.verify(sender, signature);
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
            LOG.trace("body({}), next = {}", bd.getMimeType(), nextPart);

            switch (nextPart) {
            case OCSP:
                if (OCSP_RESPONSE.equalsIgnoreCase(bd.getMimeType())) {
                    handleOcsp(bd, is);
                    break;
                }
                // $FALL-THROUGH$ OCSP response is only sent from CP to SP.
            case SOAP:
                handleSoap(bd, is);

                nextPart = NextPart.ATTACHMENT;
                break;
            case ATTACHMENT:
                if (MULTIPART_MIXED.equals(
                        MimeUtils.getBaseContentType(bd.getMimeType()))) {
                    handleAttachments(bd, is);

                    nextPart = NextPart.HASH_CHAIN_RESULT;
                    break;
                }
                // $FALL-THROUGH$ perhaps there is a hash chain result.
            case HASH_CHAIN_RESULT:
                if (HASH_CHAIN_RESULT.equalsIgnoreCase(bd.getMimeType())) {
                    try {
                        handleHashChainResult(is);
                    } catch (Exception e) {
                        throw translateException(e);
                    }

                    nextPart = NextPart.HASH_CHAIN;
                    break;
                }
                // $FALL-THROUGH$ perhaps there is a hash chain.
            case HASH_CHAIN:
                if (HASH_CHAIN.equalsIgnoreCase(bd.getMimeType())) {
                    try {
                        handleHashChain(is);
                    } catch (Exception e) {
                        throw translateException(e);
                    }

                    nextPart = NextPart.SIGNATURE;
                    break;
                }
                // $FALL-THROUGH$ Otherwise it was signature after all. Fall through the case.
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
            LOG.trace("Looking for OCSP, got: {} {}", bd.getMimeType(),
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
            LOG.trace("Looking for SOAP, got: {}, {}", bd.getMimeType(),
                    bd.getCharset());

            if (!TEXT_XML.equalsIgnoreCase(bd.getMimeType())) {
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

                verifier.addPart(MessageFileNames.MESSAGE, getHashAlgoId(),
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
                LOG.trace("attachment body: {}", bd.getMimeType());
                try {
                    DigestCalculator dc =
                            CryptoUtils.createDigestCalculator(getHashAlgoId());
                    TeeInputStream proxyIs =
                            new TeeInputStream(is, dc.getOutputStream(), true);

                    callback.attachment(bd.getMimeType(), proxyIs, null);

                    verifier.addPart(
                            MessageFileNames.attachment(++attachmentNo),
                            getHashAlgoId(), dc.getDigest());
                } catch (Exception ex) {
                    throw translateException(ex);
                }
            }
        });

        attachmentParser.parse(is);
    }

    private void handleHashChainResult(InputStream is) throws Exception {
        LOG.trace("handleHashChainResult()");

        String hashChainResult = IOUtils.toString(is, UTF_8);
        LOG.trace("HashChainResult: {}", hashChainResult);

        signature = new SignatureData(null, hashChainResult, null);
    }

    private void handleHashChain(InputStream is) throws Exception {
        LOG.trace("handleHashChain()");

        String hashChain = IOUtils.toString(is, UTF_8);
        LOG.trace("HashChain: {}", hashChain);

        signature = new SignatureData(null, signature.getHashChainResult(),
                hashChain);
    }

    private void handleSignature(BodyDescriptor bd, InputStream is)
            throws Exception {
        LOG.trace("Looking for signature, got '{}'", bd.getMimeType());

        switch (bd.getMimeType() == null
                ? "" : bd.getMimeType().toLowerCase()) {
        case SIGNATURE_BDOC:
            // We got signature, just as expected.
            signature = new SignatureData(IOUtils.toString(is, UTF_8),
                    signature.getHashChainResult(), signature.getHashChain());
            callback.signature(signature);
            break;
        case TEXT_XML:
            LOG.debug("Got fault instead of signature");
            // It seems that signing failed and the other
            // party sent SOAP fault instead of signature.

            // Parse the fault message.
            Soap soap = new SoapParserImpl().parse(bd.getMimeType(), is);
            if (soap instanceof SoapFault) {
                callback.fault((SoapFault) soap);
                return; // The nextPart will be set to NONE
            }
            // $FALL-THROUGH$ If not fault message, fall through to invalid message case.
        default:
            // Um, not what we expected.
            // The error reporting must use exceptions, otherwise
            // the parsing is not interrupted.
            throw new CodedException(X_INVALID_CONTENT_TYPE,
                    "Received invalid content type instead of signature: %s",
                    bd.getMimeType());
        }
    }

    private String getHashAlgoId() {
        if (hashAlgoId == null) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "Could not get hash algorithm identifier from message");
        }

        return hashAlgoId;
    }

}
