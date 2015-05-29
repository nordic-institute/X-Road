package ee.cyber.xroad.mediator.common;

import java.io.InputStream;
import java.io.OutputStream;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.util.AsyncHttpSender;
import ee.ria.xroad.common.util.HandlerBase;
import ee.ria.xroad.common.util.HttpHeaders;

import static ee.ria.xroad.common.ErrorCodes.X_INVALID_HTTP_METHOD;

/**
 * Base class for mediator HTTP request handlers.
 */
public abstract class AbstractMediatorHandler extends HandlerBase {

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

    protected AbstractMediatorHandler(HttpClientManager httpClientManager) {
        this.httpClientManager = httpClientManager;
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
}
