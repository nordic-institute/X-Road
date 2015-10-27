package ee.ria.xroad.proxy.serverproxy;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Date;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.http.client.HttpClient;
import org.eclipse.jetty.server.Request;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.monitoring.MessageInfo;
import ee.ria.xroad.common.monitoring.MonitorAgent;
import ee.ria.xroad.common.util.HandlerBase;
import ee.ria.xroad.common.util.MimeUtils;
import ee.ria.xroad.common.util.PerformanceLogger;
import ee.ria.xroad.proxy.ProxyMain;

import static ee.ria.xroad.common.ErrorCodes.*;

@Slf4j
class ServerProxyHandler extends HandlerBase {

    private static final String UNKNOWN_VERSION = "unknown";

    private final HttpClient client;

    ServerProxyHandler(HttpClient client) {
        this.client = client;
    }

    @Override
    public void handle(String target, Request baseRequest,
            final HttpServletRequest request,
            final HttpServletResponse response)
                    throws IOException, ServletException {
        long start = PerformanceLogger.log(log,
                "Received request from " + request.getRemoteAddr());
        try {
            if (!request.getMethod().equalsIgnoreCase("POST")) {
                throw new CodedException(X_INVALID_HTTP_METHOD,
                        "Must use POST request method instead of %s",
                        request.getMethod());
            }

            GlobalConf.verifyValidity();

            logProxyVersion(request);

            ServerMessageProcessor processor =
                    createRequestProcessor(request, response, start);
            processor.process();
        } catch (Throwable ex) {
            log.error("Request processing error", ex);

            failure(response,
                    translateWithPrefix(SERVER_SERVERPROXY_X, ex));
        } finally {
            baseRequest.setHandled(true);

            PerformanceLogger.log(log, start, "Request handled");
        }
    }

    private ServerMessageProcessor createRequestProcessor(
            HttpServletRequest request, HttpServletResponse response,
            final long start) throws Exception {
        return new ServerMessageProcessor(request, response, client,
                        getClientSslCertChain(request)) {
            @Override
            protected void postprocess() throws Exception {
                super.postprocess();
                MessageInfo messageInfo = createRequestMessageInfo();
                MonitorAgent.success(messageInfo, new Date(start), new Date());
            }
        };
    }

    @Override
    protected void failure(HttpServletResponse response, CodedException ex)
            throws IOException {
        MonitorAgent.failure(null, ex.getFaultCode(), ex.getFaultString());

        sendErrorResponse(response, ex);
    }

    private static void logProxyVersion(HttpServletRequest request) {
        String thatVersion =
                getVersion(request.getHeader(MimeUtils.HEADER_PROXY_VERSION));
        String thisVersion = getVersion(ProxyMain.getVersion());

        log.info("Received request from {} (security server version: {})",
                request.getRemoteAddr(), thatVersion);

        if (!thatVersion.equals(thisVersion)) {
            log.warn("Peer security server version ({}) does not match host "
                    + "security server version ({})", thatVersion, thisVersion);
        }
    }

    private static String getVersion(String value) {
        return !StringUtils.isBlank(value) ? value : UNKNOWN_VERSION;
    }

    private static X509Certificate[] getClientSslCertChain(
            HttpServletRequest request) throws Exception {
        Object attribute = request.getAttribute(
                "javax.servlet.request.X509Certificate");
        if (attribute != null) {
            return (X509Certificate[]) attribute;
        } else {
            return null;
        }
    }
}
