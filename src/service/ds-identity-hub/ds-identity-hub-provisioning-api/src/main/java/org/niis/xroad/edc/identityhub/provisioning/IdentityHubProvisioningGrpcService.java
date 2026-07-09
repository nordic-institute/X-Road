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
package org.niis.xroad.edc.identityhub.provisioning;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.eclipse.edc.iam.did.spi.document.Service;
import org.eclipse.edc.identityhub.spi.credential.request.model.RequestedCredential;
import org.eclipse.edc.identityhub.spi.participantcontext.IdentityHubParticipantContextService;
import org.eclipse.edc.identityhub.spi.participantcontext.model.KeyDescriptor;
import org.eclipse.edc.identityhub.spi.participantcontext.model.ParticipantManifest;
import org.eclipse.edc.identityhub.spi.verifiablecredentials.CredentialRequestManager;
import org.eclipse.edc.spi.result.ServiceFailure;
import org.eclipse.edc.spi.result.ServiceResult;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.ErrorOrigin;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.rpc.server.RpcResponseHandler;
import org.niis.xroad.edc.identityhub.provisioning.proto.CreateParticipantContextReq;
import org.niis.xroad.edc.identityhub.provisioning.proto.CreateParticipantContextResp;
import org.niis.xroad.edc.identityhub.provisioning.proto.GetCredentialRequestStateReq;
import org.niis.xroad.edc.identityhub.provisioning.proto.GetCredentialRequestStateResp;
import org.niis.xroad.edc.identityhub.provisioning.proto.GetParticipantContextExistsReq;
import org.niis.xroad.edc.identityhub.provisioning.proto.GetParticipantContextExistsResp;
import org.niis.xroad.edc.identityhub.provisioning.proto.IdentityHubProvisioningServiceGrpc;
import org.niis.xroad.edc.identityhub.provisioning.proto.RequestCredentialReq;
import org.niis.xroad.edc.identityhub.provisioning.proto.RequestCredentialResp;

import java.util.List;
import java.util.Map;

import static org.niis.xroad.common.core.exception.ErrorCode.DSP_PARTICIPANT_CONTEXT_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_PROVISIONING_FAILED;

/**
 * gRPC service that provisions IdentityHub participant contexts and holder credential requests by
 * delegating to the EDC IdentityHub services directly (no REST management API).
 */
@RequiredArgsConstructor
class IdentityHubProvisioningGrpcService extends IdentityHubProvisioningServiceGrpc.IdentityHubProvisioningServiceImplBase {

    private static final String XROAD_MEMBER_ID_PROPERTY = "xroadMemberId";
    private static final String CREDENTIAL_SERVICE_TYPE = "CredentialService";
    private static final String CREDENTIAL_SERVICE_ID_SUFFIX = "-credential-service";
    private static final String KEY_ALGORITHM_PARAM = "algorithm";
    private static final String KEY_ALGORITHM = "EdDSA";

    private final IdentityHubParticipantContextService participantContextService;
    private final CredentialRequestManager credentialRequestManager;
    private final RpcResponseHandler responseHandler;

    @Override
    public void createParticipantContext(CreateParticipantContextReq request,
                                         StreamObserver<CreateParticipantContextResp> responseObserver) {
        responseHandler.handleRequest(responseObserver, () -> createParticipantContextInternal(request));
    }

    @Override
    public void requestCredential(RequestCredentialReq request, StreamObserver<RequestCredentialResp> responseObserver) {
        responseHandler.handleRequest(responseObserver, () -> requestCredentialInternal(request));
    }

    @Override
    public void getCredentialRequestState(GetCredentialRequestStateReq request,
                                          StreamObserver<GetCredentialRequestStateResp> responseObserver) {
        responseHandler.handleRequest(responseObserver, () -> getCredentialRequestStateInternal(request));
    }

    @Override
    public void getParticipantContextExists(GetParticipantContextExistsReq request,
                                            StreamObserver<GetParticipantContextExistsResp> responseObserver) {
        responseHandler.handleRequest(responseObserver, () -> getParticipantContextExistsInternal(request));
    }

    private CreateParticipantContextResp createParticipantContextInternal(CreateParticipantContextReq request) {
        validateManifestFields(request.getParticipantContextId(), request.getDid());
        var manifest = ParticipantManifest.Builder.newInstance()
                .participantContextId(request.getParticipantContextId())
                .did(request.getDid())
                .active(true)
                .property(XROAD_MEMBER_ID_PROPERTY, request.getMemberId())
                .serviceEndpoint(new Service(
                        request.getParticipantContextId() + CREDENTIAL_SERVICE_ID_SUFFIX,
                        CREDENTIAL_SERVICE_TYPE,
                        request.getCredentialServiceUrl()))
                .key(KeyDescriptor.Builder.newInstance()
                        .keyId(request.getKeyId())
                        .privateKeyAlias(request.getPrivateKeyAlias())
                        .keyGeneratorParams(Map.of(KEY_ALGORITHM_PARAM, KEY_ALGORITHM))
                        .build())
                .build();

        var result = participantContextService.createParticipantContext(manifest);
        requireSuccessOrConflict(result, DSP_PARTICIPANT_CONTEXT_FAILED, request.getParticipantContextId());
        return CreateParticipantContextResp.getDefaultInstance();
    }

    private RequestCredentialResp requestCredentialInternal(RequestCredentialReq request) {
        var requested = List.of(new RequestedCredential(
                request.getCredentialDefinitionId(), request.getCredentialType(), request.getFormat()));

        var result = credentialRequestManager.initiateRequest(
                request.getParticipantContextId(), request.getIssuerDid(), request.getHolderPid(), requested);

        if (result.failed() && result.reason() != ServiceFailure.Reason.CONFLICT) {
            throw failure(DSP_PROVISIONING_FAILED, request.getHolderPid(), result.getFailureDetail());
        }

        var builder = RequestCredentialResp.newBuilder();
        if (result.succeeded() && result.getContent() != null) {
            builder.setRequestId(result.getContent());
        }
        return builder.build();
    }

    private GetCredentialRequestStateResp getCredentialRequestStateInternal(GetCredentialRequestStateReq request) {
        var holderRequest = credentialRequestManager.findById(request.getHolderPid());
        if (holderRequest == null) {
            return GetCredentialRequestStateResp.newBuilder().setFound(false).build();
        }
        return GetCredentialRequestStateResp.newBuilder()
                .setFound(true)
                .setStatus(holderRequest.stateAsString())
                .build();
    }

    private GetParticipantContextExistsResp getParticipantContextExistsInternal(GetParticipantContextExistsReq request) {
        var result = participantContextService.getParticipantContext(request.getParticipantContextId());
        return GetParticipantContextExistsResp.newBuilder()
                .setExists(result.succeeded())
                .build();
    }

    private void requireSuccessOrConflict(ServiceResult<?> result, ErrorCode errorCode, String metadata) {
        if (result.succeeded() || result.reason() == ServiceFailure.Reason.CONFLICT) {
            return;
        }
        throw failure(errorCode, metadata, result.getFailureDetail());
    }

    private XrdRuntimeException failure(ErrorCode errorCode, String metadata, String detail) {
        return XrdRuntimeException.systemException(errorCode)
                .origin(ErrorOrigin.DATASPACE)
                .metadataItems(metadata)
                .details(detail)
                .build();
    }

    private void validateManifestFields(String participantContextId, String did) {
        if (participantContextId == null || participantContextId.isBlank()) {
            throw XrdRuntimeException.systemException(DSP_PROVISIONING_FAILED, "participantContextId must not be blank");
        }
        if (did == null || did.isBlank()) {
            throw XrdRuntimeException.systemException(DSP_PROVISIONING_FAILED, "did must not be blank");
        }
    }
}
