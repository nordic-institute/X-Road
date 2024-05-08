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
import ee.ria.xroad.common.message.RestRequest;
import ee.ria.xroad.common.message.RestResponse;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.message.SoapParserImpl;
import ee.ria.xroad.common.messagelog.RestLogMessage;
import ee.ria.xroad.common.messagelog.SoapLogMessage;
import ee.ria.xroad.common.signature.SignatureData;
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
import org.niis.xroad.edc.spi.messagelog.XRoadMessageLog;

import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static jakarta.ws.rs.core.MediaType.WILDCARD;
import static jakarta.ws.rs.core.Response.Status.BAD_REQUEST;
import static jakarta.ws.rs.core.Response.Status.FORBIDDEN;
import static jakarta.ws.rs.core.Response.Status.INTERNAL_SERVER_ERROR;
import static jakarta.ws.rs.core.Response.status;
import static java.net.HttpURLConnection.HTTP_OK;
import static java.util.stream.Collectors.toList;
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
        return context.getHeaderString("Content-Type").contains("text/xml");
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

        logRequest(dataFlowRequest, contextApi, serviceId, isSoap);

        AsyncStreamingDataSink.AsyncResponseContext asyncResponseContext = callback -> {
            StreamingOutput output = t -> callback.outputStreamConsumer().accept(t);

            byte[] responseBytes;
            try {
                var digestCalculator = CryptoUtils.createDigestCalculator(CryptoUtils.DEFAULT_CERT_HASH_ALGORITHM_ID);
                CachingStream cachingStream = new CachingStream();
                TeeOutputStream tos = new TeeOutputStream(digestCalculator.getOutputStream(), cachingStream);
                output.write(tos);

//                var digest = digestCalculator.getDigest();
//                responseBytes = IOUtils.toByteArray(cachingStream.getCachedContents());

                Map<String, String> headers = new HashMap<>();
                headers.put("Content-Type", callback.mediaType());
                //TODO edc is not passing response headers??
                var resp = handleSuccess(cachingStream, headers, serviceId, contextApi, isSoap);

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

    @SneakyThrows
    private void logRequest(DataFlowStartMessage dataFlowRequest, ContainerRequestContextApi contextApi,
                            ServiceId.Conf serviceId, boolean isSoap) {

        if (isSoap) {
            var soapParser = new SoapParserImpl();
            // todo: use stream?
            SoapMessageImpl soapMessage = (SoapMessageImpl) soapParser.parse(MimeTypes.TEXT_XML_UTF8,
                    new ByteArrayInputStream(dataFlowRequest.getProperties().get(BODY).getBytes()));

            SoapLogMessage logMessage = new SoapLogMessage(
                    soapMessage,
                    new SignatureData(getSignatureFromHeaders(contextApi.headers())),
                    false,
                    contextApi.headers().get(MimeUtils.HEADER_REQUEST_ID));
            xRoadMessageLog.log(logMessage);
        } else { // rest message
            RestRequest restRequest = new RestRequest(
                    contextApi.method(),
                    contextApi.path(),
                    contextApi.queryParams(),
                    contextApi.headers(),
                    contextApi.headers().get(MimeUtils.HEADER_REQUEST_ID)
            );

            String body = dataFlowRequest.getProperties().get(BODY);
            CachingStream cs = new CachingStream();
            cs.write(body.getBytes());

            RestLogMessage logMessage = new RestLogMessage(contextApi.headers().get(MimeUtils.HEADER_QUERY_ID),
                    serviceId.getClientId(),
                    serviceId,
                    restRequest, // rest message
                    new SignatureData(getSignatureFromHeaders(contextApi.headers())),
                    cs.getCachedContents(), //body
                    false,
                    contextApi.headers().get(MimeUtils.HEADER_REQUEST_ID)
            );
            xRoadMessageLog.log(logMessage);
        }
    }

    private String getSignatureFromHeaders(Map<String, String> headers) {
        return new String(Base64.getDecoder().decode(headers.get(HEADER_XRD_SIG)));
    }

    private Response handleSuccess(CachingStream cachingStream, Map<String, String> headers, ServiceId.Conf serviceId,
                                   ContainerRequestContextApi contextApi, boolean isSoap) {
        try {
            Map<String, String> additionalHeaders = this.signService.signPayload(serviceId,
                    cachingStream.getCachedContents().readAllBytes(), headers);
            // todo: use stream
            var builder = Response.ok(cachingStream.getCachedContents().readAllBytes());
            headers.forEach(builder::header);
            additionalHeaders.forEach(builder::header);

            if (isSoap) {
                SoapParserImpl soapParser = new SoapParserImpl();
                SoapMessageImpl soapMessage = (SoapMessageImpl) soapParser.parse(MimeTypes.TEXT_XML_UTF8,
                        cachingStream.getCachedContents());

                var logMessage = new SoapLogMessage(
                        soapMessage,
                        new SignatureData(getSignatureFromHeaders(additionalHeaders)),
                        false,
                        contextApi.headers().get(MimeUtils.HEADER_REQUEST_ID));
                xRoadMessageLog.log(logMessage);
            } else { // REST message
                var restResponse = new RestResponse(serviceId.getClientId(),
                        contextApi.headers().get(MimeUtils.HEADER_QUERY_ID),
                        // requestHash,
                        serviceId,
                        HTTP_OK, "OK",   // todo: can't get them here
                        headers.entrySet().stream()
                                .map(e -> new BasicHeader(e.getKey(), e.getValue()))
                                .collect(toList()),
                        contextApi.headers().get(MimeUtils.HEADER_REQUEST_ID));

                var logMessage = new RestLogMessage(
                        contextApi.headers().get(MimeUtils.HEADER_QUERY_ID),
                        serviceId.getClientId(),
                        serviceId,
                        restResponse,
                        new SignatureData(getSignatureFromHeaders(additionalHeaders)),
                        cachingStream.getCachedContents(),
                        false,
                        contextApi.headers().get(MimeUtils.HEADER_REQUEST_ID)
                );
                xRoadMessageLog.log(logMessage);
            }

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
