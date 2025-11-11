/*
 * The MIT License
 *
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
package org.niis.xroad.proxy.core.addon.metaservice.serverproxy;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.RestRequest;
import ee.ria.xroad.common.message.RestResponse;
import ee.ria.xroad.common.util.CachingStream;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.common.util.MimeUtils;
import ee.ria.xroad.common.util.RequestWrapper;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
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
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.proxy.core.protocol.ProxyMessage;
import org.niis.xroad.proxy.core.protocol.ProxyMessageDecoder;
import org.niis.xroad.proxy.core.protocol.ProxyMessageEncoder;
import org.niis.xroad.proxy.core.serverproxy.HttpClientCreator;
import org.niis.xroad.proxy.core.serverproxy.RestServiceHandler;
import org.niis.xroad.proxy.core.util.OpenapiDescriptionFiletype;
import org.niis.xroad.serverconf.ServerConfProvider;
import org.niis.xroad.serverconf.model.DescriptionType;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static ee.ria.xroad.common.ErrorCodes.X_INTERNAL_ERROR;
import static ee.ria.xroad.common.ErrorCodes.X_INVALID_REQUEST;
import static ee.ria.xroad.common.util.MimeUtils.HEADER_REQUEST_ID;
import static org.niis.xroad.proxy.core.util.MetadataRequests.ALLOWED_METHODS;
import static org.niis.xroad.proxy.core.util.MetadataRequests.GET_OPENAPI;
import static org.niis.xroad.proxy.core.util.MetadataRequests.LIST_METHODS;

/**
 * Handler for REST metadata services
 */
@Slf4j
public class RestMetadataServiceHandlerImpl implements RestServiceHandler {

    private static final String QUERY_PARAM_SERVICECODE = "serviceCode";
    private static final String DEFAULT_GETOPENAPI_CONTENT_TYPE = "text/plain";

    static final ObjectMapper MAPPER;

    static {
        final ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        mapper.setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
        MAPPER = mapper;
    }

    private final ServerConfProvider serverConfProvider;
    private final HttpClientCreator httpClientCreator;
    private final String tmpDir;

    private RestResponse restResponse;
    private CachingStream restResponseBody;

    public RestMetadataServiceHandlerImpl(ServerConfProvider serverConfProvider, String[] tlsProtocols, String[] tlsCipherSuites,
                                          String tmpDir) {
        this.serverConfProvider = serverConfProvider;
        this.httpClientCreator = new HttpClientCreator(serverConfProvider, tlsProtocols, tlsCipherSuites);
        this.tmpDir = tmpDir;
    }

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
    public void startHandling(RequestWrapper servletRequest, ProxyMessage requestProxyMessage,
                              ProxyMessageDecoder messageDecoder, ProxyMessageEncoder messageEncoder,
                              HttpClient restClient, OpMonitoringData opMonitoringData)
            throws IOException, URISyntaxException, HttpClientCreator.HttpClientCreatorException {
        restResponse = new RestResponse(requestProxyMessage.getRest().getClientId(),
                requestProxyMessage.getRest().getQueryId(),
                requestProxyMessage.getRest().getHash(),
                requestProxyMessage.getRest().getServiceId(),
                HttpStatus.SC_OK,
                "OK",
                requestProxyMessage.getRest().getHeaders(),
                servletRequest.getHeaders().get(HEADER_REQUEST_ID)
        );

        restResponseBody = new CachingStream(tmpDir);
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
                serverConfProvider.getRestServices(requestProxyMessage.getRest().getServiceId().getClientId()));
    }

    private void handleAllowedMethods(ProxyMessage requestProxyMessage) throws IOException {
        restResponse.getHeaders().add(new BasicHeader(MimeUtils.HEADER_CONTENT_TYPE, MimeTypes.JSON));
        MAPPER.writeValue(restResponseBody,
                serverConfProvider.getAllowedRestServices(requestProxyMessage.getRest().getServiceId().getClientId(),
                        requestProxyMessage.getRest().getClientId())
        );
    }

    private void handleGetOpenApi(ProxyMessage requestProxyMessage)
            throws IOException, HttpClientCreator.HttpClientCreatorException, URISyntaxException {
        List<NameValuePair> pairs = URLEncodedUtils.parse(requestProxyMessage.getRest().getQuery(),
                StandardCharsets.UTF_8);
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

        DescriptionType descriptionType = serverConfProvider.getDescriptionType(targetServiceId);
        if (descriptionType == null) {
            throw new CodedException(X_INTERNAL_ERROR,
                    String.format("Service not found: %s", targetServiceId));
        }
        if (descriptionType != DescriptionType.OPENAPI3) {
            throw new CodedException(X_INTERNAL_ERROR,
                    String.format("Invalid service type: %s", descriptionType));
        }

        String serviceDescriptionURL = serverConfProvider.getServiceDescriptionURL(targetServiceId);

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
