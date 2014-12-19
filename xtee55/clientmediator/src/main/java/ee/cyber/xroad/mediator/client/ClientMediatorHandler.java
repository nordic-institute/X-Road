package ee.cyber.xroad.mediator.client;

import java.io.IOException;
import java.security.cert.X509Certificate;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.conf.serverconf.ClientCert;
import ee.cyber.sdsb.common.util.AsyncHttpSender;
import ee.cyber.sdsb.common.util.PerformanceLogger;
import ee.cyber.xroad.mediator.common.AbstractMediatorHandler;
import ee.cyber.xroad.mediator.common.HttpClientManager;
import ee.cyber.xroad.mediator.common.MediatorMessageProcessor;

import static ee.cyber.sdsb.common.ErrorCodes.SERVER_CLIENTPROXY_X;
import static ee.cyber.sdsb.common.ErrorCodes.translateWithPrefix;

class ClientMediatorHandler extends AbstractMediatorHandler {

    private static final Logger LOG =
            LoggerFactory.getLogger(ClientMediatorHandler.class);

    ClientMediatorHandler(HttpClientManager httpClientManager) {
        super(httpClientManager);
    }

    @Override
    public void handle(String target, Request baseRequest,
            final HttpServletRequest request,
            final HttpServletResponse response)
                    throws IOException, ServletException {
        long start = PerformanceLogger.log(LOG, "Received request from "
                    + request.getRemoteAddr());
        LOG.info("Received request from {}", request.getRemoteAddr());

        try {
            MediatorMessageProcessor processor;

            MetaRequestProcessor.MetaRequest metaRequest =
                    MetaRequestProcessor.getMetaRequest(target, request);
            if (metaRequest != null) {
                processor = new MetaRequestProcessor(metaRequest,
                        httpClientManager);
            } else {
                verifyRequestMethod(request);

                processor = new ClientMediatorMessageProcessor(target,
                        httpClientManager, getClientCert(request)) {
                    @Override
                    protected AsyncHttpSender createSender() {
                        AsyncHttpSender sender = super.createSender();
                        addClientHeaders(request, sender);
                        return sender;
                    }
                };
            }

            process(processor, request, response);
        } catch (CodedException.Fault fault) {
            LOG.info("Handler got fault", fault);

            sendErrorResponse(response, fault);
        } catch (Exception ex) {
            LOG.error("Request processing error", ex);

            sendErrorResponse(response,
                    translateWithPrefix(SERVER_CLIENTPROXY_X, ex));
        } finally {
            baseRequest.setHandled(true);

            PerformanceLogger.log(LOG, start, "Request handled");
        }
    }

    private static ClientCert getClientCert(HttpServletRequest request) {
        Object attribute = request.getAttribute(
                "javax.servlet.request.X509Certificate");
        if (attribute != null) {
            X509Certificate[] certs = (X509Certificate[]) attribute;
            return new ClientCert(certs[0], "cert");
        } else {
            return new ClientCert(null, null);
        }
    }
}
