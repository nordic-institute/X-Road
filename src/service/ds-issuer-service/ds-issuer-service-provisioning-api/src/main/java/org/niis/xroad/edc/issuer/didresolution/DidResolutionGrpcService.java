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
package org.niis.xroad.edc.issuer.didresolution;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.common.rpc.server.RpcResponseHandler;
import org.niis.xroad.edc.issuer.provisioning.proto.DidResolutionOutcome;
import org.niis.xroad.edc.issuer.provisioning.proto.DidResolutionReason;
import org.niis.xroad.edc.issuer.provisioning.proto.DidResolutionResult;
import org.niis.xroad.edc.issuer.provisioning.proto.DidResolutionServiceGrpc;
import org.niis.xroad.edc.issuer.provisioning.proto.ResolveDidsReq;
import org.niis.xroad.edc.issuer.provisioning.proto.ResolveDidsResp;

/**
 * gRPC adapter of {@link SubmittedDidResolver}. Resolves the requested DIDs one after the other and answers in
 * request order.
 */
@RequiredArgsConstructor
class DidResolutionGrpcService extends DidResolutionServiceGrpc.DidResolutionServiceImplBase {

    private final SubmittedDidResolver resolver;
    private final RpcResponseHandler responseHandler;

    @Override
    public void resolveDids(ResolveDidsReq request, StreamObserver<ResolveDidsResp> responseObserver) {
        responseHandler.handleRequest(responseObserver, () -> resolveInternal(request));
    }

    private ResolveDidsResp resolveInternal(ResolveDidsReq request) {
        var response = ResolveDidsResp.newBuilder();
        request.getDidsList().forEach(did -> response.addResults(toProto(resolver.resolve(did))));
        return response.build();
    }

    private static DidResolutionResult toProto(SubmittedDidResolver.Resolution resolution) {
        var result = DidResolutionResult.newBuilder().setDid(resolution.did());
        if (resolution.isResolved()) {
            return result.setOutcome(DidResolutionOutcome.RESOLVED).build();
        }
        return result.setOutcome(DidResolutionOutcome.NOT_RESOLVED)
                .setReason(toProto(resolution.reason()))
                .build();
    }

    private static DidResolutionReason toProto(SubmittedDidResolver.Reason reason) {
        return switch (reason) {
            case MALFORMED_DID -> DidResolutionReason.MALFORMED_DID;
            case NO_DOCUMENT -> DidResolutionReason.NO_DOCUMENT;
            case ID_MISMATCH -> DidResolutionReason.ID_MISMATCH;
            case FETCH_FAILURE -> DidResolutionReason.FETCH_FAILURE;
        };
    }
}
