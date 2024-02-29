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
import org.eclipse.edc.connector.dataplane.api.controller.ContainerRequestContextApiImpl;
import org.eclipse.edc.connector.dataplane.api.controller.DataPlanePublicApi;
import org.eclipse.edc.connector.dataplane.spi.pipeline.PipelineService;
import org.eclipse.edc.connector.dataplane.spi.resolver.DataAddressResolver;
import org.eclipse.edc.connector.dataplane.spi.response.TransferErrorResponse;
import org.eclipse.edc.connector.dataplane.util.sink.AsyncStreamingDataSink;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.types.domain.DataAddress;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
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

@Path("{any:.*}")
@Produces(WILDCARD)
public class XrdDataPlanePublicApiController implements DataPlanePublicApi {

    private final PipelineService pipelineService;
    private final DataAddressResolver dataAddressResolver;
    private final XrdDataFlowRequestSupplier requestSupplier;
    private final XrdEdcSignService signService;
    private final Monitor monitor;
    private final ExecutorService executorService;

    public XrdDataPlanePublicApiController(PipelineService pipelineService, DataAddressResolver dataAddressResolver,
                                           XrdEdcSignService xrdEdcSignService, Monitor monitor,
                                           ExecutorService executorService) {
        this.pipelineService = pipelineService;
        this.dataAddressResolver = dataAddressResolver;
        this.signService = xrdEdcSignService;
        this.monitor = monitor;
        this.executorService = executorService;
        this.requestSupplier = new XrdDataFlowRequestSupplier();
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
        var dataFlowRequest = requestSupplier.apply(contextApi, dataAddress);

        AsyncStreamingDataSink.AsyncResponseContext asyncResponseContext = callback -> {
            StreamingOutput output = t -> callback.outputStreamConsumer().accept(t);

            //Instead of streaming directly to consumer, we will stream to a byte array and then sign the response
            var outputStream = new ByteArrayOutputStream();
            try {
                output.write(outputStream);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            Map<String, String> headers = new HashMap<>();
            headers.put("Content-Type", callback.mediaType());
            //TODO edc is not passing response headers??
            var resp = handleSuccess(outputStream.toByteArray(), headers, dataAddress);

            return response.resume(resp);
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

    private Response handleSuccess(byte[] responseBody, Map<String, String> headers, DataAddress dataAddress) {
        try {
            Map<String, String> additionalHeaders = this.signService.signPayload(dataAddress, responseBody, headers);
            var builder = Response.ok(responseBody);
            headers.forEach(builder::header);
            additionalHeaders.forEach(builder::header);

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
