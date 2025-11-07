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

package org.niis.xroad.management.rpc;


import io.grpc.ManagedChannel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.ErrorOrigin;
import org.niis.xroad.common.rpc.client.AbstractRpcClient;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.messagelog.archiver.proto.MessageLogArchivalRequest;
import org.niis.xroad.messagelog.archiver.proto.MessageLogArchivalResp;
import org.niis.xroad.messagelog.archiver.proto.MessageLogArchiverServiceGrpc;
import org.niis.xroad.messagelog.archiver.proto.MessageLogCleanupRequest;
import org.niis.xroad.messagelog.archiver.proto.MessageLogCleanupResp;

@Slf4j
@RequiredArgsConstructor
@ApplicationScoped
public class ManagementRpcClient extends AbstractRpcClient {
    private final RpcChannelFactory proxyRpcChannelFactory;
    private final ManagementRpcChannelProperties rpcChannelProperties;

    private ManagedChannel channel;
    private MessageLogArchiverServiceGrpc.MessageLogArchiverServiceBlockingStub archiverServiceBlockingStub;

    @Override
    public ErrorOrigin getRpcOrigin() {
        return ErrorOrigin.PROXY;
    }

    @PostConstruct
    public void init() {
        log.info("Initializing {} rpc client to {}:{}", getClass().getSimpleName(), rpcChannelProperties.host(),
                rpcChannelProperties.port());
        channel = proxyRpcChannelFactory.createChannel(rpcChannelProperties);

        archiverServiceBlockingStub = MessageLogArchiverServiceGrpc.newBlockingStub(channel).withInterceptors().withWaitForReady();

    }

    @Override
    @PreDestroy
    public void close() {
        if (channel != null) {
            channel.shutdown();
        }
    }

    public MessageLogArchivalResp triggerArchival(MessageLogArchivalRequest request) {
        return exec(() -> archiverServiceBlockingStub
                .triggerArchival(request));
    }

    public MessageLogCleanupResp triggerCleanup(MessageLogCleanupRequest request) {
        return exec(() -> archiverServiceBlockingStub
                .triggerCleanup(request));
    }
}
