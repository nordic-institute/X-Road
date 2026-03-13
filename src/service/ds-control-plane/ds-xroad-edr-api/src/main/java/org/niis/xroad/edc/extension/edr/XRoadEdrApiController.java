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

package org.niis.xroad.edc.extension.edr;

import jakarta.annotation.security.RolesAllowed;
import jakarta.json.JsonObject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.container.Suspended;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.SecurityContext;
import lombok.RequiredArgsConstructor;
import org.eclipse.edc.api.auth.spi.AuthorizationService;
import org.eclipse.edc.api.auth.spi.ParticipantPrincipal;
import org.eclipse.edc.api.auth.spi.RequiredScope;
import org.eclipse.edc.participantcontext.spi.service.ParticipantContextService;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.web.spi.exception.BadGatewayException;
import org.niis.xroad.edc.extension.edr.service.EdrAcquisitionService;

import java.util.concurrent.CompletionException;

import static jakarta.ws.rs.core.MediaType.APPLICATION_JSON;
import static org.eclipse.edc.web.spi.exception.ServiceResultHandler.exceptionMapper;

@Consumes(APPLICATION_JSON)
@Produces(APPLICATION_JSON)
@Path("/v5alpha/participants/{participantContextId}/edr")
@RequiredArgsConstructor
public class XRoadEdrApiController {

    private final EdrAcquisitionService edrAcquisitionService;
    private final AuthorizationService authorizationService;
    private final ParticipantContextService participantContextService;

    @POST
    @RolesAllowed({ParticipantPrincipal.ROLE_ADMIN, ParticipantPrincipal.ROLE_PARTICIPANT})
    @RequiredScope("management-api:write")
    public void acquireEdr(@PathParam("participantContextId") String participantContextId,
                           JsonObject requestBody,
                           @Suspended AsyncResponse response,
                           @Context SecurityContext securityContext) {

        var participantContext = preAuthorize(participantContextId, securityContext);

        var edrRequest = parseRequest(requestBody);

        edrAcquisitionService.acquireEdr(participantContext, edrRequest)
                .whenComplete((result, throwable) -> {
                    try {
                        response.resume(toResponse(result, throwable));
                    } catch (Throwable mapped) {
                        response.resume(mapped);
                    }
                });
    }

    private ParticipantContext preAuthorize(String participantContextId, SecurityContext securityContext) {
        authorizationService.authorize(securityContext, participantContextId, participantContextId, ParticipantContext.class)
                .orElseThrow(exceptionMapper(ParticipantContext.class, participantContextId));

        return participantContextService.getParticipantContext(participantContextId)
                .orElseThrow(exceptionMapper(ParticipantContext.class, participantContextId));
    }

    private Object toResponse(ServiceResult<DataAddress> result, Throwable throwable) throws Throwable {
        if (throwable == null) {
            if (result.succeeded()) {
                return result.getContent().getProperties();
            } else {
                throw new BadGatewayException(result.getFailureDetail());
            }
        } else {
            var cause = throwable instanceof CompletionException ? throwable.getCause() : throwable;
            if (cause instanceof EdcException) {
                throw new BadGatewayException(cause.getMessage());
            } else {
                throw cause;
            }
        }
    }

    private EdrRequest parseRequest(JsonObject requestBody) {
        var assetId = requestBody.getString("assetId", null);
        var counterPartyId = requestBody.getString("counterPartyId", null);
        var counterPartyAddress = requestBody.getString("counterPartyAddress", null);
        var protocol = requestBody.containsKey("protocol") ? requestBody.getString("protocol") : null;

        if (assetId == null || counterPartyId == null || counterPartyAddress == null) {
            throw new IllegalArgumentException("assetId, counterPartyId, and counterPartyAddress are required");
        }

        return new EdrRequest(assetId, counterPartyId, counterPartyAddress, protocol);
    }

}
