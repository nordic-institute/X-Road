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

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.eclipse.edc.participantcontext.spi.config.model.ParticipantContextConfiguration;
import org.eclipse.edc.participantcontext.spi.config.service.ParticipantContextConfigService;
import org.eclipse.edc.participantcontext.spi.service.ParticipantContextService;
import org.eclipse.edc.participantcontext.spi.types.ParticipantContext;
import org.niis.xroad.common.rpc.server.RpcResponseHandler;
import org.niis.xroad.edc.controlplane.provisioning.proto.ControlPlaneProvisioningServiceGrpc;
import org.niis.xroad.edc.controlplane.provisioning.proto.CreateParticipantContextReq;
import org.niis.xroad.edc.controlplane.provisioning.proto.CreateParticipantContextResp;
import org.niis.xroad.edc.controlplane.provisioning.proto.PutParticipantContextConfigReq;
import org.niis.xroad.edc.controlplane.provisioning.proto.PutParticipantContextConfigResp;
import org.niis.xroad.edc.extension.catalog.CatalogCacheInvalidator;
import org.niis.xroad.edc.extension.catalog.DataPlaneContextRegistrar;

import static org.niis.xroad.common.core.exception.ErrorCode.DSP_PARTICIPANT_CONTEXT_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_PROVISIONING_FAILED;
import static org.niis.xroad.edc.extension.rpc.EdcProvisioningHelper.requireSuccessOrConflict;
import static org.niis.xroad.edc.extension.rpc.EdcProvisioningHelper.validateManifestFields;

/**
 * gRPC service that provisions Control Plane participant contexts and their STS-bound config by
 * delegating to the EDC connector services directly (no REST management API).
 */
@RequiredArgsConstructor
class ControlPlaneProvisioningGrpcService extends ControlPlaneProvisioningServiceGrpc.ControlPlaneProvisioningServiceImplBase {

    private static final String PARTICIPANT_ID_ENTRY = "edc.participant.id";
    private static final String PARTICIPANT_DID_ENTRY = "edc.participant.did";
    private static final String STS_TOKEN_URL_ENTRY = "edc.iam.sts.oauth.token.url";
    private static final String STS_CLIENT_ID_ENTRY = "edc.iam.sts.oauth.client.id";
    private static final String STS_CLIENT_SECRET_ALIAS_ENTRY = "edc.iam.sts.oauth.client.secret.alias";
    private static final String STS_CLIENT_SECRET_ALIAS_SUFFIX = "-sts-client-secret";

    private final ParticipantContextService participantContextService;
    private final ParticipantContextConfigService participantContextConfigService;
    private final DataPlaneContextRegistrar dataPlaneContextRegistrar;
    private final CatalogCacheInvalidator catalogCacheInvalidator;
    private final RpcResponseHandler responseHandler;

    @Override
    public void createParticipantContext(CreateParticipantContextReq request,
                                         StreamObserver<CreateParticipantContextResp> responseObserver) {
        responseHandler.handleRequest(responseObserver, () -> createParticipantContextInternal(request));
    }

    @Override
    public void putParticipantContextConfig(PutParticipantContextConfigReq request,
                                            StreamObserver<PutParticipantContextConfigResp> responseObserver) {
        responseHandler.handleRequest(responseObserver, () -> putParticipantContextConfigInternal(request));
    }

    private CreateParticipantContextResp createParticipantContextInternal(CreateParticipantContextReq request) {
        validateManifestFields(request.getParticipantContextId(), request.getDid());
        var participantContext = ParticipantContext.Builder.newInstance()
                .participantContextId(request.getParticipantContextId())
                .identity(request.getDid())
                .build();

        var result = participantContextService.createParticipantContext(participantContext);
        requireSuccessOrConflict(result, DSP_PARTICIPANT_CONTEXT_FAILED, request.getParticipantContextId());
        dataPlaneContextRegistrar.registerParticipantContext(request.getParticipantContextId());
        if (result.succeeded()) {
            catalogCacheInvalidator.invalidateStoreCaches();
        }
        return CreateParticipantContextResp.getDefaultInstance();
    }

    private PutParticipantContextConfigResp putParticipantContextConfigInternal(PutParticipantContextConfigReq request) {
        var configuration = ParticipantContextConfiguration.Builder.newInstance()
                .participantContextId(request.getParticipantContextId())
                .entry(PARTICIPANT_ID_ENTRY, request.getDid())
                .entry(PARTICIPANT_DID_ENTRY, request.getDid())
                .entry(STS_TOKEN_URL_ENTRY, request.getStsTokenUrl())
                .entry(STS_CLIENT_ID_ENTRY, request.getDid())
                .entry(STS_CLIENT_SECRET_ALIAS_ENTRY, request.getParticipantContextId() + STS_CLIENT_SECRET_ALIAS_SUFFIX)
                .build();

        var result = participantContextConfigService.save(configuration);
        requireSuccessOrConflict(result, DSP_PROVISIONING_FAILED, request.getParticipantContextId());
        return PutParticipantContextConfigResp.getDefaultInstance();
    }
}
