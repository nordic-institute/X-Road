package ee.cyber.xroad.mediator.service;

import java.io.IOException;
import java.net.URI;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.hibernate.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.db.HibernateUtil;
import ee.cyber.sdsb.common.db.TransactionCallback;
import ee.cyber.sdsb.common.util.AsyncHttpSender;
import ee.cyber.sdsb.common.util.PerformanceLogger;
import ee.cyber.xroad.mediator.common.AbstractMediatorHandler;
import ee.cyber.xroad.mediator.common.HttpClientManager;
import ee.cyber.xroad.mediator.common.MediatorMessageProcessor;
import ee.cyber.xroad.mediator.service.wsdlmerge.WSDLMergeRequestProcessor;

import static ee.cyber.sdsb.common.ErrorCodes.SERVER_SERVERPROXY_X;
import static ee.cyber.sdsb.common.ErrorCodes.translateWithPrefix;

class ServiceMediatorHandler extends AbstractMediatorHandler {

    private static final Logger LOG =
            LoggerFactory.getLogger(ServiceMediatorHandler.class);

    private static final String PARAM_BACKEND = "backend";

    ServiceMediatorHandler(HttpClientManager httpClientManager) {
        super(httpClientManager);
    }

    @Override
    public void handle(String target, Request baseRequest,
            final HttpServletRequest request,
            final HttpServletResponse response)
                    throws IOException, ServletException {
        long start = PerformanceLogger.log(LOG, "Received request from " +
                request.getRemoteAddr());
        LOG.info("Received request from {}", request.getRemoteAddr());

        try {
            final MediatorMessageProcessor processor =
                    getRequestProcessor(target, request);

            HibernateUtil.doInTransaction(new TransactionCallback<Object>() {
                @Override
                public Object call(Session session) throws Exception {
                    process(processor, request, response);
                    return null;
                }
            });
        } catch (CodedException.Fault fault) {
            LOG.info("Handler got fault", fault);

            sendErrorResponse(response, fault);
        } catch (Exception ex) {
            LOG.error("Request processing error", ex);

            sendErrorResponse(response,
                    translateWithPrefix(SERVER_SERVERPROXY_X, ex));
        } finally {
            baseRequest.setHandled(true);

            PerformanceLogger.log(LOG, start, "Request handled");
        }
    }

    private MediatorMessageProcessor getRequestProcessor(String target,
            final HttpServletRequest request) throws Exception {
        if (isGetRequest(request)) {
            if (isMergedWsdlRequest(target)) {
                return new WSDLMergeRequestProcessor(request);
            } else {
                // Assume simple WSDL request
                URI backend = getBackendUri(request);
                return backend != null
                        ? new WSDLRequestProcessor(backend, httpClientManager)
                        : new WSDLRequestProcessor(target, httpClientManager);
            }
        } else {
            verifyRequestMethod(request);

            return new ServiceMediatorMessageProcessor(target,
                    httpClientManager) {
                @Override
                protected AsyncHttpSender createSender() {
                    AsyncHttpSender sender = super.createSender();
                    addClientHeaders(request, sender);
                    return sender;
                }

                @Override
                protected String getRequestParameter(String parameter) {
                    return request.getParameter(parameter);
                }
            };
        }
    }

    private boolean isMergedWsdlRequest(String target) {
        return "/wsdl".equals(target);
    }

    private static URI getBackendUri(HttpServletRequest request)
            throws Exception {
        String backend = request.getParameter(PARAM_BACKEND);
        if (backend != null) {
            return new URI(backend);
        }

        return null;
    }
}
