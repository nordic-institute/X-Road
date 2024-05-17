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
package org.niis.xroad.edc.extension.signer;

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
import ee.ria.xroad.common.util.CryptoUtils;
import ee.ria.xroad.common.util.MimeTypes;
import ee.ria.xroad.common.util.MimeUtils;

import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
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
import lombok.SneakyThrows;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.message.BasicHeader;
import org.bouncycastle.util.io.TeeOutputStream;
import org.eclipse.edc.connector.dataplane.api.controller.ContainerRequestContextApi;
import org.eclipse.edc.connector.dataplane.api.controller.ContainerRequestContextApiImpl;
import org.eclipse.edc.connector.dataplane.api.controller.DataFlowRequestSupplier;
import org.eclipse.edc.connector.dataplane.api.controller.DataPlanePublicApi;
import org.eclipse.edc.connector.dataplane.spi.pipeline.PipelineService;
import org.eclipse.edc.connector.dataplane.spi.resolver.DataAddressResolver;
import org.eclipse.edc.connector.dataplane.spi.response.TransferErrorResponse;
import org.eclipse.edc.connector.dataplane.util.sink.AsyncStreamingDataSink;
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
import java.util.Set;
import java.util.concurrent.ExecutorService;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.WILDCARD;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.FORBIDDEN;
import static jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static jakarta.ws.rs.core.Response.status;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.util.stream.Collectors.toList;
import static org.apache.commons.lang3.StringUtils.defaultIfBlank;
import static org.eclipse.edc.connector.dataplane.spi.schema.DataFlowRequestSchema.BODY;
import static org.niis.xroad.edc.sig.PocConstants.HEADER_XRD_SIG;

@Path("{any:.*}")
@Produces(WILDCARD)
public class XrdDataPlanePublicApiController implements DataPlanePublicApi {

    private final PipelineService pipelineService;
    private final DataAddressResolver dataAddressResolver;
    private final DataFlowRequestSupplier requestSupplier;
    private final XrdEdcSignService signService;
    private final XRoadMessageLog xRoadMessageLog;
    private final Monitor monitor;
    private final ExecutorService executorService;

    public XrdDataPlanePublicApiController(PipelineService pipelineService, DataAddressResolver dataAddressResolver,
                                           XrdEdcSignService xrdEdcSignService, Monitor monitor,
                                           ExecutorService executorService, XRoadMessageLog xRoadMessageLog) {
        this.pipelineService = pipelineService;
        this.dataAddressResolver = dataAddressResolver;
        this.signService = xrdEdcSignService;
        this.monitor = monitor;
        this.executorService = executorService;
        this.xRoadMessageLog = xRoadMessageLog;
        this.requestSupplier = new DataFlowRequestSupplier();
    }

    @GET
    @Override
    public void get(@Context ContainerRequestContext requestContext, @Suspended AsyncResponse response) {
        handle(requestContext, response);
    }

    @DELETE
    @Override
    public void delete(@Context ContainerRequestContext requestContext, @Suspended AsyncResponse response) {
        handle(requestContext, response);
    }

    @PATCH
    @Override
    public void patch(@Context ContainerRequestContext requestContext, @Suspended AsyncResponse response) {
        handle(requestContext, response);
    }

    @PUT
    @Override
    public void put(@Context ContainerRequestContext requestContext, @Suspended AsyncResponse response) {
        handle(requestContext, response);
    }

    @POST
    @Override
    public void post(@Context ContainerRequestContext requestContext, @Suspended AsyncResponse response) {
        handle(requestContext, response);
    }

    private boolean isSoap(ContainerRequestContext context) {
        return StringUtils.contains(context.getHeaderString("Content-Type"), "text/xml");
    }

    private void handle(ContainerRequestContext context, AsyncResponse response) {
        var contextApi = new ContainerRequestContextApiImpl(context);
        var token = contextApi.headers().get(HttpHeaders.AUTHORIZATION);
        if (token == null) {
            response.resume(error(BAD_REQUEST, "Missing token"));
            return;
        }

        var tokenValidation = dataAddressResolver.resolve(token);
        if (tokenValidation.failed()) {
            response.resume(error(FORBIDDEN, tokenValidation.getFailureDetail()));
            return;
        }

        var dataAddress = tokenValidation.getContent();
        var dataFlowRequest = requestSupplier.apply(contextApi, dataAddress); // reads the request input stream

        var assetId = dataAddress.getStringProperty("assetId");
        var serviceId = ServiceId.Conf.fromEncodedId(assetId);

        boolean isSoap = isSoap(context);

        byte[] requestDigest = logAndVerifyRequest(dataFlowRequest, contextApi, serviceId, isSoap);

        AsyncStreamingDataSink.AsyncResponseContext asyncResponseContext = callback -> {
            StreamingOutput output = t -> callback.outputStreamConsumer().accept(t);

            try {
                var digestCalculator = CryptoUtils.createDigestCalculator(CryptoUtils.DEFAULT_CERT_HASH_ALGORITHM_ID);
                CachingStream cachingStream = new CachingStream();
                TeeOutputStream tos = new TeeOutputStream(digestCalculator.getOutputStream(), cachingStream);
                output.write(tos);

//                var digest = digestCalculator.getDigest();
//                responseBytes = IOUtils.toByteArray(cachingStream.getCachedContents());

                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", callback.mediaType());
                headers.put("Date", DateTimeFormatter.RFC_1123_DATE_TIME.withLocale(Locale.US).format(ZonedDateTime.now()));
                //TODO edc is not passing response headers??
                var resp = handleSuccess(cachingStream, headers, serviceId, contextApi, isSoap, requestDigest);

                return response.resume(resp);

            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            //Instead of streaming directly to consumer, we will stream to a byte array and then sign the response
//            var outputStream = new ByteArrayOutputStream();
//            try {
//                output.write(outputStream);
//            } catch (IOException e) {
//                throw new RuntimeException(e);
//            }


        };

        var sink = new AsyncStreamingDataSink(asyncResponseContext, executorService);
        pipelineService.transfer(dataFlowRequest, sink)
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

    private List<Header> toHeadersList(Map<String, String> headers, String... exclude) {
        Set<String> excludedHeaders = exclude != null ? Set.of(exclude) : Set.of();
        return headers.entrySet().stream()
                .filter(e -> !excludedHeaders.contains(e.getKey()))
                .map(e -> new BasicHeader(e.getKey(), e.getValue()))
                .sorted((h1, h2) -> {
                    int nameCompare = h1.getName().compareToIgnoreCase(h2.getName());
                    if (nameCompare != 0) {
                        return nameCompare;
                    }
                    if (h1.getValue() == null) {
                        return h2.getValue() == null ? 0 : -1;
                    }
                    if (h2.getValue() == null) {
                        return 1;
                    }
                    return h1.getValue().compareTo(h2.getValue());
                })
                .collect(toList());
    }

    @SneakyThrows
    private byte[] logAndVerifyRequest(DataFlowStartMessage dataFlowRequest, ContainerRequestContextApi contextApi,
                                       ServiceId.Conf serviceId, boolean isSoap) {
        // todo: use stream?
        byte[] requestBody = dataFlowRequest.getProperties().containsKey(BODY)
                ? dataFlowRequest.getProperties().get(BODY).getBytes() : null;
        String signatureXml = getSignatureFromHeaders(contextApi.headers());
        String signature = contextApi.headers().get(HEADER_XRD_SIG);
        String xRequestId = contextApi.headers().get(MimeUtils.HEADER_REQUEST_ID);

        if (isSoap) {
            var soapParser = new SoapParserImpl();
            // todo: use stream?
            SoapMessageImpl soapMessage = (SoapMessageImpl) soapParser.parse(MimeTypes.TEXT_XML_UTF8,
                    new ByteArrayInputStream(requestBody));

            SoapLogMessage logMessage = new SoapLogMessage(
                    soapMessage,
                    new SignatureData(signatureXml),
                    false,
                    xRequestId);

            xRoadMessageLog.log(logMessage);
            signService.verifyRequest(signature, soapMessage::getBytes, () -> null, soapMessage.getClient());

            return soapMessage.getHash();
        } else { // rest message
            RestRequest restRequest = new RestRequest(
                    contextApi.method(),
                    "/r%d/%s".formatted(RestMessage.PROTOCOL_VERSION, serviceId.toShortString()),
                    defaultIfBlank(contextApi.queryParams(), null),
                    toHeadersList(contextApi.headers(), HEADER_XRD_SIG, "Authorization"),
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
            signService.verifyRequest(signature, restRequest::getMessageBytes, () -> requestBody, restRequest.getClientId());

            return calculateDigest(restRequest, requestBody);
        }
    }

    private byte[] calculateDigest(RestRequest request, byte[] body) throws Exception {
        if (body != null) {
            var dc = CryptoUtils.createDigestCalculator(CryptoUtils.DEFAULT_DIGEST_ALGORITHM_ID);
            dc.getOutputStream().write(body);
            var bodyDigest = dc.getDigest();

            dc = CryptoUtils.createDigestCalculator(CryptoUtils.DEFAULT_DIGEST_ALGORITHM_ID);
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

    private Response handleSuccess(CachingStream cachingStream, Map<String, String> headers, ServiceId.Conf serviceId,
                                   ContainerRequestContextApi contextApi, boolean isSoap, byte[] requestDigest) {
        try {
            var queryId = contextApi.headers().get(MimeUtils.HEADER_QUERY_ID);
            var xRequestId = contextApi.headers().get(MimeUtils.HEADER_REQUEST_ID);
            // todo: use stream
            byte[] responseContent = cachingStream.getCachedContents().readAllBytes();
            SignatureResponse signatureResponse;

            if (isSoap) {
                SoapParser soapParser = new ResponseSoapParserImpl(requestDigest);
                SoapMessageImpl soapMessage = (SoapMessageImpl) soapParser.parse(MimeTypes.TEXT_XML_UTF8,
                        cachingStream.getCachedContents());

                signatureResponse = this.signService.sign(serviceId, soapMessage::getBytes, () -> null);

                var logMessage = new SoapLogMessage(
                        soapMessage,
                        new SignatureData(signatureResponse.getSignatureDecoded()),
                        false,
                        xRequestId);
                xRoadMessageLog.log(logMessage);

                var builder = Response.ok(soapMessage.getBytes());
                headers.forEach(builder::header);
                builder.header(HEADER_XRD_SIG, signatureResponse.getSignature());
                return builder.build();

            } else { // REST message
                var clientId = RestMessage.decodeClientId(contextApi.headers().get("X-Road-Client"));
                var restResponse = new RestResponse(clientId,
                        queryId,
                        requestDigest,
                        serviceId,
                        HTTP_OK, "OK",   // todo: can't get them here
                        toHeadersList(headers),
                        xRequestId);

                signatureResponse = this.signService.sign(serviceId, restResponse::getMessageBytes, () -> responseContent);

                var logMessage = new RestLogMessage(
                        queryId,
                        restResponse.getClientId(),
                        serviceId,
                        restResponse,
                        new SignatureData(signatureResponse.getSignatureDecoded()),
                        cachingStream.getCachedContents(),
                        false,
                        xRequestId);
                xRoadMessageLog.log(logMessage);

                var builder = Response.ok(responseContent);
                restResponse.getHeaders().forEach(h -> builder.header(h.getName(), h.getValue()));
                builder.header(HEADER_XRD_SIG, signatureResponse.getSignature());
                return builder.build();
            }
        } catch (Exception e) {
            monitor.severe("Failed to sign response payload", e);
            throw new RuntimeException(e);
        }
    }

    private static Response error(Response.Status status, String error) {
        return status(status).type(APPLICATION_JSON).entity(new TransferErrorResponse(List.of(error))).build();
    }

}
