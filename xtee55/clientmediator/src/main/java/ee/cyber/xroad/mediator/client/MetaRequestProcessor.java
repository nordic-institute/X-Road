package ee.cyber.xroad.mediator.client;

import java.net.URI;

import javax.servlet.http.HttpServletRequest;

import lombok.Value;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.util.AsyncHttpSender;
import ee.cyber.sdsb.common.util.MimeTypes;
import ee.cyber.xroad.mediator.MediatorSystemProperties;
import ee.cyber.xroad.mediator.common.AbstractMediatorHandler;
import ee.cyber.xroad.mediator.common.HttpClientManager;
import ee.cyber.xroad.mediator.common.MediatorMessageProcessor;
import ee.cyber.xroad.mediator.common.MediatorRequest;
import ee.cyber.xroad.mediator.common.MediatorResponse;

import static ee.cyber.sdsb.common.ErrorCodes.X_INTERNAL_ERROR;

class MetaRequestProcessor implements MediatorMessageProcessor {

    @Value
    static class MetaRequest {
        private final String param;
        private final String value;
    }

    private static final Logger LOG =
            LoggerFactory.getLogger(MetaRequestProcessor.class);

    private static final int SEND_TIMEOUT_SECONDS = 120;

    private static final String PARAM_PRODUCER = "producer";
    private static final String PARAM_URI = "uri";

    private final HttpClientManager httpClientManager;
    private final MetaRequest metaRequest;

    MetaRequestProcessor(MetaRequest request,
            HttpClientManager httpClientManager) {
        this.metaRequest = request;
        this.httpClientManager = httpClientManager;
    }

    @Override
    public void process(MediatorRequest request, MediatorResponse response)
            throws Exception {
        LOG.info("Processing meta request ({} = {})", metaRequest.getParam(),
                metaRequest.getValue());

        try (AsyncHttpSender sender =
                new AsyncHttpSender(httpClientManager.getDefaultHttpClient())) {
            sender.doGet(new URI(getUriProxyAddress(metaRequest)));
            sender.waitForResponse(SEND_TIMEOUT_SECONDS);

            response.setContentType(getResponseContentType(sender),
                    sender.getResponseHeaders());
            IOUtils.copy(sender.getResponseContent(),
                    response.getOutputStream());
        }
    }

    static String getUriProxyAddress(MetaRequest request) {
        String uriProxyAddress =
                MediatorSystemProperties.getXroadUriProxyAddress();
        uriProxyAddress += "?" + request.getParam() + "=" + request.getValue();

        return uriProxyAddress;
    }

    static MetaRequest getMetaRequest(HttpServletRequest request) {
        if (!AbstractMediatorHandler.isGetRequest(request)) {
            return null;
        }

        String uri = request.getParameter(PARAM_URI);
        String producer = request.getParameter(PARAM_PRODUCER);
        if (uri != null) {
            return new MetaRequest(PARAM_URI, uri);
        } else if (producer != null)  {
            return new MetaRequest(PARAM_PRODUCER, producer);
        } else {
            throw new CodedException(X_INTERNAL_ERROR, "Unknown meta request");
        }
    }

    private static String getResponseContentType(AsyncHttpSender sender) {
        if (sender.getResponseContentType() != null) {
            return sender.getResponseContentType();
        }

        return MimeTypes.TEXT_XML;
    }
}
