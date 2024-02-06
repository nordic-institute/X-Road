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

package org.niis.xroad.edc.extension.messagelog;

import io.grpc.Channel;
import lombok.Getter;
import org.eclipse.edc.spi.monitor.Monitor;
import org.niis.xroad.common.rpc.client.RpcClient;
import org.niis.xroad.common.rpc.mapper.ClientIdMapper;
import org.niis.xroad.common.rpc.mapper.ServiceIdMapper;
import org.niis.xroad.edc.spi.messagelog.LogMessage;
import org.niis.xroad.edc.spi.messagelog.XRoadMessageLog;
import org.niis.xroad.messagelog.proto.LogMessageReq;
import org.niis.xroad.messagelog.proto.MessagelogServiceGrpc;
import org.niis.xroad.messagelog.proto.SignatureData;

import static java.util.Optional.ofNullable;

public class XRoadMessageLogImpl implements XRoadMessageLog {

    private final Monitor monitor;

    private final RpcClient<MessageLogExecutionContext> rpcClient;

    public XRoadMessageLogImpl(Monitor monitor, String grpcHost, int grpcPort, int timeout) {
        monitor.info("Initializing messagelog with gRPC client.");
        this.monitor = monitor;
        try {
            this.rpcClient = RpcClient.newClient(grpcHost, grpcPort, timeout, MessageLogExecutionContext::new);
        } catch (Exception e) {
            throw new RuntimeException("Messagelog service init failed.", e);
        }
    }

    public XRoadMessageLogImpl(Monitor monitor) {
        monitor.warning("Initializing messagelog without gRPC.");
        this.monitor = monitor;
        this.rpcClient = null;
    }

    @Override
    public void log(LogMessage message) {
        monitor.debug("Logging message to message log.");
        if (rpcClient != null) {
            try {
                rpcClient.execute(ctx -> ctx.getMessagelogServiceBlockingStub().log(transform(message)));
            } catch (Exception e) {
                // todo:
                throw new RuntimeException("Messagelog request failed", e);
            }
        } else {
            monitor.warning("Messagelog gRPC client not initialized. Message not logged.");
        }
    }

    private LogMessageReq transform(LogMessage message) {
        var signatureBuilder = SignatureData.newBuilder()
                .setSignatureXml(message.signature().signatureXml());

        ofNullable(message.signature().hashChain()).ifPresent(signatureBuilder::setHashChain);
        ofNullable(message.signature().hashChainResult()).ifPresent(signatureBuilder::setHashChainResult);

        var builder = LogMessageReq.newBuilder()
                .setSignature(signatureBuilder.build())
                .setClientId(ClientIdMapper.toDto(message.clientId()))
                .setServiceId(ServiceIdMapper.toDto(message.serviceId()))
                .setClientSide(message.clientSide())
                .setResponse(message.response());

        ofNullable(message.body()).ifPresent(builder::setBody);
        ofNullable(message.queryId()).ifPresent(builder::setQueryId);
        ofNullable(message.xRequestId()).ifPresent(builder::setXRequestId);

        return builder.build();
    }

    @Getter
    private static class MessageLogExecutionContext implements RpcClient.ExecutionContext {
        private final MessagelogServiceGrpc.MessagelogServiceBlockingStub messagelogServiceBlockingStub;

        MessageLogExecutionContext(Channel channel) {
            messagelogServiceBlockingStub = MessagelogServiceGrpc.newBlockingStub(channel).withWaitForReady();
        }

    }
}
