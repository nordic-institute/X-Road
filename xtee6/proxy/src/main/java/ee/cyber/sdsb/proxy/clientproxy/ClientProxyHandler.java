package ee.cyber.sdsb.proxy.clientproxy;

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

/**
 * Receives the message and does low-level HTTP stuff. The main processing
 * is delegated to RequestProcessor class.
 */
class ClientProxyHandler extends HandlerBase {

    private static final Logger LOG =
        LoggerFactory.getLogger(ClientProxyHandler.class);

    private final HttpClient client;

    private ClientMessageProcessor processor;

    public ClientProxyHandler(HttpClient client) {
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
                throw new ClientException(X_INVALID_HTTP_METHOD,
                        "Must use POST request method instead of %s",
                        request.getMethod());
            }

            if (isSslConnection(baseRequest)
                    && getClientCertificate(request) == null) {
                throw new ClientException(X_SSL_AUTH_FAILED,
                        "SSL client authentication failed");
            }

            final Date startTime = new Date();
            processor = new ClientMessageProcessor(request, response, client) {
                @Override
                protected void onSuccess(SoapMessageImpl message) {
                    MessageInfo messageInfo = createRequestMessageInfo();
                    MonitorAgent.success(messageInfo, startTime, new Date());
                }
            };
            processor.process();
        } catch (CodedException.Fault | ClientException ex) {
            // Exceptions caused by incoming message and exceptions
            // derived from faults sent by serverproxy already contain
            // full error code. Thus, we must not attach additional
            // error code prefixes to them.

            failure(response, ex);
        } catch (Exception ex) {
            LOG.error("Request processing error", ex);

            // All the other exceptions get prefix Server.ClientProxy...
            failure(response, translateWithPrefix(SERVER_CLIENTPROXY_X, ex));
        } finally {
            baseRequest.setHandled(true);

            PerformanceLogger.log(LOG, start, "Request handled");
        }
    }

    @Override
    protected void failure(HttpServletResponse response, CodedException ex)
            throws IOException {
        MessageInfo info = null;
        if (processor != null && processor.getRequestSoap() != null) {
            info = processor.createRequestMessageInfo();
        }

        MonitorAgent.failure(info, ex.getFaultCode(), ex.getFaultString());

        sendErrorResponse(response, ex);
    }

    private static boolean isSslConnection(Request baseRequest) {
        return getConnectorName(baseRequest).equals(
                ClientProxy.CLIENT_SSL_CONNECTOR_NAME);
    }

    private static String getConnectorName(Request baseRequest) {
        return baseRequest.getConnection().getConnector().getName();
    }

}
