package ee.cyber.xroad.mediator.common;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.Map;

import org.apache.http.impl.nio.client.CloseableHttpAsyncClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.message.AbstractSoapMessage;
import ee.cyber.sdsb.common.message.SoapMessage;
import ee.cyber.sdsb.common.message.SoapMessageDecoder;
import ee.cyber.sdsb.common.util.AsyncHttpSender;
import ee.cyber.xroad.mediator.IdentifierMapping;
import ee.cyber.xroad.mediator.IdentifierMappingProvider;
import ee.cyber.xroad.mediator.message.MessageEncoder;
import ee.cyber.xroad.mediator.message.MessageVersion;
import ee.cyber.xroad.mediator.message.MultipartMessageEncoder;
import ee.cyber.xroad.mediator.message.SoapMessageConverter;
import ee.cyber.xroad.mediator.message.SoapMessageEncoder;
import ee.cyber.xroad.mediator.message.SoapParserImpl;
import ee.cyber.xroad.mediator.util.IOPipe;

import static ee.cyber.sdsb.common.ErrorCodes.*;
import static ee.cyber.sdsb.common.util.MimeTypes.MULTIPART_RELATED;
import static ee.cyber.sdsb.common.util.MimeUtils.getBaseContentType;

public abstract class AbstractMediatorMessageProcessor
        implements MediatorMessageProcessor {

    private static final Logger LOG =
            LoggerFactory.getLogger(AbstractMediatorMessageProcessor.class);

    private static final int SEND_TIMEOUT_SECONDS = 120; // TODO: Make configurable

    protected final IOPipe requestPipe = new IOPipe();

    protected final String target;
    protected final HttpClientManager httpClientManager;

    protected AsyncHttpSender sender;

    protected MessageVersion inboundRequestVersion;
    protected MessageVersion inboundResponseVersion;
    protected MessageVersion outboundRequestVersion;

    protected Class<?> inboundRequestHeaderClass;

    protected AbstractMediatorMessageProcessor(String target,
            HttpClientManager httpClientManager) throws Exception {
        this.target = getTarget(target);
        this.httpClientManager = httpClientManager;
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
            if (sender != null) {
                sender.close();
            }
        }
    }

    void processRequest(final MediatorRequest request) throws Exception {
        LOG.trace("processRequest({})", request.getContentType());

        SoapMessageDecoder.Callback cb = new MessageDecoderCallback() {
            @Override
            public void soap(SoapMessage message) throws Exception {
                LOG.trace("soap({})", message.getXml());

                inboundRequestVersion = MessageVersion.fromMessage(message);
                inboundRequestHeaderClass = getHeaderClass(message);

                SoapMessage outboundRequestMessage =
                        getOutboundRequestMessage(message);

                outboundRequestVersion =
                        MessageVersion.fromMessage(outboundRequestMessage);

                encoder = isMultipart(request.getContentType())
                        ? new MultipartMessageEncoder(requestPipe.out)
                        : new SoapMessageEncoder(requestPipe.out);

                startSending(getTargetAddress(outboundRequestMessage),
                        encoder.getContentType());

                encoder.soap(outboundRequestMessage);
            }
        };

        parseMessage(request.getContentType(), request.getInputStream(), cb);
    }

    void processResponse(final MediatorResponse response) throws Exception {
        LOG.trace("processResponse()");

        final OutputStream out = response.getOutputStream();
        SoapMessageDecoder.Callback cb = new MessageDecoderCallback() {
            @Override
            public void soap(SoapMessage message) throws Exception {
                LOG.trace("soap({})", message.getXml());

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

    protected void startSending(URI address, String contentType)
            throws Exception {
        LOG.debug("startSending({}, {})", address, contentType);

        sender = createSender();

        sender.doPost(address, requestPipe.in, contentType);
    }

    protected void parseMessage(String contentType, InputStream content,
            SoapMessageDecoder.Callback decoderCallback) throws Exception {
        LOG.debug("parseMessage({})", contentType);

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
            LOG.trace("onCompleted()");
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
            LOG.error("onError()", e);
            if (encoder != null) {
                encoder.close();
            }

            throw translateException(e);
        }

        @Override
        public void attachment(String contentType, InputStream content,
                Map<String, String> additionalHeaders) throws Exception {
            LOG.trace("attachment({})", contentType);

            if (encoder != null && encoder instanceof MultipartMessageEncoder) {
                ((MultipartMessageEncoder) encoder).attachment(contentType,
                        content, additionalHeaders);
            } else {
                throw new CodedException(X_INTERNAL_ERROR,
                        "Expected SOAP message, but received multipart");
            }
        }
    }

    public static final String getTarget(String target) {
        return target != null && target.startsWith("/")
                ? target.substring(1) : target; // Strip '/'
    }
}
