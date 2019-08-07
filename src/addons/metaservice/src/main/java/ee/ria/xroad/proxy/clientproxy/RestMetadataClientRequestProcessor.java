/**
 * The MIT License
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.proxy.clientproxy;

import ee.ria.xroad.common.CodedExceptionWithHttpStatus;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.conf.serverconf.model.DescriptionType;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.RestRequest;
import ee.ria.xroad.common.monitoring.MessageInfo;
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.MimeUtils;
import ee.ria.xroad.proxy.util.MessageProcessorBase;

import lombok.extern.slf4j.Slf4j;
import org.apache.http.Header;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicHeader;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;

import static ee.ria.xroad.common.ErrorCodes.X_BAD_REQUEST;
import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;

/**
 * Rest metadata client request processor
 */
@Slf4j
public class RestMetadataClientRequestProcessor extends MessageProcessorBase {

    private static final String GET_OPENAPI = "getOpenAPI";
    private static final String QUERY_PARAM_SERVICECODE = "serviceCode";

    private static final int BUFFER_SIZE_BYTES = 65536;
    private static final String DEFAULT_GETOPENAPI_CONTENT_TYPE = "text/plain";

    private RestRequest restRequest;

    RestMetadataClientRequestProcessor(HttpServletRequest request, HttpServletResponse response) {
        super(request, response, null);
    }

    /**
     * @return true if the request can be processed
     */
    public boolean canProcess() throws Exception {
        log.trace("canProcess() method={} requestURI={} queryString={}", servletRequest.getMethod(),
                servletRequest.getRequestURI(), servletRequest.getQueryString());

        restRequest = new RestRequest(
                servletRequest.getMethod(),
                servletRequest.getRequestURI(),
                servletRequest.getQueryString(),
                getHeaders(servletRequest),
                UUID.randomUUID().toString());
        if (restRequest.getQueryId() == null) {
            restRequest.setQueryId(GlobalConf.getInstanceIdentifier() + "-" + UUID.randomUUID().toString());
        }

        log.trace("serviceCode={}", restRequest.getServiceId().getServiceCode());

        if (restRequest.getVerb() == RestRequest.Verb.GET
                && restRequest.getServiceId().getServiceCode().equalsIgnoreCase(GET_OPENAPI)) {
            return true;
        }
        return false;
    }

    @Override
    public void process() throws IOException, URISyntaxException {
        log.trace("process()");

        // parse query string
        String fullURL = getFullURL(servletRequest);
        log.trace("fullURL={}", fullURL);
        List<NameValuePair> pairs = URLEncodedUtils.parse(new URI(fullURL), Charset.forName("UTF-8"));
        String targetServiceCode = null;
        for (NameValuePair pair : pairs) {
            log.trace("{} : {}", pair.getName(), pair.getValue());
            if (pair.getName().equalsIgnoreCase(QUERY_PARAM_SERVICECODE)) {
                targetServiceCode = pair.getValue();
            }
        }

        if (targetServiceCode == null || targetServiceCode.isEmpty()) {
            servletResponse.setHeader(MimeUtils.HEADER_ERROR, "Server.ClientProxy.BadRequest");
            throw new CodedExceptionWithHttpStatus(HttpServletResponse.SC_BAD_REQUEST,
                    X_BAD_REQUEST,
                    String.format("Missing request parameters"));
        }

        ServiceId targetServiceId = ServiceId.create(restRequest.getServiceId().getClientId(), targetServiceCode);
        log.trace("targetServiceId={}", targetServiceId);

        DescriptionType descriptionType = ServerConf.getDescriptionType(targetServiceId);
        if (descriptionType == null) {
            servletResponse.setHeader(MimeUtils.HEADER_ERROR, "Server.ClientProxy.InternalError");
            throw new CodedExceptionWithHttpStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    X_INTERNAL_ERROR,
                    String.format("Service not found: %s", targetServiceId.toString()));
        }
        if (descriptionType != DescriptionType.REST) {
            servletResponse.setHeader(MimeUtils.HEADER_ERROR, "Server.ClientProxy.InternalError");
            throw new CodedExceptionWithHttpStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    X_INTERNAL_ERROR,
                    String.format("Invalid service type: %s", descriptionType.toString()));
        }

        String serviceDescriptionURL = ServerConf.getServiceDescriptionURL(targetServiceId);
        URL url = new URL(serviceDescriptionURL);

        // try to resolve content type
        try {
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            // Use the same timeouts as client proxy to server proxy connections.
            connection.setConnectTimeout(SystemProperties.getClientProxyTimeout());
            connection.setReadTimeout(SystemProperties.getClientProxyHttpClientTimeout());
            connection.setRequestMethod("HEAD");
            connection.connect();
            servletResponse.setContentType(connection.getContentType());
            log.trace("contentType={}", connection.getContentType());
        } catch (Exception e) {
            servletResponse.setContentType(DEFAULT_GETOPENAPI_CONTENT_TYPE);
            log.info("Using default content type {}", DEFAULT_GETOPENAPI_CONTENT_TYPE);
        }

        byte[] buffer = new byte[BUFFER_SIZE_BYTES];
        OutputStream outputStream = servletResponse.getOutputStream();
        log.trace("outputStream={}", outputStream);
        URLConnection urlConnection = url.openConnection();
        urlConnection.setConnectTimeout(SystemProperties.getClientProxyTimeout());
        urlConnection.setReadTimeout(SystemProperties.getClientProxyHttpClientTimeout());
        try (InputStream inputStream = urlConnection.getInputStream()) {
            int length;
            do {
                length = inputStream.read(buffer);
                log.trace("read length={}", length);
                if (length > 0) {
                    outputStream.write(buffer, 0, length);
                    log.trace("wrote length={}", length);
                }
            } while (length > 0);
        } catch (IOException e) {
            servletResponse.setHeader(MimeUtils.HEADER_ERROR, "Server.ClientProxy.InternalError");
            throw new CodedExceptionWithHttpStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    X_INTERNAL_ERROR,
                    String.format("Failed reading service description from: %s", serviceDescriptionURL));
        }

        // set X-Road specific headers to the response
        servletResponse.setHeader(MimeUtils.HEADER_QUERY_ID, restRequest.getQueryId());
        servletResponse.setHeader(MimeUtils.HEADER_REQUEST_ID, restRequest.getXRequestId());
        servletResponse.setHeader(MimeUtils.HEADER_SERVICE_ID, restRequest.getServiceId().toString());
        servletResponse.setHeader(MimeUtils.HEADER_CLIENT_ID, restRequest.getClientId().toString());
        servletResponse.setHeader(MimeUtils.HEADER_REQUEST_HASH, CryptoUtils.encodeBase64(restRequest.getHash()));

        servletResponse.setStatus(HttpStatus.SC_OK);
    }

    @Override
    public MessageInfo createRequestMessageInfo() {
        log.trace("createRequestMessageInfo()");
        return new MessageInfo(MessageInfo.Origin.CLIENT_PROXY, restRequest.getClientId(), restRequest.getServiceId(),
                null, restRequest.getQueryId());
    }

    private List<Header> getHeaders(HttpServletRequest request) {
        List<Header> headers = new ArrayList<>();
        Enumeration<String> names = request.getHeaderNames();
        while (names.hasMoreElements()) {
            String name = names.nextElement();
            String value = request.getHeader(name);
            headers.add(new BasicHeader(name, value));
        }
        return headers;
    }

    private static String getFullURL(HttpServletRequest request) {
        StringBuilder builder = new StringBuilder(request.getRequestURL().toString());
        String queryString = request.getQueryString();
        if (queryString == null || queryString.isEmpty()) {
            return builder.toString();
        } else {
            return builder.append('?').append(queryString).toString();
        }
    }
}
