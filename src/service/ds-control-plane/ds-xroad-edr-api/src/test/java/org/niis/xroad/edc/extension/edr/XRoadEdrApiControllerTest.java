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

import jakarta.json.Json;
import jakarta.json.JsonObject;
import jakarta.ws.rs.container.AsyncResponse;
import jakarta.ws.rs.core.SecurityContext;
import org.eclipse.edc.api.auth.spi.AuthorizationService;
import org.eclipse.edc.participantcontext.spi.service.ParticipantContextService;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.spi.EdcException;
import org.eclipse.edc.spi.result.ServiceResult;
import org.eclipse.edc.spi.types.domain.DataAddress;
import org.eclipse.edc.web.spi.exception.BadGatewayException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.edc.extension.edr.service.EdrAcquisitionService;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class XRoadEdrApiControllerTest {

    @Mock
    EdrAcquisitionService edrAcquisitionService;
    @Mock
    AuthorizationService authorizationService;
    @Mock
    ParticipantContextService participantContextService;
    @Mock
    AsyncResponse response;
    @Mock
    SecurityContext securityContext;

    XRoadEdrApiController controller;

    @BeforeEach
    void setUp() {
        controller = new XRoadEdrApiController(edrAcquisitionService, authorizationService, participantContextService);
    }

    @Test
    void acquireEdrSuccessResumesWithDataAddressProperties() {
        stubPreAuthorize();
        var dataAddress = DataAddress.Builder.newInstance()
                .type("HttpData")
                .property("endpoint", "http://provider/api")
                .build();
        when(edrAcquisitionService.acquireEdr(any(ParticipantContext.class), any(EdrRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(ServiceResult.success(dataAddress)));

        controller.acquireEdr("participant1", buildValidBody(), response, securityContext);

        var captor = ArgumentCaptor.forClass(Object.class);
        verify(response).resume(captor.capture());
        assertThat(captor.getValue()).isEqualTo(dataAddress.getProperties());
    }

    @Test
    void acquireEdrEdcExceptionResumesWithBadGateway() {
        stubPreAuthorize();
        when(edrAcquisitionService.acquireEdr(any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new EdcException("upstream failure")));

        controller.acquireEdr("participant1", buildValidBody(), response, securityContext);

        var captor = ArgumentCaptor.forClass(Throwable.class);
        verify(response).resume(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(BadGatewayException.class);
    }

    @Test
    void acquireEdrGenericExceptionResumesWithSameException() {
        stubPreAuthorize();
        var runtimeException = new RuntimeException("generic failure");
        when(edrAcquisitionService.acquireEdr(any(), any()))
                .thenReturn(CompletableFuture.failedFuture(runtimeException));

        controller.acquireEdr("participant1", buildValidBody(), response, securityContext);

        var captor = ArgumentCaptor.forClass(Throwable.class);
        verify(response).resume(captor.capture());
        assertThat(captor.getValue()).isSameAs(runtimeException);
    }

    @Test
    void acquireEdrCompletionExceptionWrappingEdcExceptionResumesWithBadGateway() {
        stubPreAuthorize();
        when(edrAcquisitionService.acquireEdr(any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new CompletionException(new EdcException("edc wrapped"))));

        controller.acquireEdr("participant1", buildValidBody(), response, securityContext);

        var captor = ArgumentCaptor.forClass(Throwable.class);
        verify(response).resume(captor.capture());
        assertThat(captor.getValue()).isInstanceOf(BadGatewayException.class);
    }

    @Test
    void acquireEdrMissingAssetIdThrowsIllegalArgument() {
        stubPreAuthorize();
        var body = Json.createObjectBuilder()
                .add("counterPartyId", "provider-1")
                .add("counterPartyAddress", "http://provider/dsp")
                .build();

        assertThatThrownBy(() -> controller.acquireEdr("participant1", body, response, securityContext))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(edrAcquisitionService);
    }

    @Test
    void acquireEdrMissingCounterPartyIdThrowsIllegalArgument() {
        stubPreAuthorize();
        var body = Json.createObjectBuilder()
                .add("assetId", "asset-1")
                .add("counterPartyAddress", "http://provider/dsp")
                .build();

        assertThatThrownBy(() -> controller.acquireEdr("participant1", body, response, securityContext))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(edrAcquisitionService);
    }

    @Test
    void acquireEdrMissingCounterPartyAddressThrowsIllegalArgument() {
        stubPreAuthorize();
        var body = Json.createObjectBuilder()
                .add("assetId", "asset-1")
                .add("counterPartyId", "provider-1")
                .build();

        assertThatThrownBy(() -> controller.acquireEdr("participant1", body, response, securityContext))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(edrAcquisitionService);
    }

    @Test
    void acquireEdrAuthorizationContextForwardedPassesCorrectArgs() {
        var participantContext = stubPreAuthorize();
        when(edrAcquisitionService.acquireEdr(any(ParticipantContext.class), any(EdrRequest.class)))
                .thenReturn(CompletableFuture.completedFuture(
                        ServiceResult.success(DataAddress.Builder.newInstance().type("HttpData").build())));

        controller.acquireEdr("participant1", buildValidBody(), response, securityContext);

        var contextCaptor = ArgumentCaptor.forClass(ParticipantContext.class);
        var requestCaptor = ArgumentCaptor.forClass(EdrRequest.class);
        verify(edrAcquisitionService).acquireEdr(contextCaptor.capture(), requestCaptor.capture());
        assertThat(contextCaptor.getValue()).isSameAs(participantContext);
        assertThat(requestCaptor.getValue().assetId()).isEqualTo("asset-1");
        assertThat(requestCaptor.getValue().counterPartyId()).isEqualTo("provider-1");
        assertThat(requestCaptor.getValue().counterPartyAddress()).isEqualTo("http://provider/dsp");
    }

    private ParticipantContext stubPreAuthorize() {
        var participantContext = ParticipantContext.Builder.newInstance()
                .participantContextId("participant1")
                .identity("participant1")
                .build();
        when(authorizationService.authorize(securityContext, "participant1", "participant1", ParticipantContext.class))
                .thenReturn(ServiceResult.success());
        when(participantContextService.getParticipantContext("participant1"))
                .thenReturn(ServiceResult.success(participantContext));
        return participantContext;
    }

    private JsonObject buildValidBody() {
        return Json.createObjectBuilder()
                .add("assetId", "asset-1")
                .add("counterPartyId", "provider-1")
                .add("counterPartyAddress", "http://provider/dsp")
                .build();
    }
}
