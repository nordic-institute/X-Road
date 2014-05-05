package ee.cyber.sdsb.proxy.serverproxy;

import java.io.IOException;
import java.util.Date;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.http.client.HttpClient;
import org.eclipse.jetty.server.Request;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.message.SoapMessageImpl;
import ee.cyber.sdsb.common.monitoring.MessageInfo;
import ee.cyber.sdsb.common.monitoring.MonitorAgent;
import ee.cyber.sdsb.common.util.HandlerBase;
import ee.cyber.sdsb.common.util.PerformanceLogger;

import static ee.cyber.sdsb.common.ErrorCodes.*;

class ServerProxyHandler extends HandlerBase {

    private static final Logger LOG =
        LoggerFactory.getLogger(ServerProxyHandler.class);

    private final HttpClient client;

    ServerProxyHandler(HttpClient client) {
        this.client = client;
    }

    @Override
    public void handle(String target, Request baseRequest,
            HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        long start = PerformanceLogger.log(LOG, "Received request from " +
                request.getRemoteAddr());
        LOG.info("Received request from {}", request.getRemoteAddr());

        try {
            if (!request.getMethod().equalsIgnoreCase("POST")) {
                throw new CodedException(X_INVALID_HTTP_METHOD,
                        "Must use POST request method instead of %s",
                        request.getMethod());
            }

            final Date startTime = new Date();
            ServerMessageProcessor processor = new ServerMessageProcessor(
                    request, response, client, getClientCertificate(request)) {
                @Override
                protected void onSuccess(SoapMessageImpl message) {
                    MessageInfo messageInfo = createRequestMessageInfo();
                    MonitorAgent.success(messageInfo, startTime, new Date());
                }
            };
            processor.process();
        } catch (Exception ex) {
            LOG.error("Request processing error", ex);

            failure(response,
                    translateWithPrefix(SERVER_SERVERPROXY_X, ex));
        } finally {
            baseRequest.setHandled(true);

            PerformanceLogger.log(LOG, start, "Request handled");
        }
    }

    @Override
    protected void failure(HttpServletResponse response, CodedException ex)
            throws IOException {
        MonitorAgent.failure(null, ex.getFaultCode(), ex.getFaultString());

        sendErrorResponse(response, ex);
    }
}
