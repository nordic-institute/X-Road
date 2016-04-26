/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.input.TeeInputStream;
import org.apache.james.mime4j.MimeException;
import org.apache.james.mime4j.parser.AbstractContentHandler;
import org.apache.james.mime4j.parser.MimeStreamParser;
import org.apache.james.mime4j.stream.BodyDescriptor;
import org.apache.james.mime4j.stream.Field;
import org.apache.james.mime4j.stream.MimeConfig;
import org.bouncycastle.cert.ocsp.OCSPResp;
import org.bouncycastle.operator.DigestCalculator;
import org.eclipse.jetty.http.HttpFields;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.message.Soap;
import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.message.SoapParserImpl;
import ee.ria.xroad.common.signature.SignatureData;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.MessageFileNames;
import ee.ria.xroad.common.util.MimeUtils;
import ee.ria.xroad.proxy.signedmessage.Verifier;

import static ee.ria.xroad.common.ErrorCodes.*;
import static ee.ria.xroad.common.util.MimeTypes.*;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_CONTENT_TYPE;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.eclipse.jetty.http.MimeTypes.TEXT_XML;

/**
 * Decodes proxy SOAP messages from an input stream.
 */
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

    /**
     * Construct a message decoder.
     * @param callback the callback executed on the decoded message
     * @param contentType expected content type of the input stream
     * @param hashAlgoId hash algorithm id used when hashing parts
     */
    public ProxyMessageDecoder(ProxyMessageConsumer callback,
            String contentType, String hashAlgoId) {
        this(callback, contentType, true, hashAlgoId);
    }

    /**
     * Construct a message decoder.
     * @param callback the callback executed on the decoded message
     * @param contentType expected content type of the input stream
     * @param faultAllowed whether a SOAP fault should be parsed or an exception
     * should be thrown
     * @param hashAlgoId hash algorithm id used when hashing parts
     */
    public ProxyMessageDecoder(ProxyMessageConsumer callback,
            String contentType, boolean faultAllowed, String hashAlgoId) {
        LOG.trace("new ProxyMessageDecoder({}, {})", contentType, hashAlgoId);

        this.callback = callback;
        this.contentType = contentType;
        this.faultAllowed = faultAllowed;
        this.hashAlgoId = hashAlgoId;
    }

    /**
     * Attempts to decode the proxy SOAP message from the given input stream.
     * @param is input stream from which to decode the message
     * @throws Exception if the stream content type does not match the expected one
     */
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

    /**
     * Verifies that the signature matches the sender.
     * @param sender the sender
     * @param signatureData the signature
     * @throws Exception in case verification fails
     */
    public void verify(ClientId sender, SignatureData signatureData)
            throws Exception {
        verifier.verify(sender, signatureData);
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
            default:
                throw new IllegalArgumentException("Unexpected next body part: "
                        + nextPart);
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

    private void parseAttachments(String attachmentContentType, InputStream is)
            throws MimeException, IOException {
        MimeConfig config = new MimeConfig();
        config.setHeadlessParsing(attachmentContentType);

        final MimeStreamParser attachmentParser = new MimeStreamParser(config);
        attachmentParser.setContentHandler(new AbstractContentHandler() {
            private int attachmentNo = 0;
            private Map<String, String> headers;

            @Override
            public void startHeader() throws MimeException {
                headers = new HashMap<>();
            }

            @Override
            public void field(Field field) throws MimeException {
                if (!field.getName().toLowerCase().equals(
                        HEADER_CONTENT_TYPE)) {
                    headers.put(field.getName(), field.getBody());
                }
            }

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

                    callback.attachment(bd.getMimeType(), proxyIs, headers);

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
