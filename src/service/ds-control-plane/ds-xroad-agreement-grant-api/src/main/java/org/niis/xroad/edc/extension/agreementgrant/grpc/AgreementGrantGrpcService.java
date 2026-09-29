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
import org.eclipse.edc.policy.model.AndConstraint;
import org.eclipse.edc.policy.model.AtomicConstraint;
import org.eclipse.edc.policy.model.Constraint;
import org.eclipse.edc.policy.model.Expression;
import org.eclipse.edc.policy.model.LiteralExpression;
import org.eclipse.edc.policy.model.Operator;
import org.eclipse.edc.policy.model.OrConstraint;
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
import org.niis.xroad.edc.extension.policy.controlplane.XRoadPolicyNamespace;
import org.niis.xroad.edc.extension.policy.controlplane.util.PolicyContextHelper;
import org.niis.xroad.serverconf.model.BaseEndpoint;

import java.util.ArrayList;
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
        var permissions = agreement.getPolicy().getPermissions();
        if (permissions.size() != 1) {
            log.debug("Agreement {} policy has {} permissions, expected exactly 1", agreement.getId(), permissions.size());
            return noGrant(NoGrantReason.POLICY_NOT_UNDERSTOOD);
        }

        var pieces = flatten(permissions.get(0).getConstraints());
        var subjectAtomic = findSubjectConstraint(pieces);
        if (subjectAtomic == null || subjectAtomic.getOperator() != Operator.EQ) {
            return noGrant(NoGrantReason.POLICY_NOT_UNDERSTOOD);
        }

        var subjectKey = literalValue(subjectAtomic.getLeftExpression());
        if (!XRoadPolicyNamespace.XROAD_CLIENT_ID.equals(subjectKey)) {
            return noGrant(NoGrantReason.SUBJECT_NOT_A_SUBSYSTEM);
        }

        var client = parseSubjectClient(agreement, subjectAtomic);
        if (client == null) {
            return noGrant(NoGrantReason.POLICY_NOT_UNDERSTOOD);
        }
        if (client.getSubsystemCode() == null) {
            return noGrant(NoGrantReason.SUBJECT_NOT_A_SUBSYSTEM);
        }

        var pathPieces = pieces.stream().filter(piece -> piece != subjectAtomic).toList();
        var scopes = resolveScopes(pathPieces);
        if (scopes == null) {
            return noGrant(NoGrantReason.POLICY_NOT_UNDERSTOOD);
        }

        var service = AssetMapper.decodeAssetId(agreement.getAssetId());
        if (service == null) {
            log.debug("Agreement {} asset id could not be decoded into a service id", agreement.getId());
            return noGrant(NoGrantReason.POLICY_NOT_UNDERSTOOD);
        }

        return grant(client, service, scopes);
    }

    /**
     * Expands every {@link AndConstraint} into its own siblings, so that the single top-level constraint
     * {@code PolicyMapper} always adds to a permission — either a lone subject constraint, or
     * {@code AND(subjectConstraint, pathPiece)} — comes back as a flat list of independent pieces.
     * {@link OrConstraint} is left intact: it marks a group of alternative path constraints, not siblings.
     */
    private static List<Constraint> flatten(List<Constraint> constraints) {
        var result = new ArrayList<Constraint>();
        for (var constraint : constraints) {
            if (constraint instanceof AndConstraint andConstraint) {
                result.addAll(flatten(andConstraint.getConstraints()));
            } else {
                result.add(constraint);
            }
        }
        return result;
    }

    private static AtomicConstraint findSubjectConstraint(List<Constraint> pieces) {
        AtomicConstraint subject = null;
        for (var piece : pieces) {
            if (piece instanceof AtomicConstraint atomic && isSubjectKey(literalValue(atomic.getLeftExpression()))) {
                if (subject != null) {
                    return null;
                }
                subject = atomic;
            }
        }
        return subject;
    }

    private static boolean isSubjectKey(String key) {
        return XRoadPolicyNamespace.XROAD_CLIENT_ID.equals(key)
                || XRoadPolicyNamespace.XROAD_GLOBAL_GROUP.equals(key)
                || XRoadPolicyNamespace.XROAD_LOCAL_GROUP.equals(key);
    }

    private static ClientId parseSubjectClient(ContractAgreement agreement, AtomicConstraint subjectAtomic) {
        var encodedClientId = literalValue(subjectAtomic.getRightExpression());
        if (encodedClientId == null) {
            return null;
        }
        try {
            return PolicyContextHelper.parseClientId(encodedClientId);
        } catch (RuntimeException e) {
            log.debug("Agreement {} subject constraint value '{}' could not be parsed as a client id",
                    agreement.getId(), encodedClientId, e);
            return null;
        }
    }

    /**
     * @return the scopes granted by the (at most one) remaining path piece, the base-endpoint sentinel when
     *         there is none, or {@code null} when the piece is not a recognised datapath shape
     */
    private static List<Scope> resolveScopes(List<Constraint> pathPieces) {
        if (pathPieces.isEmpty()) {
            return List.of(Scope.newBuilder()
                    .setMethod(BaseEndpoint.ANY_METHOD)
                    .setPath(BaseEndpoint.ANY_PATH)
                    .build());
        }
        if (pathPieces.size() > 1) {
            return null;
        }

        var piece = pathPieces.get(0);
        if (piece instanceof OrConstraint or) {
            return resolvePathAtomics(or.getConstraints());
        }
        if (piece instanceof AtomicConstraint atomic) {
            return resolvePathAtomics(List.of(atomic));
        }
        return null;
    }

    private static List<Scope> resolvePathAtomics(List<Constraint> constraints) {
        var scopes = new ArrayList<Scope>();
        for (var constraint : constraints) {
            if (!(constraint instanceof AtomicConstraint atomic)) {
                return null;
            }
            var scope = parsePathScope(atomic);
            if (scope == null) {
                return null;
            }
            scopes.add(scope);
        }
        return scopes;
    }

    private static Scope parsePathScope(AtomicConstraint atomic) {
        if (!XRoadPolicyNamespace.XROAD_DATAPATH.equals(literalValue(atomic.getLeftExpression()))
                || atomic.getOperator() != Operator.EQ) {
            return null;
        }
        var value = literalValue(atomic.getRightExpression());
        if (value == null) {
            return null;
        }
        var spaceIndex = value.indexOf(' ');
        if (spaceIndex <= 0 || spaceIndex == value.length() - 1) {
            return null;
        }
        return Scope.newBuilder()
                .setMethod(value.substring(0, spaceIndex))
                .setPath(value.substring(spaceIndex + 1))
                .build();
    }

    private static String literalValue(Expression expression) {
        return expression instanceof LiteralExpression literal && literal.getValue() != null
                ? literal.getValue().toString()
                : null;
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
