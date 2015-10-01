package ee.cyber.xroad.mediator.client;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.cert.X509Certificate;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import lombok.extern.slf4j.Slf4j;
import org.eclipse.jetty.server.Request;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.conf.serverconf.IsAuthenticationData;
import ee.ria.xroad.common.util.AsyncHttpSender;
import ee.ria.xroad.common.util.HandlerBase;
import ee.ria.xroad.common.util.HttpHeaders;
import ee.ria.xroad.common.util.PerformanceLogger;

import static ee.ria.xroad.common.ErrorCodes.*;

@Slf4j
class ClientMediatorHandler extends HandlerBase {

    // We need to exclude headers that we do not want to carry over
    private static final Set<String> HEADER_BLACKLIST =
            new TreeSet<>(new Comparator<String>() {
                @Override
                public int compare(String a, String b) {
                    return a.toLowerCase().compareTo(b.toLowerCase());
                }
            });

    static {
        HEADER_BLACKLIST.add(HttpHeaders.CONTENT_LENGTH);
        HEADER_BLACKLIST.add(HttpHeaders.CONTENT_TYPE);

        HEADER_BLACKLIST.add(HttpHeaders.CONTENT_ENCODING);
        HEADER_BLACKLIST.add(HttpHeaders.TRANSFER_ENCODING);
        HEADER_BLACKLIST.add(HttpHeaders.CONTENT_TRANSFER_ENCODING);
    }

    protected final HttpClientManager httpClientManager;

    protected ClientMediatorHandler(HttpClientManager httpClientManager) {
        this.httpClientManager = httpClientManager;
    }

    @Override
    public void handle(String target, Request baseRequest,
            final HttpServletRequest request,
            final HttpServletResponse response)
                    throws IOException, ServletException {
        long start = PerformanceLogger.log(log, "Received request from "
                + request.getRemoteAddr());
        log.info("Received request from {}", request.getRemoteAddr());

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
                        httpClientManager, getIsAuthenticationData(request)) {
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
            log.info("Handler got fault", fault);

            sendErrorResponse(response, fault);
        } catch (Exception ex) {
            log.error("Request processing error", ex);

            sendErrorResponse(response,
                    translateWithPrefix(SERVER_CLIENTPROXY_X, ex));
        } finally {
            baseRequest.setHandled(true);

            PerformanceLogger.log(log, start, "Request handled");
        }
    }

    protected void addClientHeaders(HttpServletRequest request,
            AsyncHttpSender sender) {
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String headerName = headerNames.nextElement();
            if (!HEADER_BLACKLIST.contains(headerName)) {
                sender.addHeader(headerName, request.getHeader(headerName));
            }
        }
    }

    protected void process(final MediatorMessageProcessor processor,
            final HttpServletRequest request,
            final HttpServletResponse response) throws Exception {
        processor.process(new MediatorRequest() {
            @Override
            public String getContentType() {
                return request.getContentType();
            }
            @Override
            public InputStream getInputStream() throws Exception {
                return request.getInputStream();
            }
            @Override
            public String getParameters() {
                return request.getQueryString();
            }
        }, new MediatorResponse() {
            @Override
            public void setContentType(String contentType,
                    Map<String, String> additionalHeaders) {
                response.setContentType(contentType);
                setResponseHeaders(response, additionalHeaders);
            }
            @Override
            public OutputStream getOutputStream() throws Exception {
                return response.getOutputStream();
            }
        });
    }

    protected static void setResponseHeaders(HttpServletResponse response,
            Map<String, String> headers) {
        for (Entry<String, String> e : headers.entrySet()) {
            if (!HEADER_BLACKLIST.contains(e.getKey())) {
                response.addHeader(e.getKey(), e.getValue());
            }
        }
    }

    protected static void verifyRequestMethod(HttpServletRequest request) {
        if (!isPostRequest(request)) {
            throw new CodedException(X_INVALID_HTTP_METHOD,
                    "Must use POST request method instead of %s",
                    request.getMethod());
        }
    }

    /**
     * @param request the HTTP servlet request
     * @return true if the given HTTP request is a GET request
     */
    public static boolean isGetRequest(HttpServletRequest request) {
        return request.getMethod().equalsIgnoreCase("GET");
    }

    /**
     * @param request the HTTP servlet request
     * @return true if the given HTTP request is a POST request
     */
    public static boolean isPostRequest(HttpServletRequest request) {
        return request.getMethod().equalsIgnoreCase("POST");
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
}
