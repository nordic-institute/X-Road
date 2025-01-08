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
package org.niis.xroad.edc.extension.dataplane.api;

import ee.ria.xroad.common.crypto.Digests;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.common.message.ResponseSoapParserImpl;
import ee.ria.xroad.common.message.RestMessage;
import ee.ria.xroad.common.message.RestRequest;
import ee.ria.xroad.common.message.RestResponse;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.message.SoapParser;
import ee.ria.xroad.common.message.SoapParserImpl;
import ee.ria.xroad.common.messagelog.RestLogMessage;
import ee.ria.xroad.common.messagelog.SoapLogMessage;
import ee.ria.xroad.common.signature.SignatureData;
import ee.ria.xroad.common.util.CacheInputStream;
import ee.ria.xroad.common.util.CachingStream;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.common.util.MimeUtils;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HEAD;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.StreamingOutput;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.bouncycastle.operator.DigestCalculator;
import org.bouncycastle.util.io.TeeOutputStream;
import org.eclipse.edc.connector.dataplane.spi.iam.DataPlaneAuthorizationService;
import org.eclipse.edc.connector.dataplane.spi.pipeline.PipelineService;
import org.eclipse.edc.connector.dataplane.spi.response.TransferErrorResponse;
import org.eclipse.edc.connector.dataplane.util.sink.AsyncStreamingDataSink;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowStartMessage;
import org.niis.xroad.edc.sig.SignatureResponse;
import org.niis.xroad.edc.spi.messagelog.XRoadMessageLog;

import java.io.ByteArrayInputStream;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;

import static ee.ria.xroad.common.util.EncoderUtils.encodeBase64;
import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.WILDCARD;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.FORBIDDEN;
import static jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static jakarta.ws.rs.core.Response.Status.UNAUTHORIZED;
import static jakarta.ws.rs.core.Response.status;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.eclipse.edc.connector.dataplane.spi.schema.DataFlowRequestSchema.BODY;
import static org.eclipse.edc.connector.dataplane.spi.schema.DataFlowRequestSchema.MEDIA_TYPE;
import static org.niis.xroad.edc.sig.PocConstants.HEADER_XRD_SIG;

@Path("/lc/{any:.*}")
@Produces(WILDCARD)
public class XrdDataPlanePublicApiController {

    private final PipelineService pipelineService;
    private final DataFlowRequestSupplier requestSupplier;
    private final XrdEdcSignService signService;
    private final XRoadMessageLog xRoadMessageLog;
    private final Monitor monitor;

    private final ExecutorService executorService;
    private final DataPlaneAuthorizationService authorizationService;

    public XrdDataPlanePublicApiController(PipelineService pipelineService,
                                           XrdEdcSignService xrdEdcSignService, Monitor monitor,
                                           ExecutorService executorService,
                                           XRoadMessageLog xRoadMessageLog, DataPlaneAuthorizationService authorizationService) {
        this.pipelineService = pipelineService;
        this.signService = xrdEdcSignService;
        this.monitor = monitor;
        this.executorService = executorService;
        this.xRoadMessageLog = xRoadMessageLog;
        this.authorizationService = authorizationService;
        this.requestSupplier = new DataFlowRequestSupplier();
    }

    @GET
    public void get(@Context ContainerRequestContext requestContext, @Suspended AsyncResponse response) {
        handle(requestContext, response);
    }

    @HEAD
    public void head(@Context ContainerRequestContext requestContext, @Suspended AsyncResponse response) {
        handle(requestContext, response);
    }

    @POST
    public void post(@Context ContainerRequestContext requestContext, @Suspended AsyncResponse response) {
        handle(requestContext, response);
    }

    @PUT
    public void put(@Context ContainerRequestContext requestContext, @Suspended AsyncResponse response) {
        handle(requestContext, response);
    }

    @DELETE
    public void delete(@Context ContainerRequestContext requestContext, @Suspended AsyncResponse response) {
        handle(requestContext, response);
    }

    @PATCH
    public void patch(@Context ContainerRequestContext requestContext, @Suspended AsyncResponse response) {
        handle(requestContext, response);
    }

    private boolean isSoap(DataFlowStartMessage dataFlowStartMessage) {
        return Optional.ofNullable(dataFlowStartMessage.getProperties().get(MEDIA_TYPE))
                .map(contentType -> contentType.contains("text/xml"))
                .orElse(false);
    }

    private void handle(ContainerRequestContext requestContext, AsyncResponse response) {
        var contextApi = new ContainerRequestContextApiImpl(requestContext);

        var token = contextApi.headers().get(HttpHeaders.AUTHORIZATION);
        if (token == null) {
            response.resume(error(UNAUTHORIZED, "Missing Authorization Header"));
            return;
        }

        var sourceDataAddress = authorizationService.authorize(token, buildRequestData(requestContext));
        if (sourceDataAddress.failed()) {
            response.resume(error(FORBIDDEN, sourceDataAddress.getFailureDetail()));
            return;
        }

        var startMessage = requestSupplier.apply(contextApi, sourceDataAddress.getContent());

        processRequest(contextApi, startMessage, response);
    }

    private Map<String, Object> buildRequestData(ContainerRequestContext requestContext) {
        var requestData = new HashMap<String, Object>();
        requestData.put("headers", requestContext.getHeaders());

        var uriInfo = requestContext.getUriInfo();
        requestData.put("path", uriInfo);

        var path = uriInfo.getPath();
        requestData.put("resolvedPath", path.startsWith("/") ? path.substring(1) : path);
        requestData.put("method", requestContext.getMethod());
        requestData.put("content-type", requestContext.getMediaType());
        return requestData;
    }

    private void processRequest(ContainerRequestContextApiImpl contextApi, DataFlowStartMessage dataFlowStartMessage,
                                AsyncResponse response) {
        var assetId = dataFlowStartMessage.getAssetId();
        if (isBlank(assetId)) {
            response.resume(error(BAD_REQUEST, "Missing assetId"));
            return;
        }
        var serviceId = ServiceId.Conf.fromEncodedId(assetId);
        boolean isSoap = isSoap(dataFlowStartMessage);

        byte[] requestDigest = logAndVerifyRequest(dataFlowStartMessage, contextApi, serviceId, isSoap);

        AsyncStreamingDataSink.AsyncResponseContext asyncResponseContext = callback -> {
            StreamingOutput output = t -> callback.outputStreamConsumer().accept(t);
            try {
                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", callback.mediaType());
                headers.put("Date", DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.US).format(ZonedDateTime.now()));

                CachingStream cachingStream = new CachingStream();
                if (isSoap) {
                    output.write(cachingStream);
                    return response.resume(handleSuccessSoap(cachingStream, headers, serviceId, contextApi, requestDigest));
                } else {
                    // calculate the body digest while caching stream, it will be required for signing response later
                    DigestCalculator dc = Digests.createDigestCalculator(Digests.DEFAULT_DIGEST_ALGORITHM);
                    TeeOutputStream teeOutputStream = new TeeOutputStream(dc.getOutputStream(), cachingStream);
                    output.write(teeOutputStream);
                    byte[] responseBodyDigest = dc.getDigest();
                    return response.resume(handleSuccessRest(cachingStream, headers, serviceId, contextApi, requestDigest,
                            responseBodyDigest));
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        };

        var sink = new AsyncStreamingDataSink(asyncResponseContext, executorService);
        pipelineService.transfer(dataFlowStartMessage, sink)
                .whenComplete((result, throwable) -> {
                    if (throwable == null) {
                        if (result.failed()) {
                            response.resume(error(INTERNAL_SERVER_ERROR, result.getFailureDetail()));
                        }
                    } else {
                        var error = "Unhandled exception occurred during data transfer: " + throwable.getMessage();
                        response.resume(error(INTERNAL_SERVER_ERROR, error));
                    }
                });
    }

    private List<Header> toSortedHeadersList(Map<String, String> headers, String... exclude) {
        Set<String> excludedHeaders = exclude != null ? Set.of(exclude) : Set.of();
        return headers.entrySet().stream()
                .filter(e -> !excludedHeaders.contains(e.getKey()))
                .map(e -> new BasicHeader(e.getKey(), e.getValue()))
                .sorted(new RestMessage.HeadersComparator())
                .collect(toList());
    }

    private byte[] logAndVerifyRequest(DataFlowStartMessage dataFlowRequest, ContainerRequestContextApi contextApi,
                                       ServiceId.Conf serviceId, boolean isSoap) {
        try {
            // todo: use stream?
            byte[] requestBody = dataFlowRequest.getProperties().containsKey(BODY)
                    ? dataFlowRequest.getProperties().get(BODY).getBytes() : null;
            String signatureXml = getSignatureFromHeaders(contextApi.headers());
            String signature = contextApi.headers().get(HEADER_XRD_SIG);
            String xRequestId = contextApi.headers().get(MimeUtils.HEADER_REQUEST_ID);

            if (isSoap) {
                var soapParser = new SoapParserImpl();
                SoapMessageImpl soapMessage = (SoapMessageImpl) soapParser.parse(MimeTypes.TEXT_XML_UTF8,
                        new ByteArrayInputStream(requestBody));

                SoapLogMessage logMessage = new SoapLogMessage(
                        soapMessage,
                        new SignatureData(signatureXml),
                        // todo attachments?
                        List.of(),
                        false,
                        xRequestId);

                xRoadMessageLog.log(logMessage);
                signService.verifyRequest(signature, soapMessage.getBytes(), null, soapMessage.getClient());

                return soapMessage.getHash();
            } else { // rest message
                RestRequest restRequest = new RestRequest(
                        contextApi.method(),
                        "/r%d/%s".formatted(RestMessage.PROTOCOL_VERSION, serviceId.toShortString()),
                        defaultIfBlank(contextApi.queryParams(), null),
                        toSortedHeadersList(contextApi.headers(), HEADER_XRD_SIG, "Authorization"),
                        xRequestId);

                RestLogMessage logMessage = new RestLogMessage(contextApi.headers().get(MimeUtils.HEADER_QUERY_ID),
                        restRequest.getClientId(),
                        serviceId,
                        restRequest,
                        new SignatureData(signatureXml),
                        bodyAsStream(requestBody),
                        false,
                        xRequestId);

                xRoadMessageLog.log(logMessage);
                signService.verifyRequest(signature, restRequest.getMessageBytes(), requestBody, restRequest.getClientId());

                return calculateRestRequestDigest(restRequest, requestBody);
            }
        } catch (Exception e) {
            monitor.severe("Failed to verify request", e);
            throw new EdcException(e);
        }
    }

    private byte[] calculateRestRequestDigest(RestRequest request, byte[] body) throws Exception {
        if (body != null) {
            var dc = Digests.createDigestCalculator(Digests.DEFAULT_DIGEST_ALGORITHM);
            dc.getOutputStream().write(body);
            var bodyDigest = dc.getDigest();

            dc = Digests.createDigestCalculator(Digests.DEFAULT_DIGEST_ALGORITHM);
            try (var out = dc.getOutputStream()) {
                out.write(request.getHash());
                out.write(bodyDigest);
            }
            return dc.getDigest();
        }
        return request.getHash();
    }

    private CacheInputStream bodyAsStream(byte[] body) throws Exception {
        if (body != null) {
            CachingStream cs = new CachingStream();
            cs.write(body);
            return cs.getCachedContents();
        }
        return null;
    }

    private String getSignatureFromHeaders(Map<String, String> headers) {
        return new String(Base64.getDecoder().decode(headers.get(HEADER_XRD_SIG)));
    }

    private String getClientIdFromHeaders(Map<String, String> headers) {
        for (Map.Entry<String, String> header : headers.entrySet()) {
            if (header.getKey().equalsIgnoreCase(MimeUtils.HEADER_CLIENT_ID)) {
                return header.getValue();
            }
        }
        throw new EdcException("Missing clientID header.");
    }

    private Response handleSuccessRest(CachingStream responseStream, Map<String, String> headers, ServiceId.Conf serviceId,
                                       ContainerRequestContextApi contextApi, byte[] requestDigest, byte[] responseBodyDigest) {
        try {
            var queryId = contextApi.headers().get(MimeUtils.HEADER_QUERY_ID);
            var xRequestId = contextApi.headers().get(MimeUtils.HEADER_REQUEST_ID);
            var clientId = RestMessage.decodeClientId(getClientIdFromHeaders(contextApi.headers()));

            var restResponse = new RestResponse(clientId,
                    queryId,
                    requestDigest,
                    serviceId,
                    HTTP_OK, "OK",   // todo: can't get them here
                    toSortedHeadersList(headers),
                    xRequestId);

            SignatureResponse signatureResponse = this.signService.sign(serviceId, restResponse.getMessageBytes(),
                    encodeBase64(responseBodyDigest));

            var logMessage = new RestLogMessage(
                    queryId,
                    restResponse.getClientId(),
                    serviceId,
                    restResponse,
                    new SignatureData(signatureResponse.getSignatureDecoded()),
                    responseStream.getCachedContents(),
                    false,
                    xRequestId);
            xRoadMessageLog.log(logMessage);

            var builder = Response.ok((StreamingOutput) output -> responseStream.getCachedContents().transferTo(output));
            restResponse.getHeaders().forEach(h -> builder.header(h.getName(), h.getValue()));
            builder.header(HEADER_XRD_SIG, signatureResponse.getSignature());
            return builder.build();
        } catch (Exception e) {
            monitor.severe("Failed to sign response payload", e);
            throw new RuntimeException(e);
        }
    }

    private Response handleSuccessSoap(CachingStream responseStream, Map<String, String> headers, ServiceId.Conf serviceId,
                                       ContainerRequestContextApi contextApi, byte[] requestDigest) {
        try {
            var xRequestId = contextApi.headers().get(MimeUtils.HEADER_REQUEST_ID);

            SoapParser soapParser = new ResponseSoapParserImpl(requestDigest);
            SoapMessageImpl soapMessage = (SoapMessageImpl) soapParser.parse(MimeTypes.TEXT_XML_UTF8,
                    responseStream.getCachedContents());

            SignatureResponse signatureResponse = this.signService.sign(serviceId, soapMessage.getBytes());

            var logMessage = new SoapLogMessage(
                    soapMessage,
                    new SignatureData(signatureResponse.getSignatureDecoded()),
                    // todo attachments?
                    List.of(),
                    false,
                    xRequestId);
            xRoadMessageLog.log(logMessage);

            var builder = Response.ok(soapMessage.getBytes());
            headers.forEach(builder::header);
            builder.header(HEADER_XRD_SIG, signatureResponse.getSignature());
            return builder.build();
        } catch (Exception e) {
            monitor.severe("Failed to sign response payload", e);
            throw new RuntimeException(e);
        }
    }


    private static Response error(Response.Status status, String error) {
        return status(status).type(APPLICATION_JSON).entity(new TransferErrorResponse(List.of(error))).build();
    }

}
