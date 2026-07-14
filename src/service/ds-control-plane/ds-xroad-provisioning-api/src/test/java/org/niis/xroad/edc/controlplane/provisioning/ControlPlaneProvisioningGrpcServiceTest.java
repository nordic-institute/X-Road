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
package org.niis.xroad.edc.controlplane.provisioning;

import io.grpc.StatusRuntimeException;
import io.grpc.stub.StreamObserver;
import org.eclipse.edc.participantcontext.spi.config.model.ParticipantContextConfiguration;
import org.eclipse.edc.participantcontext.spi.config.service.ParticipantContextConfigService;
import org.eclipse.edc.participantcontext.spi.service.ParticipantContextService;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.eclipse.edc.spi.result.ServiceResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.niis.xroad.common.rpc.server.RpcResponseHandler;
import org.niis.xroad.edc.controlplane.provisioning.proto.CreateParticipantContextReq;
import org.niis.xroad.edc.controlplane.provisioning.proto.CreateParticipantContextResp;
import org.niis.xroad.edc.controlplane.provisioning.proto.PutParticipantContextConfigReq;
import org.niis.xroad.edc.controlplane.provisioning.proto.PutParticipantContextConfigResp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ControlPlaneProvisioningGrpcServiceTest {

    @Mock
    private ParticipantContextService participantContextService;
    @Mock
    private ParticipantContextConfigService participantContextConfigService;
    @Mock
    private StreamObserver<CreateParticipantContextResp> createObserver;
    @Mock
    private StreamObserver<PutParticipantContextConfigResp> configObserver;

    private ControlPlaneProvisioningGrpcService service;

    @BeforeEach
    void setUp() {
        service = new ControlPlaneProvisioningGrpcService(
                participantContextService, participantContextConfigService, new RpcResponseHandler());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void createParticipantContextRejectsBlankParticipantContextId(String blank) {
        var request = CreateParticipantContextReq.newBuilder()
                .setParticipantContextId(blank)
                .setDid("did:web:example.com")
                .build();

        service.createParticipantContext(request, createObserver);

        verify(createObserver).onError(any(StatusRuntimeException.class));
        verify(participantContextService, never()).createParticipantContext(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void createParticipantContextRejectsBlankDid(String blank) {
        var request = CreateParticipantContextReq.newBuilder()
                .setParticipantContextId("ctx-1")
                .setDid(blank)
                .build();

        service.createParticipantContext(request, createObserver);

        verify(createObserver).onError(any(StatusRuntimeException.class));
        verify(participantContextService, never()).createParticipantContext(any());
    }

    @Test
    void createParticipantContextSucceeds() {
        when(participantContextService.createParticipantContext(any()))
                .thenReturn(ServiceResult.success(ParticipantContext.Builder.newInstance()
                        .participantContextId("ctx-1")
                        .identity("did:web:example.com")
                        .build()));

        var request = CreateParticipantContextReq.newBuilder()
                .setParticipantContextId("ctx-1")
                .setDid("did:web:example.com")
                .build();

        service.createParticipantContext(request, createObserver);

        verify(createObserver).onNext(any());
        verify(createObserver).onCompleted();
        verify(createObserver, never()).onError(any());
    }

    @Test
    void createParticipantContextToleratesConflict() {
        when(participantContextService.createParticipantContext(any()))
                .thenReturn(ServiceResult.conflict("already exists"));

        var request = CreateParticipantContextReq.newBuilder()
                .setParticipantContextId("ctx-1")
                .setDid("did:web:example.com")
                .build();

        service.createParticipantContext(request, createObserver);

        verify(createObserver).onNext(any());
        verify(createObserver).onCompleted();
        verify(createObserver, never()).onError(any());
    }

    @Test
    void putParticipantContextConfigMapsAllConfigEntries() {
        var captor = ArgumentCaptor.forClass(ParticipantContextConfiguration.class);
        when(participantContextConfigService.save(captor.capture()))
                .thenReturn(ServiceResult.success());

        var request = PutParticipantContextConfigReq.newBuilder()
                .setParticipantContextId("ctx-1")
                .setDid("did:web:example.com")
                .setStsTokenUrl("https://sts.example.com/token")
                .build();

        service.putParticipantContextConfig(request, configObserver);

        verify(configObserver).onNext(any());
        verify(configObserver).onCompleted();
        verify(configObserver, never()).onError(any());

        var saved = captor.getValue();
        assertThat(saved.getEntries()).containsEntry("edc.participant.id", "did:web:example.com");
        assertThat(saved.getEntries()).containsEntry("edc.participant.did", "did:web:example.com");
        assertThat(saved.getEntries()).containsEntry("edc.iam.sts.oauth.token.url", "https://sts.example.com/token");
        assertThat(saved.getEntries()).containsEntry("edc.iam.sts.oauth.client.id", "did:web:example.com");
        assertThat(saved.getEntries()).containsEntry("edc.iam.sts.oauth.client.secret.alias", "ctx-1-sts-client-secret");
    }

    @Test
    void putParticipantContextConfigToleratesConflict() {
        when(participantContextConfigService.save(any()))
                .thenReturn(ServiceResult.conflict("already saved"));

        var request = PutParticipantContextConfigReq.newBuilder()
                .setParticipantContextId("ctx-1")
                .setDid("did:web:example.com")
                .setStsTokenUrl("https://sts.example.com/token")
                .build();

        service.putParticipantContextConfig(request, configObserver);

        verify(configObserver).onNext(any());
        verify(configObserver).onCompleted();
        verify(configObserver, never()).onError(any());
    }

    @Test
    void putParticipantContextConfigPropagatesUnexpectedFailure() {
        when(participantContextConfigService.save(any()))
                .thenReturn(ServiceResult.unexpected("db down"));

        var request = PutParticipantContextConfigReq.newBuilder()
                .setParticipantContextId("ctx-1")
                .setDid("did:web:example.com")
                .setStsTokenUrl("https://sts.example.com/token")
                .build();

        service.putParticipantContextConfig(request, configObserver);

        verify(configObserver).onError(any(StatusRuntimeException.class));
        verify(configObserver, never()).onCompleted();
    }
}
