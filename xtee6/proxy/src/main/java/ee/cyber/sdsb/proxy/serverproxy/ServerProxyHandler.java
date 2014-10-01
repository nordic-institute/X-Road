package ee.cyber.sdsb.proxy.serverproxy;

import java.io.IOException;
import java.security.cert.X509Certificate;
import java.util.Date;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import lombok.extern.slf4j.Slf4j;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.client.HttpClient;
import org.eclipse.jetty.server.Request;

import ee.cyber.sdsb.common.CodedException;
import ee.cyber.sdsb.common.cert.CertChain;
import ee.cyber.sdsb.common.conf.GlobalConf;
import ee.cyber.sdsb.common.monitoring.MessageInfo;
import ee.cyber.sdsb.common.monitoring.MonitorAgent;
import ee.cyber.sdsb.common.util.HandlerBase;
import ee.cyber.sdsb.common.util.MimeUtils;
import ee.cyber.sdsb.common.util.PerformanceLogger;
import ee.cyber.sdsb.proxy.ProxyMain;

import static ee.cyber.sdsb.common.ErrorCodes.*;

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

            logProxyVersion(request);

            ServerMessageProcessor processor =
                    createRequestProcessor(request, response, start);
            processor.process();
        } catch (Exception ex) {
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

    private static CertChain getClientSslCertChain(HttpServletRequest request)
            throws Exception {
        Object attribute = request.getAttribute(
                "javax.servlet.request.X509Certificate");
        if (attribute != null) {
            X509Certificate[] certs = (X509Certificate[]) attribute;
            X509Certificate trustAnchor =
                    GlobalConf.getCaCert(certs[certs.length - 1]);
            if (trustAnchor == null) {
                throw new Exception("Unable to find trust anchor");
            }

            return CertChain.create(
                    (X509Certificate[]) ArrayUtils.add(certs, trustAnchor));
        } else {
            return null;
        }
    }
}
