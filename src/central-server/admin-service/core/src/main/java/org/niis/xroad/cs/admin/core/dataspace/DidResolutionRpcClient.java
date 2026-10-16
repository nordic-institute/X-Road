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
import org.niis.xroad.cs.admin.api.domain.DidResolution;
import org.niis.xroad.cs.admin.api.domain.DidResolutionReason;
import org.niis.xroad.cs.admin.api.service.DidResolutionService;
import org.niis.xroad.edc.issuer.provisioning.proto.DidResolutionResult;
import org.niis.xroad.edc.issuer.provisioning.proto.DidResolutionServiceGrpc;
import org.niis.xroad.edc.issuer.provisioning.proto.ResolveDidsReq;
import org.niis.xroad.edc.issuer.provisioning.proto.ResolveDidsResp;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;

import java.util.List;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.niis.xroad.common.core.exception.ErrorCode.INTERNAL_ERROR;
import static org.niis.xroad.common.core.exception.ErrorCode.NETWORK_ERROR;

/**
 * gRPC transport client for the DID resolution service. The stub does not wait for the Issuer Service to become
 * ready and every call carries its own deadline.
 */
@Slf4j
@RequiredArgsConstructor
public class DidResolutionRpcClient extends AbstractRpcClient
        implements DidResolutionService, InitializingBean, DisposableBean {

    private final RpcChannelFactory rpcChannelFactory;
    private final DidResolutionRpcChannelProperties channelProperties;

    private ManagedChannel channel;
    private DidResolutionServiceGrpc.DidResolutionServiceBlockingStub stub;

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
        stub = DidResolutionServiceGrpc.newBlockingStub(channel);
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

    @Override
    public List<DidResolution> resolve(List<String> dids) {
        ResolveDidsResp response;
        try {
            response = exec(() -> {
                try {
                    return stub.withDeadlineAfter(channelProperties.deadlineAfter(), MILLISECONDS)
                            .resolveDids(ResolveDidsReq.newBuilder().addAllDids(dids).build());
                } catch (StatusRuntimeException e) {
                    if (e.getStatus().getCode() == Status.Code.UNAVAILABLE) {
                        throw XrdRuntimeException.systemException(NETWORK_ERROR)
                                .origin(getRpcOrigin())
                                .cause(e)
                                .details("DID resolution call failed: " + e.getStatus().getCode())
                                .build();
                    }
                    throw e;
                }
            });
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.INTERNAL) {
                throw XrdRuntimeException.systemException(INTERNAL_ERROR)
                        .origin(getRpcOrigin())
                        .cause(e)
                        .details("DID resolution call failed: " + e.getStatus().getCode())
                        .build();
            }
            throw e;
        }
        return response.getResultsList().stream().map(DidResolutionRpcClient::toDomain).toList();
    }

    private static DidResolution toDomain(DidResolutionResult result) {
        return switch (result.getOutcome()) {
            case RESOLVED -> DidResolution.resolved(result.getDid());
            case NOT_RESOLVED -> DidResolution.notResolved(result.getDid(), toDomain(result.getReason()));
            default -> throw XrdRuntimeException.systemException(INTERNAL_ERROR)
                    .origin(ErrorOrigin.DS_ISSUER_SERVICE)
                    .details("DID resolution returned an unknown outcome: " + result.getOutcome())
                    .build();
        };
    }

    private static DidResolutionReason toDomain(org.niis.xroad.edc.issuer.provisioning.proto.DidResolutionReason reason) {
        return switch (reason) {
            case MALFORMED_DID -> DidResolutionReason.MALFORMED_DID;
            case NO_DOCUMENT -> DidResolutionReason.NO_DOCUMENT;
            case ID_MISMATCH -> DidResolutionReason.ID_MISMATCH;
            case FETCH_FAILURE -> DidResolutionReason.FETCH_FAILURE;
            default -> throw XrdRuntimeException.systemException(INTERNAL_ERROR)
                    .origin(ErrorOrigin.DS_ISSUER_SERVICE)
                    .details("DID resolution returned an unknown reason: " + reason)
                    .build();
        };
    }
}
