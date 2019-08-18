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
package ee.ria.xroad.proxy.serverproxy;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.conf.serverconf.model.DescriptionType;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.RestRequest;
import ee.ria.xroad.common.message.RestResponse;
import ee.ria.xroad.common.metadata.MethodListType;
import ee.ria.xroad.common.metadata.ObjectFactory;
import ee.ria.xroad.common.opmonitoring.OpMonitoringData;
import ee.ria.xroad.common.util.CachingStream;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.common.util.MimeUtils;
import ee.ria.xroad.proxy.protocol.ProxyMessage;
import ee.ria.xroad.proxy.protocol.ProxyMessageDecoder;
import ee.ria.xroad.proxy.protocol.ProxyMessageEncoder;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicHeader;

import javax.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_REQUEST;
import static ee.ria.xroad.common.metadata.MetadataRequests.ALLOWED_METHODS;
import static ee.ria.xroad.common.metadata.MetadataRequests.GET_OPENAPI;
import static ee.ria.xroad.common.metadata.MetadataRequests.LIST_METHODS;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_REQUEST_ID;

/**
 * Handler for REST metadata services
 */
@Slf4j
public class RestMetadataServiceHandlerImpl implements RestServiceHandler {

    private static final String QUERY_PARAM_SERVICECODE = "serviceCode";
    private static final String DEFAULT_GETOPENAPI_CONTENT_TYPE = "text/plain";
    private static final int BUFFER_SIZE_BYTES = 65536;

    static final ObjectFactory OBJECT_FACTORY = new ObjectFactory();

    static final ObjectMapper MAPPER;

    static {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.setPropertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
        MAPPER = mapper;
    }

    private RestResponse restResponse;
    private CachingStream restResponseBody;

    @Override
    public boolean shouldVerifyAccess() {
        return false;
    }

    @Override
    public boolean shouldVerifySignature() {
        return true;
    }

    @Override
    public boolean shouldLogSignature() {
        return true;
    }

    @Override
    public boolean canHandle(ServiceId requestServiceId, ProxyMessage requestProxyMessage) {
        if (requestProxyMessage.getRest().getVerb() != RestRequest.Verb.GET) {
            return false;
        }
        return Arrays.asList(LIST_METHODS, ALLOWED_METHODS, GET_OPENAPI).contains(requestServiceId.getServiceCode());
    }

    @Override
    public void startHandling(HttpServletRequest servletRequest, ProxyMessage requestProxyMessage,
                              ProxyMessageDecoder messageDecoder, ProxyMessageEncoder messageEncoder,
                              HttpClient restClient, HttpClient opMonitorClient,
                              OpMonitoringData opMonitoringData) throws Exception {
        restResponse = new RestResponse(requestProxyMessage.getRest().getClientId(),
                requestProxyMessage.getRest().getQueryId(),
                requestProxyMessage.getRest().getHash(),
                requestProxyMessage.getRest().getServiceId(),
                HttpStatus.SC_OK,
                "OK",
                requestProxyMessage.getRest().getHeaders(),
                servletRequest.getHeader(HEADER_REQUEST_ID)
        );

        restResponseBody = new CachingStream();
        if (requestProxyMessage.getRest().getServiceId().getServiceCode().equals(LIST_METHODS)) {
            handleListMethods(requestProxyMessage);
        } else if (requestProxyMessage.getRest().getServiceId().getServiceCode().equals(ALLOWED_METHODS)) {
            handleAllowedMethods(requestProxyMessage);
        } else if (requestProxyMessage.getRest().getServiceId().getServiceCode().equals(GET_OPENAPI)) {
            handleGetOpenApi(servletRequest, requestProxyMessage);
        }

        messageEncoder.restResponse(restResponse);
        messageEncoder.restBody(restResponseBody.getCachedContents());

        // It's required that in case of metadata service (where message is
        // not forwarded) the requestOutTs must be equal with the requestInTs
        // and the responseInTs must be equal with the responseOutTs.
        opMonitoringData.setRequestOutTs(opMonitoringData.getRequestInTs());
        opMonitoringData.setAssignResponseOutTsToResponseInTs(true);
    }

    private void handleListMethods(ProxyMessage requestProxyMessage) throws IOException {
        restResponse.getHeaders().add(new BasicHeader(MimeUtils.HEADER_CONTENT_TYPE, MimeTypes.JSON));
        MethodListType methodList = OBJECT_FACTORY.createMethodListType();
        methodList.getService().addAll(ServerConf.getServicesByDescriptionType(
                requestProxyMessage.getRest().getServiceId().getClientId(), DescriptionType.OPENAPI3));
        MAPPER.writeValue(restResponseBody, methodList);
    }

    private void handleAllowedMethods(ProxyMessage requestProxyMessage) throws IOException {
        restResponse.getHeaders().add(new BasicHeader(MimeUtils.HEADER_CONTENT_TYPE, MimeTypes.JSON));
        MethodListType methodList = OBJECT_FACTORY.createMethodListType();
        methodList.getService().addAll(ServerConf.getAllowedServicesByDescriptionType(
                requestProxyMessage.getRest().getServiceId().getClientId(),
                requestProxyMessage.getRest().getClientId(),
                DescriptionType.OPENAPI3));
        MAPPER.writeValue(restResponseBody, methodList);
    }

    private void handleGetOpenApi(HttpServletRequest servletRequest, ProxyMessage requestProxyMessage)
            throws URISyntaxException, IOException {
        // parse query string
        String fullURL = getFullURL(servletRequest) + "?" + requestProxyMessage.getRest().getQuery();
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
            throw new CodedException(X_INVALID_REQUEST,
                    "Missing serviceCode in message body");
        }

        ServiceId targetServiceId = ServiceId.create(requestProxyMessage.getRest().getServiceId().getClientId(),
                targetServiceCode);
        log.trace("targetServiceId={}", targetServiceId);

        DescriptionType descriptionType = ServerConf.getDescriptionType(targetServiceId);
        if (descriptionType == null) {
            throw new CodedException(X_INTERNAL_ERROR,
                    String.format("Service not found: %s", targetServiceId.toString()));
        }
        if (descriptionType != DescriptionType.OPENAPI3_DESCRIPTION) {
            throw new CodedException(X_INTERNAL_ERROR,
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
            restResponse.getHeaders().add(new BasicHeader(MimeUtils.HEADER_CONTENT_TYPE,
                    connection.getContentType()));
            log.trace("contentType={}", connection.getContentType());
        } catch (Exception e) {
            restResponse.getHeaders().add(new BasicHeader(MimeUtils.HEADER_CONTENT_TYPE,
                    DEFAULT_GETOPENAPI_CONTENT_TYPE));
            log.trace("Using default content type {}", DEFAULT_GETOPENAPI_CONTENT_TYPE);
        }

        byte[] buffer = new byte[BUFFER_SIZE_BYTES];
        URLConnection urlConnection = url.openConnection();
        urlConnection.setConnectTimeout(SystemProperties.getClientProxyTimeout());
        urlConnection.setReadTimeout(SystemProperties.getClientProxyHttpClientTimeout());
        try (InputStream inputStream = urlConnection.getInputStream()) {
            int length;
            do {
                length = inputStream.read(buffer);
                log.trace("read length={}", length);
                if (length > 0) {
                    restResponseBody.write(buffer, 0, length);
                    log.trace("wrote length={}", length);
                }
            } while (length > 0);
        } catch (IOException e) {
            throw new CodedException(X_INTERNAL_ERROR,
                    String.format("Failed reading service description from: %s", serviceDescriptionURL));
        }
    }

    @Override
    public RestResponse getRestResponse() {
        return restResponse;
    }

    @Override
    public CachingStream getRestResponseBody() {
        return restResponseBody;
    }

    @Override
    public void finishHandling() throws Exception {
        // NOP
    }

    private String getFullURL(HttpServletRequest request) {
        StringBuilder builder = new StringBuilder(request.getRequestURL().toString());
        String queryString = request.getQueryString();
        if (queryString == null || queryString.isEmpty()) {
            return builder.toString();
        } else {
            return builder.append('?').append(queryString).toString();
        }
    }
}
