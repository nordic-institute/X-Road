package ee.ria.xroad.proxy.clientproxy;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Date;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.client.HttpClient;
import org.eclipse.jetty.server.Request;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.CodedExceptionWithHttpStatus;
import ee.ria.xroad.common.conf.serverconf.IsAuthenticationData;
import ee.ria.xroad.common.monitoring.MessageInfo;
import ee.ria.xroad.common.monitoring.MonitorAgent;
import ee.ria.xroad.common.util.HandlerBase;
import ee.ria.xroad.common.util.PerformanceLogger;
import ee.ria.xroad.proxy.util.MessageProcessorBase;

import static ee.ria.xroad.common.ErrorCodes.SERVER_CLIENTPROXY_X;
import static ee.ria.xroad.common.ErrorCodes.translateWithPrefix;

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

        long start = PerformanceLogger.log(log, "Received request from "
                + request.getRemoteAddr());
        log.info("Received request from {}", request.getRemoteAddr());

        MessageProcessorBase processor = null;
        try {
            processor = createRequestProcessor(target, request, response);

            if (processor != null) {
                handled = true;
                start = logPerformanceBegin(request);
                processor.process();
                success(processor, start);

                log.info("Request successfully handled ({} ms)",
                        System.currentTimeMillis() - start);
            }
        } catch (CodedException.Fault | ClientException ex) {
            handled = true;

            log.error("Request processing error", ex);

            // Exceptions caused by incoming message and exceptions
            // derived from faults sent by serverproxy already contain
            // full error code. Thus, we must not attach additional
            // error code prefixes to them.

            failure(processor, response, ex);
        } catch (CodedExceptionWithHttpStatus ex) {
            handled = true;

            log.error("Request processing error", ex);

            // Respond with HTTP status code and plain text error message
            // instead of SOAP fault message.

            failure(response, ex);
        } catch (Throwable ex) {
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
        MonitorAgent.success(
            processor.createRequestMessageInfo(),
            new Date(start),
            new Date()
        );
    }

    protected void failure(MessageProcessorBase processor,
            HttpServletResponse response, CodedException ex)
            throws IOException {
        MessageInfo info = processor != null
                ? processor.createRequestMessageInfo()
                : null;

        MonitorAgent.failure(info, ex.getFaultCode(), ex.getFaultString());

        sendErrorResponse(response, ex);
    }

    @Override
    protected void failure(HttpServletResponse response, CodedException ex)
            throws IOException {
        MonitorAgent.failure(null, ex.getFaultCode(), ex.getFaultString());

        sendErrorResponse(response, ex);
    }

    protected void failure(HttpServletResponse response,
            CodedExceptionWithHttpStatus e) throws IOException {
        MonitorAgent.failure(null,
            e.withPrefix(SERVER_CLIENTPROXY_X).getFaultCode(),
            e.getFaultString()
        );

        sendPlainTextErrorResponse(response, e.getStatus(), e.getFaultString());
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

    protected static IsAuthenticationData getIsAuthenticationData(
            HttpServletRequest request) {
        X509Certificate[] certs =
                (X509Certificate[]) request.getAttribute(
                        "javax.servlet.request.X509Certificate");
        return new IsAuthenticationData(
            certs != null && certs.length != 0 ? certs[0] : null,
            !"https".equals(request.getScheme()) // if not HTTPS, it's plaintext
        );
    }

    private static long logPerformanceBegin(HttpServletRequest request) {
        long start = PerformanceLogger.log(log, "Received request from "
                + request.getRemoteAddr());
        log.info("Received request from {}", request.getRemoteAddr());

        return start;
    }

    private static void logPerformanceEnd(long start) {
        PerformanceLogger.log(log, start, "Request handled");
    }
}
