package ee.ria.xroad.proxy.clientproxy;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import lombok.extern.slf4j.Slf4j;

import org.apache.http.client.HttpClient;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.proxy.util.MessageProcessorBase;

import static ee.ria.xroad.common.ErrorCodes.X_INVALID_REQUEST;

@Slf4j
class MetadataHandler extends AbstractClientProxyHandler {

    public MetadataHandler(HttpClient client) {
        super(client);
    }

    @Override
    MessageProcessorBase createRequestProcessor(String target,
            HttpServletRequest request, HttpServletResponse response)
                    throws Exception {
        log.trace("createRequestProcessor({})", target);
        if (!isGetRequest(request)) {
            return null;
        }

        if (target == null) {
            throw new CodedException(X_INVALID_REQUEST,
                    "Target must not be null");
        }

        MetadataClientRequestProcessor processor =
                new MetadataClientRequestProcessor(target, request, response);
        if (processor.canProcess()) {
            log.trace("Processing with MetadataClientRequestProcessor");
            return processor;
        }

        return null;
    }
}
