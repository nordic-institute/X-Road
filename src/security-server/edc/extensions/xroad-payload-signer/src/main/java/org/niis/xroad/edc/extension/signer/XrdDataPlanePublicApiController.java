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
import jakarta.ws.rs.core.Response;
import org.eclipse.edc.connector.dataplane.api.controller.ContainerRequestContextApiImpl;
import org.eclipse.edc.connector.dataplane.api.controller.DataPlanePublicApi;
import org.eclipse.edc.connector.dataplane.spi.pipeline.PipelineService;
import org.eclipse.edc.connector.dataplane.spi.resolver.DataAddressResolver;
import org.eclipse.edc.connector.dataplane.spi.response.TransferErrorResponse;
import org.eclipse.edc.spi.monitor.Monitor;
import org.eclipse.edc.spi.result.Result;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.spi.types.domain.transfer.DataFlowRequest;
import org.eclipse.edc.web.spi.exception.NotAuthorizedException;

import java.util.List;
import java.util.Map;

@Path("{any:.*}")
@Produces({"application/json"})
public class XrdDataPlanePublicApiController implements DataPlanePublicApi {

    private final PipelineService pipelineService;
    private final DataAddressResolver dataAddressResolver;
    private final XrdDataFlowRequestSupplier requestSupplier;
    private final ResponseSigner responseSigner;
    private final Monitor monitor;

    public XrdDataPlanePublicApiController(PipelineService pipelineService, DataAddressResolver dataAddressResolver,
                                           ResponseSigner responseSigner, Monitor monitor) {
        this.pipelineService = pipelineService;
        this.dataAddressResolver = dataAddressResolver;
        this.responseSigner = responseSigner;
        this.monitor = monitor;
        this.requestSupplier = new XrdDataFlowRequestSupplier();
    }

    @GET
    public void get(@Context ContainerRequestContext requestContext, @Suspended AsyncResponse response) {
        this.handle(requestContext, response);
    }

    @DELETE
    public void delete(@Context ContainerRequestContext requestContext, @Suspended AsyncResponse response) {
        this.handle(requestContext, response);
    }

    @PATCH
    public void patch(@Context ContainerRequestContext requestContext, @Suspended AsyncResponse response) {
        this.handle(requestContext, response);
    }

    @PUT
    public void put(@Context ContainerRequestContext requestContext, @Suspended AsyncResponse response) {
        this.handle(requestContext, response);
    }

    @POST
    public void post(@Context ContainerRequestContext requestContext, @Suspended AsyncResponse response) {
        this.handle(requestContext, response);
    }

    private void handle(ContainerRequestContext context, AsyncResponse response) {
        monitor.debug("Received request for data plane public api. Ctx: " + context);
        ContainerRequestContextApiImpl contextApi = new ContainerRequestContextApiImpl(context);
        String token = (String) contextApi.headers().get("Authorization");
        if (token == null) {
            response.resume(this.badRequest("Missing bearer token"));
        } else {
            DataAddress dataAddress = this.extractSourceDataAddress(token);
            DataFlowRequest dataFlowRequest = this.requestSupplier.apply(contextApi, dataAddress);
            Result<Boolean> validationResult = this.pipelineService.validate(dataFlowRequest);
            if (validationResult.failed()) {
                String errorMsg = validationResult.getFailureMessages().isEmpty()
                        ? String.format("Failed to validate request with id: %s",
                        dataFlowRequest.getId()) : String.join(",", validationResult.getFailureMessages());
                response.resume(this.badRequest(errorMsg));
            } else {
                this.pipelineService.transfer(dataFlowRequest).whenComplete((result, throwable) -> {
                    if (throwable == null) {
                        if (result.succeeded()) {

                            //TODO xroad8 assume this is outputstream .
                            if (result.getContent() instanceof String responseStr) {
                                Map<String, String> additionalHeaders;
                                try {
                                    additionalHeaders = this.responseSigner.signPayload(dataAddress, responseStr);
                                    var builder = Response.ok(responseStr);
                                    additionalHeaders.forEach(builder::header);

                                    response.resume(builder.build());
                                } catch (Exception e) {
                                    monitor.severe("Failed to sign response payload", e);
                                    response.resume(e);
                                }
                            } else {
                                response.resume(Response.ok(result.getContent()).build());
                            }
                        } else {
                            response.resume(this.internalServerError(result.getFailureMessages()));
                        }
                    } else {
                        response.resume(this.internalServerError("Unhandled exception occurred during data transfer: "
                                + throwable.getMessage()));
                    }

                });
            }
        }
    }

    private DataAddress extractSourceDataAddress(String token) {
        Result<DataAddress> result = this.dataAddressResolver.resolve(token);
        if (result.failed()) {
            throw new NotAuthorizedException(String.join(", ", result.getFailureMessages()));
        } else {
            return (DataAddress) result.getContent();
        }
    }

    private Response badRequest(String error) {
        return this.badRequest(List.of(error));
    }

    private Response badRequest(List<String> errors) {
        return Response.status(Response.Status.BAD_REQUEST).entity(new TransferErrorResponse(errors)).build();
    }

    private Response internalServerError(String error) {
        return this.internalServerError(List.of(error));
    }

    private Response internalServerError(List<String> errors) {
        return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new TransferErrorResponse(errors)).build();
    }
}
