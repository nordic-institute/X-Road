/*
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
package org.niis.xroad.proxy.core.serverproxy;

import ee.ria.xroad.common.crypto.Digests;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.util.RequestWrapper;
import ee.ria.xroad.common.util.TimeUtils;

import jakarta.inject.Named;
import jakarta.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.input.TeeInputStream;
import org.apache.http.Header;
import org.apache.http.HttpEntityEnclosingRequest;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.HttpClient;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpOptions;
import org.apache.http.client.methods.HttpPatch;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpTrace;
import org.apache.http.entity.InputStreamEntity;
import org.apache.http.protocol.BasicHttpContext;
import org.apache.http.protocol.HttpContext;
import org.apache.http.util.EntityUtils;
import org.bouncycastle.operator.DigestCalculator;
import org.niis.xroad.common.core.annotation.ArchUnitSuppressed;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.properties.CommonProperties;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.proxy.core.protocol.ProxyMessage;
import org.niis.xroad.proxy.core.protocol.ProxyMessageEncoder;
import org.niis.xroad.proxy.core.util.CachingStream;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;

import static ee.ria.xroad.common.util.MimeUtils.HEADER_REQUEST_ID;
import static ee.ria.xroad.common.util.TimeUtils.getEpochMillisecond;
import static org.niis.xroad.common.core.exception.ErrorCode.SERVICE_MISSING_URL;
import static org.niis.xroad.proxy.core.configuration.ServerProxyConfig.SERVER_PROXY_HTTP_CLIENT;

/**
 * Default REST service handler that forwards the request to the configured service address.
 */
@Slf4j
@Singleton
public class DefaultRestServiceHandlerImpl implements RestServiceHandler {

    private final ServerConfProvider serverConfProvider;
    private final HttpClient serverHttpClient;
    private final CommonProperties commonProperties;

    public DefaultRestServiceHandlerImpl(ServerConfProvider serverConfProvider,
                                         @Named(SERVER_PROXY_HTTP_CLIENT) HttpClient serverHttpClient,
                                         CommonProperties commonProperties) {
        this.serverConfProvider = serverConfProvider;
        this.serverHttpClient = serverHttpClient;
        this.commonProperties = commonProperties;
    }

    @Override
    public boolean shouldVerifyAccess() {
        return true;
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
    public boolean canHandle(ServiceId requestSrvcId, ProxyMessage requestProxyMessage) {
        return true;
    }

    @Override
    @ArchUnitSuppressed("NoVanillaExceptions")
    public RestServiceHandlerResult startHandling(RequestWrapper request, ProxyMessage requestProxyMessage,
                                                  ProxyMessageEncoder messageEncoder,
                                                  OpMonitoringData monitoringData) throws IOException {
        String address = serverConfProvider.getServiceAddress(requestProxyMessage.getRest().getServiceId());
        if (address == null || address.isEmpty()) {
            throw XrdRuntimeException.systemException(SERVICE_MISSING_URL,
                    "Service address not specified for '%s'".formatted(requestProxyMessage.getRest().getServiceId()));
        }

        address = concatPath(address, requestProxyMessage.getRest().getServicePath());
        final String query = requestProxyMessage.getRest().getQuery();
        if (query != null) {
            address += "?" + query;
        }

        HttpRequestBase req = switch (requestProxyMessage.getRest().getVerb()) {
            case GET -> new HttpGet(address);
            case POST -> new HttpPost(address);
            case PUT -> new HttpPut(address);
            case DELETE -> new HttpDelete(address);
            case PATCH -> new HttpPatch(address);
            case OPTIONS -> new HttpOptions(address);
            case HEAD -> new HttpHead(address);
            case TRACE -> new HttpTrace(address);
        };

        int timeout = TimeUtils.secondsToMillis(serverConfProvider
                .getServiceTimeout(requestProxyMessage.getRest().getServiceId()));
        req.setConfig(RequestConfig
                .custom()
                .setSocketTimeout(timeout)
                .build());

        for (Header header : requestProxyMessage.getRest().getHeaders()) {
            req.addHeader(header);
        }

        if (req instanceof HttpEntityEnclosingRequest httpEntityEnclosingRequest && requestProxyMessage.hasRestBody()) {
            httpEntityEnclosingRequest.setEntity(new InputStreamEntity(requestProxyMessage.getRestBody(),
                    requestProxyMessage.getRestBody().size()));
        }

        final HttpContext ctx = new BasicHttpContext();
        ctx.setAttribute(ServiceId.class.getName(), requestProxyMessage.getRest().getServiceId());
        monitoringData.setRequestOutTs(getEpochMillisecond());
        final HttpResponse response = serverHttpClient.execute(req, ctx);
        monitoringData.setResponseInTs(getEpochMillisecond());
        final StatusLine statusLine = response.getStatusLine();

        // calculate request hash
        byte[] requestDigest = calculateRequestDigest(requestProxyMessage);

        var restResponse = new ee.ria.xroad.common.message.RestResponse(
                requestProxyMessage.getRest().getClientId(),
                requestProxyMessage.getRest().getQueryId(),
                requestDigest,
                requestProxyMessage.getRest().getServiceId(),
                statusLine.getStatusCode(),
                statusLine.getReasonPhrase(),
                Arrays.asList(response.getAllHeaders()),
                request.getHeaders().get(HEADER_REQUEST_ID)
        );
        messageEncoder.restResponse(restResponse);

        CachingStream restResponseBody = null;
        if (response.getEntity() != null) {
            restResponseBody = new CachingStream(commonProperties.tempFilesPath());
            TeeInputStream tee = new TeeInputStream(response.getEntity().getContent(), restResponseBody);
            messageEncoder.restBody(tee);
            EntityUtils.consume(response.getEntity());
        }

        monitoringData.setResponseAttachmentCount(0);
        monitoringData.setResponseSize(restResponse.getMessageBytes().length
                + messageEncoder.getAttachmentsByteCount());

        return new RestServiceHandlerResult(restResponse, restResponseBody);
    }

    private byte[] calculateRequestDigest(ProxyMessage requestProxyMessage) throws IOException {
        if (requestProxyMessage instanceof ServerRestMessageProcessor.VerifyingProxyMessage verifyingProxyMessage
                && verifyingProxyMessage.getDecoder() != null
                && verifyingProxyMessage.getDecoder().getRestBodyDigest() != null) {
            final DigestCalculator dc = Digests.createDigestCalculator(Digests.DEFAULT_DIGEST_ALGORITHM);
            try (OutputStream out = dc.getOutputStream()) {
                out.write(requestProxyMessage.getRest().getHash());
                out.write(verifyingProxyMessage.getDecoder().getRestBodyDigest());
            }
            return dc.getDigest();

        }
        return requestProxyMessage.getRest().getHash();
    }

    private String concatPath(String address, String path) {
        if (path == null || path.isEmpty()) return address;
        if (address.endsWith("/") && path.startsWith("/")) {
            return address.concat(path.substring(1));
        }
        return address.concat(path);
    }
}
