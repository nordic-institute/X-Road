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
package org.niis.xroad.cs.admin.core.dataspace;

import io.grpc.ManagedChannel;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.ErrorOrigin;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.common.rpc.client.AbstractRpcClient;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.cs.admin.api.domain.CanSignCause;
import org.niis.xroad.cs.admin.api.domain.CanSignResult;
import org.niis.xroad.edc.issuer.provisioning.proto.CanSignReq;
import org.niis.xroad.edc.issuer.provisioning.proto.CanSignResp;
import org.niis.xroad.edc.issuer.provisioning.proto.CreateAttestationDefinitionReq;
import org.niis.xroad.edc.issuer.provisioning.proto.CreateCredentialDefinitionReq;
import org.niis.xroad.edc.issuer.provisioning.proto.CreateParticipantContextReq;
import org.niis.xroad.edc.issuer.provisioning.proto.CredentialMapping;
import org.niis.xroad.edc.issuer.provisioning.proto.IssuerProvisioningServiceGrpc;
import org.niis.xroad.edc.issuer.provisioning.proto.RevokeCredentialReq;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.util.List;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.niis.xroad.common.core.exception.ErrorCode.DSP_PARTICIPANT_CONTEXT_CONFLICT;
import static org.niis.xroad.common.core.exception.ErrorCode.INTERNAL_ERROR;

/**
 * gRPC transport client for the issuer provisioning service.
 */
@Slf4j
@RequiredArgsConstructor
public class IssuerProvisioningRpcClient extends AbstractRpcClient implements InitializingBean, DisposableBean {

    /**
     * Deadline for {@link #canSign(String)} alone. Set-up and the status check call it while a human waits, so it
     * must fail fast when the Issuer Service is down rather than block for the channel's default deadline; a few
     * seconds is enough for a healthy, collocated Issuer Service to answer.
     */
    private static final long CAN_SIGN_DEADLINE_MILLIS = 3_000;

    private final RpcChannelFactory rpcChannelFactory;
    private final IssuerProvisioningRpcChannelProperties channelProperties;

    private ManagedChannel channel;
    private IssuerProvisioningServiceGrpc.IssuerProvisioningServiceBlockingStub stub;

    @Override
    public ErrorOrigin getRpcOrigin() {
        return ErrorOrigin.DS_ISSUER_SERVICE;
    }

    @Override
    public ManagedChannel getChannel() {
        return channel;
    }

    @Override
    public void afterPropertiesSet() {
        log.info("Initializing {} rpc client to {}:{}", getClass().getSimpleName(),
                channelProperties.host(), channelProperties.port());
        channel = rpcChannelFactory.createChannel(channelProperties);
        stub = IssuerProvisioningServiceGrpc.newBlockingStub(channel).withWaitForReady();
    }

    @Override
    public void close() {
        if (channel != null) {
            channel.shutdown();
        }
    }

    @Override
    public void destroy() {
        close();
    }

    /**
     * Creates the participant context for the given issuer identity. A conflict, meaning the context already
     * exists, is not an error here: the caller decides whether that is acceptable by checking "can sign" itself.
     */
    public void createParticipantContext(String participantContextId, String did, String issuerServiceUrl,
                                         String keyId, String privateKeyAlias) {
        try {
            exec(() -> stub.createParticipantContext(CreateParticipantContextReq.newBuilder()
                    .setParticipantContextId(participantContextId)
                    .setDid(did)
                    .setIssuerServiceUrl(issuerServiceUrl)
                    .setKeyId(keyId)
                    .setPrivateKeyAlias(privateKeyAlias)
                    .build()));
        } catch (XrdRuntimeException e) {
            if (!e.isCausedBy(DSP_PARTICIPANT_CONTEXT_CONFLICT)) {
                throw e;
            }
        }
    }

    public void createAttestationDefinition(String participantContextId, String attestationDefinitionId, String attestationType) {
        exec(() -> stub.createAttestationDefinition(CreateAttestationDefinitionReq.newBuilder()
                .setParticipantContextId(participantContextId)
                .setAttestationDefinitionId(attestationDefinitionId)
                .setAttestationType(attestationType)
                .build()));
    }

    public void createCredentialDefinition(String participantContextId, String credentialDefinitionId, String credentialType,
                                           String format, String jsonSchema, String jsonSchemaUrl, long validitySeconds,
                                           List<String> attestations, List<CredentialMapping> mappings) {
        exec(() -> stub.createCredentialDefinition(CreateCredentialDefinitionReq.newBuilder()
                .setParticipantContextId(participantContextId)
                .setCredentialDefinitionId(credentialDefinitionId)
                .setCredentialType(credentialType)
                .setFormat(format)
                .setJsonSchema(jsonSchema)
                .setJsonSchemaUrl(jsonSchemaUrl)
                .setValiditySeconds(validitySeconds)
                .addAllAttestations(attestations)
                .addAllMappings(mappings)
                .build()));
    }

    /**
     * Revokes every credential at the issuer matching the given participant context and holder DID that was
     * issued before the given cut-off timestamp (POSIX milliseconds).
     *
     * @return the number of credentials revoked
     */
    public int revokeCredential(String participantContextId, String holderDid, long issuedBefore) {
        var response = exec(() -> stub.revokeCredential(RevokeCredentialReq.newBuilder()
                .setParticipantContextId(participantContextId)
                .setHolderDid(holderDid)
                .setIssuedBefore(issuedBefore)
                .build()));
        return response.getRevokedCount();
    }

    /**
     * Checks whether the issuer can sign credentials for the given participant context, under
     * {@link #CAN_SIGN_DEADLINE_MILLIS} instead of the channel's own deadline. A deadline overrun comes back as
     * {@link CanSignCause#TIMEOUT}; any other failure to reach the Issuer Service comes back as
     * {@link CanSignCause#ISSUER_SERVICE_UNREACHABLE}. Neither is an exception, so a caller reading the status
     * can rely on this method never throwing for a down or unreachable Issuer Service.
     */
    public CanSignResult canSign(String participantContextId) {
        try {
            var response = stub.withDeadlineAfter(CAN_SIGN_DEADLINE_MILLIS, MILLISECONDS)
                    .canSign(CanSignReq.newBuilder().setParticipantContextId(participantContextId).build());
            return toDomain(response);
        } catch (StatusRuntimeException error) {
            if (error.getStatus().getCode() == Status.Code.DEADLINE_EXCEEDED) {
                return CanSignResult.cannotSign(CanSignCause.TIMEOUT);
            }
            log.warn("Can-sign check could not reach the Issuer Service: status={} serverError={}",
                    error.getStatus().getCode(), mapServerError(error).map(Object::toString).orElse("none"));
            return CanSignResult.cannotSign(CanSignCause.ISSUER_SERVICE_UNREACHABLE);
        }
    }

    private static CanSignResult toDomain(CanSignResp response) {
        return switch (response.getOutcome()) {
            case ABSENT -> CanSignResult.absent();
            case CAN_SIGN -> CanSignResult.canSign(response.getDid());
            case CANNOT_SIGN -> CanSignResult.cannotSign(toDomain(response.getCause()));
            default -> throw XrdRuntimeException.systemException(INTERNAL_ERROR)
                    .origin(ErrorOrigin.DS_ISSUER_SERVICE)
                    .details("Can-sign check returned an unknown outcome: " + response.getOutcome())
                    .build();
        };
    }

    private static CanSignCause toDomain(org.niis.xroad.edc.issuer.provisioning.proto.CanSignCause cause) {
        return switch (cause) {
            case NO_ACTIVE_KEY -> CanSignCause.NO_ACTIVE_KEY;
            case DID_NOT_PUBLISHED -> CanSignCause.DID_NOT_PUBLISHED;
            default -> throw XrdRuntimeException.systemException(INTERNAL_ERROR)
                    .origin(ErrorOrigin.DS_ISSUER_SERVICE)
                    .details("Can-sign check returned an unknown cause: " + cause)
                    .build();
        };
    }
}
