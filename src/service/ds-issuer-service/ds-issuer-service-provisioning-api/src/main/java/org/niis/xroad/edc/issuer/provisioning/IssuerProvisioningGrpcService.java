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
package org.niis.xroad.edc.issuer.provisioning;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.eclipse.edc.iam.did.spi.document.Service;
import org.eclipse.edc.identityhub.spi.participantcontext.IdentityHubParticipantContextService;
import org.eclipse.edc.identityhub.spi.participantcontext.model.KeyDescriptor;
import org.eclipse.edc.identityhub.spi.participantcontext.model.ParticipantManifest;
import org.eclipse.edc.issuerservice.spi.issuance.attestation.AttestationDefinitionService;
import org.eclipse.edc.issuerservice.spi.issuance.credentialdefinition.CredentialDefinitionService;
import org.eclipse.edc.issuerservice.spi.issuance.model.AttestationDefinition;
import org.eclipse.edc.issuerservice.spi.issuance.model.CredentialDefinition;
import org.eclipse.edc.issuerservice.spi.issuance.model.MappingDefinition;
import org.niis.xroad.common.rpc.server.RpcResponseHandler;
import org.niis.xroad.edc.issuer.provisioning.proto.CreateAttestationDefinitionReq;
import org.niis.xroad.edc.issuer.provisioning.proto.CreateAttestationDefinitionResp;
import org.niis.xroad.edc.issuer.provisioning.proto.CreateCredentialDefinitionReq;
import org.niis.xroad.edc.issuer.provisioning.proto.CreateCredentialDefinitionResp;
import org.niis.xroad.edc.issuer.provisioning.proto.CreateParticipantContextReq;
import org.niis.xroad.edc.issuer.provisioning.proto.CreateParticipantContextResp;
import org.niis.xroad.edc.issuer.provisioning.proto.IssuerProvisioningServiceGrpc;

import java.util.List;
import java.util.Map;

import static org.niis.xroad.common.core.exception.ErrorCode.DSP_PARTICIPANT_CONTEXT_FAILED;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_PROVISIONING_FAILED;
import static org.niis.xroad.edc.extension.rpc.EdcProvisioningHelper.requireSuccessOrConflict;
import static org.niis.xroad.edc.extension.rpc.EdcProvisioningHelper.validateManifestFields;

/**
 * gRPC service that provisions the issuer participant context, attestation definitions and credential
 * definitions by delegating to the EDC issuer-service services directly (no REST admin API).
 *
 * <p>No {@code CreateHolder} RPC is defined here. The issuer runs with
 * {@code edc.issuance.anonymous.allowed=true}, so {@code DcpHolderTokenVerifier} auto-creates
 * an anonymous holder per requesting DID; no holder is ever explicitly provisioned.
 * Re-check this assumption on each EDC version upgrade.
 */
@RequiredArgsConstructor
class IssuerProvisioningGrpcService extends IssuerProvisioningServiceGrpc.IssuerProvisioningServiceImplBase {

    private static final String ISSUER_SERVICE_TYPE = "IssuerService";
    private static final String ISSUER_SERVICE_ID_SUFFIX = "-issuer-service";
    private static final String ADMIN_ROLE = "admin";
    private static final String KEY_ALGORITHM_PARAM = "algorithm";
    private static final String KEY_ALGORITHM = "EdDSA";

    private final IdentityHubParticipantContextService participantContextService;
    private final AttestationDefinitionService attestationDefinitionService;
    private final CredentialDefinitionService credentialDefinitionService;
    private final RpcResponseHandler responseHandler;

    @Override
    public void createParticipantContext(CreateParticipantContextReq request,
                                         StreamObserver<CreateParticipantContextResp> responseObserver) {
        responseHandler.handleRequest(responseObserver, () -> createParticipantContextInternal(request));
    }

    @Override
    public void createAttestationDefinition(CreateAttestationDefinitionReq request,
                                            StreamObserver<CreateAttestationDefinitionResp> responseObserver) {
        responseHandler.handleRequest(responseObserver, () -> createAttestationDefinitionInternal(request));
    }

    @Override
    public void createCredentialDefinition(CreateCredentialDefinitionReq request,
                                           StreamObserver<CreateCredentialDefinitionResp> responseObserver) {
        responseHandler.handleRequest(responseObserver, () -> createCredentialDefinitionInternal(request));
    }

    private CreateParticipantContextResp createParticipantContextInternal(CreateParticipantContextReq request) {
        validateManifestFields(request.getParticipantContextId(), request.getDid());
        var manifest = ParticipantManifest.Builder.newInstance()
                .participantContextId(request.getParticipantContextId())
                .did(request.getDid())
                .active(true)
                .scopes(List.of(ADMIN_ROLE))
                .serviceEndpoint(new Service(
                        request.getParticipantContextId() + ISSUER_SERVICE_ID_SUFFIX,
                        ISSUER_SERVICE_TYPE,
                        request.getIssuerServiceUrl()))
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

    private CreateAttestationDefinitionResp createAttestationDefinitionInternal(CreateAttestationDefinitionReq request) {
        var attestationDefinition = AttestationDefinition.Builder.newInstance()
                .id(request.getAttestationDefinitionId())
                .participantContextId(request.getParticipantContextId())
                .attestationType(request.getAttestationType())
                .configuration(Map.of())
                .build();

        var result = attestationDefinitionService.createAttestation(attestationDefinition);
        requireSuccessOrConflict(result, DSP_PROVISIONING_FAILED, request.getAttestationDefinitionId());
        return CreateAttestationDefinitionResp.getDefaultInstance();
    }

    private CreateCredentialDefinitionResp createCredentialDefinitionInternal(CreateCredentialDefinitionReq request) {
        var mappings = request.getMappingsList().stream()
                .map(m -> new MappingDefinition(m.getInput(), m.getOutput(), m.getRequired()))
                .toList();

        var credentialDefinition = CredentialDefinition.Builder.newInstance()
                .id(request.getCredentialDefinitionId())
                .participantContextId(request.getParticipantContextId())
                .credentialType(request.getCredentialType())
                .format(request.getFormat())
                .jsonSchema(request.getJsonSchema())
                .jsonSchemaUrl(request.getJsonSchemaUrl())
                .validity(request.getValiditySeconds())
                .attestations(request.getAttestationsList())
                .mappings(mappings)
                .build();

        var result = credentialDefinitionService.createCredentialDefinition(credentialDefinition);
        requireSuccessOrConflict(result, DSP_PROVISIONING_FAILED, request.getCredentialDefinitionId());
        return CreateCredentialDefinitionResp.getDefaultInstance();
    }

}
