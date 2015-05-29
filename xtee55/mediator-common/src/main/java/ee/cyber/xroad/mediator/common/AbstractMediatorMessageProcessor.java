package ee.cyber.xroad.mediator.common;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.output.CountingOutputStream;
import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;

import ee.cyber.xroad.mediator.IdentifierMapping;
import ee.cyber.xroad.mediator.IdentifierMappingProvider;
import ee.cyber.xroad.mediator.message.MessageEncoder;
import ee.cyber.xroad.mediator.message.MessageVersion;
import ee.cyber.xroad.mediator.message.MultipartMessageEncoder;
import ee.cyber.xroad.mediator.message.SoapMessageConverter;
import ee.cyber.xroad.mediator.message.SoapMessageEncoder;
import ee.cyber.xroad.mediator.message.SoapParserImpl;
import ee.cyber.xroad.mediator.util.IOPipe;
import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.message.AbstractSoapMessage;
import ee.ria.xroad.common.message.SoapFault;
import ee.ria.xroad.common.message.SoapMessage;
import ee.ria.xroad.common.message.SoapMessageDecoder;
import ee.ria.xroad.common.util.AsyncHttpSender;
import ee.ria.xroad.common.util.CachingStream;

import static ee.ria.xroad.common.ErrorCodes.*;
import static ee.ria.xroad.common.util.AbstractHttpSender.CHUNKED_LENGTH;
import static ee.ria.xroad.common.util.MimeTypes.MULTIPART_RELATED;
import static ee.ria.xroad.common.util.MimeUtils.getBaseContentType;
import static org.apache.commons.io.IOUtils.closeQuietly;

/**
 * Base class for mediator message processors.
 */
@Slf4j
public abstract class AbstractMediatorMessageProcessor
        implements MediatorMessageProcessor {

    private static final int SEND_TIMEOUT_SECONDS = 120; // TODO Make configurable

    protected final IOPipe requestPipe = new IOPipe();

    protected final String target;
    protected final HttpClientManager httpClientManager;

    protected AsyncHttpSender sender;
    private InputStream cachedRequest;

    protected MessageVersion inboundRequestVersion;
    protected MessageVersion inboundResponseVersion;
    protected MessageVersion outboundRequestVersion;

    protected Class<?> inboundRequestHeaderClass;

    protected AbstractMediatorMessageProcessor(String target,
            HttpClientManager httpClientManager) throws Exception {
        this.target = stripSlash(target);
        this.httpClientManager = httpClientManager;

        GlobalConf.verifyValidity();
        GlobalConf.initForCurrentThread();
    }

    @Override
    public void process(final MediatorRequest request,
            final MediatorResponse response) throws Exception {
        try {
            processRequest(request);

            verifyRequest();

            waitForResponse();

            processResponse(response);
        } finally {
            closeQuietly(sender);
            closeQuietly(cachedRequest);
        }
    }

    void processRequest(final MediatorRequest request) throws Exception {
        log.trace("processRequest({})", request.getContentType());

        RequestDecoderCallback cb = new RequestDecoderCallback(request);

        parseMessage(request.getContentType(), request.getInputStream(), cb);

        if (cb.sendWithHttp10) {
            cb.encoder.close();
            log.trace("Sending cached message with length {}",
                    cb.counter.getByteCount());
            // Start the sending operation. Cache will be closed when the
            // response is received.
            cachedRequest = cb.cache.getCachedContents();
            cb.startSending(cachedRequest,
                    cb.counter.getByteCount());
        }
    }

    private class RequestDecoderCallback extends MessageDecoderCallback {
        MediatorRequest request;
        boolean sendWithHttp10 = false;
        SoapMessage outboundRequestMessage;
        CachingStream cache;
        CountingOutputStream counter;

        RequestDecoderCallback(MediatorRequest request) {
            this.request = request;
        }

        @Override
        public void soap(SoapMessage message) throws Exception {
            log.trace("soap({})", message.getXml());

            inboundRequestVersion = MessageVersion.fromMessage(message);
            inboundRequestHeaderClass = getHeaderClass(message);

            outboundRequestMessage = getOutboundRequestMessage(message);

            outboundRequestVersion =
                    MessageVersion.fromMessage(outboundRequestMessage);

            sendWithHttp10 = shouldSendWithHttp10(outboundRequestMessage);
            if (sendWithHttp10) {
                log.trace("Sending request with HTTP 1.0");
                cache = new CachingStream();
                counter = new CountingOutputStream(cache);
                encoder = getEncoder(counter);
            } else {
                log.trace("Sending request with HTTP 1.1 chunked encoding");
                encoder = getEncoder(requestPipe.out);
                startSending(requestPipe.in, CHUNKED_LENGTH);
            }

            encoder.soap(outboundRequestMessage);
        }

        private MessageEncoder getEncoder(OutputStream output) {
            return isMultipart(request.getContentType())
                    ? new MultipartMessageEncoder(output)
                    : new SoapMessageEncoder(output);
        }

        void startSending(InputStream content, long contentLength)
                throws Exception {
            AbstractMediatorMessageProcessor.this.startSending(
                    getTargetAddress(outboundRequestMessage),
                    encoder.getContentType(), content, contentLength);
        }
    }

    /**
     * Returns true, if given message should be sent using HTTP 1.0
     * protocol without the use of chunked encoding.
     */
    protected boolean shouldSendWithHttp10(SoapMessage message) {
        return false;
    }

    void processResponse(final MediatorResponse response) throws Exception {
        log.trace("processResponse()");

        final OutputStream out = response.getOutputStream();
        SoapMessageDecoder.Callback cb = new MessageDecoderCallback() {
            @Override
            public void soap(SoapMessage message) throws Exception {
                log.trace("soap({})", message.getXml());

                inboundResponseVersion = MessageVersion.fromMessage(message);

                SoapMessage outboundResponseMessage =
                        getOutboundResponseMessage(message);

                String responseContentType = sender.getResponseContentType();
                if (isMultipart(sender.getResponseContentType())) {
                    encoder = new MultipartMessageEncoder(out);
                    responseContentType = encoder.getContentType();
                } else {
                    encoder = new SoapMessageEncoder(out);
                }

                response.setContentType(responseContentType,
                        sender.getResponseHeaders());

                encoder.soap(outboundResponseMessage);
            }
        };

        parseMessage(sender.getResponseContentType(),
                sender.getResponseContent(), cb);
    }

    protected void startSending(URI address, String contentType,
            InputStream content, long contentLength) throws Exception {
        log.debug("startSending({}, {})", address, contentType);

        sender = createSender();

        sender.doPost(address, content, contentLength, contentType);
    }

    protected void parseMessage(String contentType, InputStream content,
            SoapMessageDecoder.Callback decoderCallback) throws Exception {
        log.debug("parseMessage({})", contentType);

        SoapMessageDecoder soapMessageDecoder =
                new SoapMessageDecoder(contentType, decoderCallback,
                        new SoapParserImpl());
        try {
            soapMessageDecoder.parse(content);
        } catch (Exception ex) {
            throw translateException(ex);
        }
    }

    protected IdentifierMappingProvider getIdentifierMapping() {
        return IdentifierMapping.getInstance();
    }

    protected SoapMessageConverter getMessageConverter() {
        return new SoapMessageConverter(getIdentifierMapping());
    }

    protected void verifyRequest() {
        if (inboundRequestVersion == null) {
            throw new CodedException(X_MISSING_SOAP,
                    "Request does not contain SOAP message");
        }
    }

    protected void waitForResponse() throws Exception {
        try {
            sender.waitForResponse(getSendTimeoutSeconds());
        } catch (CodedException e) {
            throw findActualCause(e);
        }
    }

    protected void verifyRequestResponseMessageVersions() {
        if (outboundRequestVersion != inboundResponseVersion) {
            throw new CodedException(X_INTERNAL_ERROR,
                    "Unexpected response message");
        }
    }

    protected AsyncHttpSender createSender() {
        return new AsyncHttpSender(getHttpClient());
    }

    protected String getRequestParameter(String parameter) {
        return null;
    }

    protected int getSendTimeoutSeconds() {
        return SEND_TIMEOUT_SECONDS;
    }

    protected abstract CloseableHttpAsyncClient getHttpClient();

    protected abstract SoapMessage getOutboundRequestMessage(
            SoapMessage inboundRequestMessage) throws Exception;

    protected abstract SoapMessage getOutboundResponseMessage(
            SoapMessage inboundResponseMessage) throws Exception;

    protected abstract URI getTargetAddress(SoapMessage outboundRequestMessage)
            throws Exception;

    private static boolean isMultipart(String contentType) {
        return MULTIPART_RELATED.equals(getBaseContentType(contentType));
    }

    private CodedException findActualCause(CodedException e) {
        Throwable current = e;
        while (current.getCause() != null) {
            if (current.getCause() instanceof CodedException) {
                return (CodedException) current.getCause();
            }

            current = current.getCause();
        }

        return e;
    }

    private static Class<?> getHeaderClass(SoapMessage message) {
        if (message instanceof AbstractSoapMessage) {
            return ((AbstractSoapMessage<?>) message).getHeader().getClass();
        }

        return null;
    }

    private abstract class MessageDecoderCallback
            implements SoapMessageDecoder.Callback {

        protected MessageEncoder encoder;

        @Override
        public void onCompleted() {
            log.trace("onCompleted()");
            try {
                if (encoder != null) {
                    encoder.close();
                }
            } catch (Exception e) {
                throw translateException(e);
            }
        }

        @Override
        public void onError(Exception e) throws Exception {
            log.error("onError()", e);
            if (encoder != null) {
                encoder.close();
            }

            throw translateException(e);
        }

        @Override
        public void attachment(String contentType, InputStream content,
                Map<String, String> additionalHeaders) throws Exception {
            log.trace("attachment({})", contentType);

            if (encoder != null && encoder instanceof MultipartMessageEncoder) {
                ((MultipartMessageEncoder) encoder).attachment(contentType,
                        content, additionalHeaders);
            } else {
                throw new CodedException(X_INTERNAL_ERROR,
                        "Expected SOAP message, but received multipart");
            }
        }

        @Override
        public void fault(SoapFault fault) throws Exception {
            onError(fault.toCodedException());
        }
    }

    /**
     * @param str the string
     * @return the given string with the leading character removed if it is '/'
     */
    public static String stripSlash(String str) {
        return str != null && str.startsWith("/")
                ? str.substring(1) : str; // Strip '/'
    }
}
