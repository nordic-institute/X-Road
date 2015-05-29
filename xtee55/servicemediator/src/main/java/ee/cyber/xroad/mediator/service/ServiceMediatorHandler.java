package ee.cyber.xroad.mediator.service;

import java.io.IOException;
import java.net.URI;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.server.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.util.AsyncHttpSender;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.common.util.MimeUtils;
import ee.ria.xroad.common.util.PerformanceLogger;
import ee.cyber.xroad.mediator.common.AbstractMediatorHandler;
import ee.cyber.xroad.mediator.common.HttpClientManager;
import ee.cyber.xroad.mediator.common.MediatorMessageProcessor;
import ee.cyber.xroad.mediator.service.wsdlmerge.WSDLMergeRequestProcessor;

import static ee.ria.xroad.common.ErrorCodes.SERVER_SERVERPROXY_X;
import static ee.ria.xroad.common.ErrorCodes.translateWithPrefix;
import static javax.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
import static javax.servlet.http.HttpServletResponse.SC_OK;

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
        long start = PerformanceLogger.log(LOG, "Received request from "
                    + request.getRemoteAddr());
        LOG.info("Received request from {}", request.getRemoteAddr());

        try {
            MediatorMessageProcessor processor =
                    getRequestProcessor(target, request);

            process(processor, request, response);
        } catch (CodedException.Fault fault) {
            LOG.info("Handler got fault", fault);

            if (isMergedWsdlRequest(target)) {
                sendWsdlMergeErrorResponse(response, fault.getFaultString());
            } else if (isGetRequest(request)) {
                sendErrorResponse(response, SC_INTERNAL_SERVER_ERROR,
                        fault.getFaultString());
            } else {
                sendErrorResponse(response, fault);
            }
        } catch (Exception ex) {
            LOG.error("Request processing error", ex);

            if (isMergedWsdlRequest(target)) {
                sendWsdlMergeErrorResponse(response, ex.getMessage());
            } else if (isGetRequest(request)) {
                sendErrorResponse(response, SC_INTERNAL_SERVER_ERROR,
                        ex.getMessage());
            } else {
                sendErrorResponse(response,
                        translateWithPrefix(SERVER_SERVERPROXY_X, ex));
            }
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

    private void sendWsdlMergeErrorResponse(HttpServletResponse response,
            String errorMsg) throws IOException {
        sendErrorResponse(response, SC_OK, errorMsg);
    }
    /**
     * We send plain text with HTTP OK status, as MISP
     * (and other V5 client applications) could handle it more accurately.
     */
    private void sendErrorResponse(HttpServletResponse response,
            int status, String errorMsg) throws IOException {
        String encoding = MimeUtils.UTF8;
        byte[] messageBytes = errorMsg.getBytes(encoding);

        response.setStatus(status);
        response.setContentType(MimeTypes.TEXT_PLAIN);
        response.setContentLength(messageBytes.length);
        response.setCharacterEncoding(encoding);

        response.getOutputStream().write(messageBytes);
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
