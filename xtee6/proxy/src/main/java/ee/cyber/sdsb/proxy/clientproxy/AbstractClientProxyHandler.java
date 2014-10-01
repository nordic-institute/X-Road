package ee.cyber.sdsb.proxy.clientproxy;

import java.io.IOException;
import java.util.Date;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.apache.http.client.HttpClient;
import org.eclipse.jetty.server.Request;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.conf.serverconf.ClientCert;
import ee.cyber.sdsb.common.monitoring.MessageInfo;
import ee.cyber.sdsb.common.monitoring.MonitorAgent;
import ee.cyber.sdsb.common.util.HandlerBase;
import ee.cyber.sdsb.common.util.HttpHeaders;
import ee.cyber.sdsb.common.util.PerformanceLogger;
import ee.cyber.sdsb.proxy.util.MessageProcessorBase;

import static ee.cyber.sdsb.common.ErrorCodes.SERVER_CLIENTPROXY_X;
import static ee.cyber.sdsb.common.ErrorCodes.translateWithPrefix;

/**
 * Base class for client proxy handlers.
 */
@Slf4j
@RequiredArgsConstructor
abstract class AbstractClientProxyHandler extends HandlerBase {

    protected final HttpClient client;

    abstract MessageProcessorBase createRequestProcessor(String target,
            HttpServletRequest request, HttpServletResponse response)
                    throws Exception;

    @Override
    public void handle(String target, Request baseRequest,
            HttpServletRequest request, HttpServletResponse response)
                    throws IOException, ServletException {
        if (baseRequest.isHandled()) {
            // If some handler already processed the request, we do nothing.
            return;
        }

        boolean handled = false;

        long start = PerformanceLogger.log(log, "Received request from " +
                request.getRemoteAddr());
        log.info("Received request from {}", request.getRemoteAddr());

        MessageProcessorBase processor = null;
        try {
            processor = createRequestProcessor(target, request, response);
            if (processor != null) {
                handled = true;
                start = logPerformanceBegin(request);
                processor.process();
                success(processor, start);
            }
        } catch (CodedException.Fault | ClientException ex) {
            handled = true;

            log.error("Request processing error", ex);

            // Exceptions caused by incoming message and exceptions
            // derived from faults sent by serverproxy already contain
            // full error code. Thus, we must not attach additional
            // error code prefixes to them.

            failure(processor, response, ex);
        } catch (Exception ex) {
            handled = true;

            log.error("Request processing error", ex);

            // All the other exceptions get prefix Server.ClientProxy...
            failure(processor, response,
                    translateWithPrefix(SERVER_CLIENTPROXY_X, ex));
        } finally {
            baseRequest.setHandled(handled);

            if (handled) {
                logPerformanceEnd(start);
            }
        }
    }

    protected void success(MessageProcessorBase processor, long start) {
        MessageInfo messageInfo = processor.createRequestMessageInfo();
        MonitorAgent.success(messageInfo, new Date(start), new Date());
    }

    protected void failure(MessageProcessorBase processor,
            HttpServletResponse response, CodedException ex)
            throws IOException {
        MessageInfo info = processor != null
                ? processor.createRequestMessageInfo() : null;

        MonitorAgent.failure(info, ex.getFaultCode(), ex.getFaultString());

        sendErrorResponse(response, ex);
    }

    @Override
    protected void failure(HttpServletResponse response, CodedException ex)
            throws IOException {
        MessageInfo info = null;

        MonitorAgent.failure(info, ex.getFaultCode(), ex.getFaultString());

        sendErrorResponse(response, ex);
    }

    protected static boolean isGetRequest(HttpServletRequest request) {
        return request.getMethod().equalsIgnoreCase("GET");
    }

    protected static boolean isPostRequest(HttpServletRequest request) {
        return request.getMethod().equalsIgnoreCase("POST");
    }

    protected static final String stripSlash(String str) {
        return str != null && str.startsWith("/")
                ? str.substring(1) : str; // Strip '/'
    }

    protected static ClientCert getClientCert(HttpServletRequest request) {
        return ClientCert.fromParameters(
                request.getHeader(HttpHeaders.X_SSL_CLIENT_VERIFY),
                request.getHeader(HttpHeaders.X_SSL_CLIENT_CERT));
    }

    private static long logPerformanceBegin(HttpServletRequest request) {
        long start = PerformanceLogger.log(log, "Received request from " +
                request.getRemoteAddr());
        log.info("Received request from {}", request.getRemoteAddr());

        return start;
    }

    private static void logPerformanceEnd(long start) {
        PerformanceLogger.log(log, start, "Request handled");
    }
}
