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
package org.niis.xroad.edc.extension.agreementgrant.grpc;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

import io.grpc.stub.StreamObserver;
import io.opentelemetry.instrumentation.annotations.WithSpan;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.edc.connector.controlplane.contract.spi.negotiation.store.ContractNegotiationStore;
import org.eclipse.edc.connector.controlplane.contract.spi.types.agreement.ContractAgreement;
import org.niis.xroad.common.rpc.mapper.ClientIdMapper;
import org.niis.xroad.common.rpc.mapper.ServiceIdMapper;
import org.niis.xroad.common.rpc.server.RpcResponseHandler;
import org.niis.xroad.edc.agreementgrant.proto.AgreementGrantServiceGrpc;
import org.niis.xroad.edc.agreementgrant.proto.Grant;
import org.niis.xroad.edc.agreementgrant.proto.NoGrant;
import org.niis.xroad.edc.agreementgrant.proto.NoGrantReason;
import org.niis.xroad.edc.agreementgrant.proto.ResolveAgreementGrantRequest;
import org.niis.xroad.edc.agreementgrant.proto.ResolveAgreementGrantResponse;
import org.niis.xroad.edc.agreementgrant.proto.Scope;
import org.niis.xroad.edc.extension.catalog.AssetMapper;
import org.niis.xroad.edc.extension.policy.controlplane.PolicySubject;
import org.niis.xroad.edc.extension.policy.controlplane.XRoadPolicyReader;
import org.niis.xroad.edc.extension.policy.controlplane.XRoadPolicyShape;
import org.niis.xroad.serverconf.model.BaseEndpoint;

import java.util.List;

/**
 * gRPC service that resolves the grant behind a negotiated agreement, so the provider's data plane can mint an
 * agreement token without this service minting anything itself. Reads the agreement's ODRL policy tree written
 * by the catalog ({@code AND(subjectConstraint, OR(pathConstraints))}) and turns it back into a consumer client,
 * a provider service and a method/path scope, or a typed reason why no grant can be stated.
 */
@Slf4j
@RequiredArgsConstructor
class AgreementGrantGrpcService extends AgreementGrantServiceGrpc.AgreementGrantServiceImplBase {

    private final ContractNegotiationStore contractNegotiationStore;
    private final RpcResponseHandler responseHandler;

    @Override
    @WithSpan("dsp-agreement-grant-resolve")
    public void resolveAgreementGrant(ResolveAgreementGrantRequest request,
                                       StreamObserver<ResolveAgreementGrantResponse> responseObserver) {
        responseHandler.handleRequest(responseObserver, () -> resolveInternal(request));
    }

    private ResolveAgreementGrantResponse resolveInternal(ResolveAgreementGrantRequest request) {
        var agreement = contractNegotiationStore.findContractAgreement(request.getAgreementId());
        if (agreement == null) {
            return noGrant(NoGrantReason.AGREEMENT_NOT_FOUND);
        }
        return resolveGrant(agreement);
    }

    private ResolveAgreementGrantResponse resolveGrant(ContractAgreement agreement) {
        var shape = XRoadPolicyReader.read(agreement.getPolicy()).orElse(null);
        if (shape == null) {
            log.debug("Agreement {} policy is not a single-permission subject policy", agreement.getId());
            return noGrant(NoGrantReason.POLICY_NOT_UNDERSTOOD);
        }

        if (!(shape.subject() instanceof PolicySubject.Client subject) || !subject.isSubsystem()) {
            return noGrant(NoGrantReason.SUBJECT_NOT_A_SUBSYSTEM);
        }

        var scopes = resolveScopes(shape);
        if (scopes == null) {
            return noGrant(NoGrantReason.POLICY_NOT_UNDERSTOOD);
        }

        var service = AssetMapper.decodeAssetId(agreement.getAssetId());
        if (service == null) {
            log.debug("Agreement {} asset id could not be decoded into a service id", agreement.getId());
            return noGrant(NoGrantReason.POLICY_NOT_UNDERSTOOD);
        }

        return grant(subject.clientId(), service, scopes);
    }

    /**
     * @return the scopes granted by the policy's endpoint constraints, the base-endpoint sentinel when there are
     *         none, or {@code null} when the endpoint constraints are not a recognised datapath shape
     */
    private static List<Scope> resolveScopes(XRoadPolicyShape shape) {
        var endpoints = shape.endpoints().orElse(null);
        if (endpoints == null) {
            return null;
        }
        if (endpoints.isEmpty()) {
            return List.of(Scope.newBuilder()
                    .setMethod(BaseEndpoint.ANY_METHOD)
                    .setPath(BaseEndpoint.ANY_PATH)
                    .build());
        }
        return endpoints.stream()
                .map(endpoint -> Scope.newBuilder()
                        .setMethod(endpoint.method())
                        .setPath(endpoint.path())
                        .build())
                .toList();
    }

    private static ResolveAgreementGrantResponse noGrant(NoGrantReason reason) {
        return ResolveAgreementGrantResponse.newBuilder()
                .setNoGrant(NoGrant.newBuilder().setReason(reason).build())
                .build();
    }

    private static ResolveAgreementGrantResponse grant(ClientId client, ServiceId service, List<Scope> scopes) {
        var grant = Grant.newBuilder()
                .setClientId(ClientIdMapper.toDto(client))
                .setServiceId(ServiceIdMapper.toDto(service))
                .addAllScopes(scopes)
                .build();
        return ResolveAgreementGrantResponse.newBuilder()
                .setGrant(grant)
                .build();
    }
}
