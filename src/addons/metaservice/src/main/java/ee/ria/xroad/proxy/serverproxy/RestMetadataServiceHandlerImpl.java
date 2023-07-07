/**
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
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
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.conf.serverconf.model.DescriptionType;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.RestRequest;
import ee.ria.xroad.common.message.RestResponse;
import ee.ria.xroad.common.metadata.ObjectFactory;
import ee.ria.xroad.common.opmonitoring.OpMonitoringData;
import ee.ria.xroad.common.util.CachingStream;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.common.util.MimeUtils;
import ee.ria.xroad.common.util.OpenapiDescriptionFiletype;
import ee.ria.xroad.proxy.protocol.ProxyMessage;
import ee.ria.xroad.proxy.protocol.ProxyMessageDecoder;
import ee.ria.xroad.proxy.protocol.ProxyMessageEncoder;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.Header;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.message.BasicHeader;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;

import javax.servlet.http.HttpServletRequest;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
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

    private HttpClientCreator httpClientCreator = new HttpClientCreator();

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
            handleGetOpenApi(requestProxyMessage);
        }

        messageEncoder.restResponse(restResponse);
        messageEncoder.restBody(restResponseBody.getCachedContents());

        // It's required that in case of metadata service (where message is
        // not forwarded) the requestOutTs must be equal with the requestInTs
        // and the responseInTs must be equal with the responseOutTs.
        opMonitoringData.setRequestOutTs(opMonitoringData.getRequestInTs());
        opMonitoringData.setAssignResponseOutTsToResponseInTs(true);
        opMonitoringData.setServiceType(DescriptionType.REST.name());
    }

    private void handleListMethods(ProxyMessage requestProxyMessage) throws IOException {
        restResponse.getHeaders().add(new BasicHeader(MimeUtils.HEADER_CONTENT_TYPE, MimeTypes.JSON));
        MAPPER.writeValue(restResponseBody,
                ServerConf.getRestServices(requestProxyMessage.getRest().getServiceId().getClientId()));
    }

    private void handleAllowedMethods(ProxyMessage requestProxyMessage) throws IOException {
        restResponse.getHeaders().add(new BasicHeader(MimeUtils.HEADER_CONTENT_TYPE, MimeTypes.JSON));
        MAPPER.writeValue(restResponseBody,
                ServerConf.getAllowedRestServices(requestProxyMessage.getRest().getServiceId().getClientId(),
                        requestProxyMessage.getRest().getClientId())
        );
    }

    private void handleGetOpenApi(ProxyMessage requestProxyMessage) throws IOException,
            HttpClientCreator.HttpClientCreatorException, URISyntaxException {
        List<NameValuePair> pairs = URLEncodedUtils.parse(requestProxyMessage.getRest().getQuery(),
                Charset.forName("UTF-8"));
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

        ServiceId.Conf targetServiceId = ServiceId.Conf.create(
                requestProxyMessage.getRest().getServiceId().getClientId(),
                targetServiceCode);
        log.trace("targetServiceId={}", targetServiceId);

        DescriptionType descriptionType = ServerConf.getDescriptionType(targetServiceId);
        if (descriptionType == null) {
            throw new CodedException(X_INTERNAL_ERROR,
                    String.format("Service not found: %s", targetServiceId.toString()));
        }
        if (descriptionType != DescriptionType.OPENAPI3) {
            throw new CodedException(X_INTERNAL_ERROR,
                    String.format("Invalid service type: %s", descriptionType.toString()));
        }

        String serviceDescriptionURL = ServerConf.getServiceDescriptionURL(targetServiceId);

        HttpClient client = httpClientCreator.getHttpClient();

        HttpContext httpContext = new BasicHttpContext();

        // ServerMessageProcessor uses the same method to pass the ServiceId to CustomSSLSocketFactory
        httpContext.setAttribute(ServiceId.class.getName(), targetServiceId);

        URI uri = new URI(serviceDescriptionURL);
        HttpResponse response = client.execute(new HttpGet(uri), httpContext);
        StatusLine statusLine = response.getStatusLine();

        if (HttpStatus.SC_OK != statusLine.getStatusCode()) {
            throw new CodedException(X_INTERNAL_ERROR,
                    String.format("Failed reading service description from %s. Status: %s Reason: %s",
                            serviceDescriptionURL, statusLine.getStatusCode(), statusLine.getReasonPhrase()));
        }

        InputStream responseContent = response.getEntity().getContent();

        try {
            OpenapiDescriptionFiletype filetype = getFileType(response, uri);
            Openapi3Anonymiser anonymiser = new Openapi3Anonymiser();
            if (OpenapiDescriptionFiletype.JSON.equals(filetype)) {
                anonymiser.anonymiseJson(responseContent, restResponseBody);
            } else {
                anonymiser.anonymiseYaml(responseContent, restResponseBody);
            }
        } catch (IOException e) {
            throw new CodedException(X_INTERNAL_ERROR,
                    String.format("Failed overwriting origin URL for the openapi servers for %s",
                            serviceDescriptionURL));
        }

        if (response.containsHeader(MimeUtils.HEADER_CONTENT_TYPE)) {
            restResponse.getHeaders().add(new BasicHeader(MimeUtils.HEADER_CONTENT_TYPE,
                    response.getFirstHeader(MimeUtils.HEADER_CONTENT_TYPE).getValue()));
        } else {
            restResponse.getHeaders().add(new BasicHeader(MimeUtils.HEADER_CONTENT_TYPE,
                    DEFAULT_GETOPENAPI_CONTENT_TYPE));
        }

    }

    private OpenapiDescriptionFiletype getFileType(HttpResponse response, URI uri) {
        boolean isJson = false;
        boolean isYaml = false;
        Header[] contentTypeHeaders = response.getHeaders("content-type");
        for (Header header : contentTypeHeaders) {
            String contentType = header.getValue();
            if (contentType.contains("application/json")) {
                isJson = true;
            } else if ("application/x-yaml".equals(contentType)) {
                isYaml = true;
            }
        }

        if (isJson) {
            return OpenapiDescriptionFiletype.JSON;
        } else if (isYaml) {
            return OpenapiDescriptionFiletype.YAML;
        }

        return uri.getPath().endsWith(".json")
                ? OpenapiDescriptionFiletype.JSON : OpenapiDescriptionFiletype.YAML;
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
    public void finishHandling() {
        // NOP
    }
}
