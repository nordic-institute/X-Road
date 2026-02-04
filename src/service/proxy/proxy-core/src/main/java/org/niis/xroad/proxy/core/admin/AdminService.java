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

package org.niis.xroad.proxy.core.admin;

import ee.ria.xroad.common.AddOnStatusDiagnostics;
import ee.ria.xroad.common.ProxyMemory;
import ee.ria.xroad.common.identifier.ClientId;

import io.grpc.stub.StreamObserver;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.messagelog.archive.EncryptionConfigProvider;
import org.niis.xroad.proxy.core.admin.handler.TimestampStatusHandler;
import org.niis.xroad.proxy.core.configuration.ProxyMessageLogProperties;
import org.niis.xroad.proxy.proto.AddOnStatusResp;
import org.niis.xroad.proxy.proto.AdminServiceGrpc;
import org.niis.xroad.proxy.proto.MessageLogArchiveEncryptionMember;
import org.niis.xroad.proxy.proto.MessageLogEncryptionStatusResp;
import org.niis.xroad.proxy.proto.ProxyMemoryStatusResp;
import org.niis.xroad.proxy.proto.TimestampStatusResp;
import org.niis.xroad.proxy.proto.TimestampingPrioritizationStrategyResp;
import org.niis.xroad.proxy.proto.dto.MessageLogEncryptionStatusDiagnostics;
import org.niis.xroad.rpc.common.Empty;
import org.niis.xroad.rpc.common.ServicePrioritizationStrategy;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

@ApplicationScoped
@RequiredArgsConstructor
@Slf4j
public class AdminService extends AdminServiceGrpc.AdminServiceImplBase {

    private final ServerConfProvider serverConfProvider;
    private final AddOnStatusDiagnostics addOnStatusDiagnostics;
    private final TimestampStatusHandler timestampStatusHandler;
    private final ProxyMemoryStatusService proxyMemoryStatusService;
    private final EncryptionConfigProvider encryptionConfigProvider;
    private final ProxyMessageLogProperties messageLogProperties;

    private MessageLogEncryptionStatusDiagnostics messageLogEncryptionStatusDiagnostics;

    @Override
    public void getAddOnStatus(Empty request, StreamObserver<AddOnStatusResp> responseObserver) {
        handleRequest(responseObserver, this::handleAddOnStatus);
    }

    @Override
    public void getMessageLogEncryptionStatus(Empty request, StreamObserver<MessageLogEncryptionStatusResp> responseObserver) {
        handleRequest(responseObserver, this::handleMessageLogEncryptionStatus);
    }

    @Override
    public void getTimestampStatus(Empty request, StreamObserver<TimestampStatusResp> responseObserver) {
        handleRequest(responseObserver, timestampStatusHandler::handle);
    }

    @Override
    public void getProxyMemoryStatus(Empty request, StreamObserver<ProxyMemoryStatusResp> responseObserver) {
        handleRequest(responseObserver, this::handleProxyMemoryStatus);
    }

    @Override
    public void clearConfCache(Empty request, StreamObserver<Empty> responseObserver) {
        handleRequest(responseObserver, this::handleClearConfCache);
    }

    @Override
    public void getTimestampingPrioritizationStrategy(
            Empty request, StreamObserver<TimestampingPrioritizationStrategyResp> responseObserver) {
        handleRequest(responseObserver, this::handleGetTimestampingPrioritizationStrategy);
    }

    private <T> void handleRequest(StreamObserver<T> responseObserver, Supplier<T> handler) {
        try {
            responseObserver.onNext(handler.get());
            responseObserver.onCompleted();
        } catch (Exception e) {
            log.error("Error handling request", e);
            responseObserver.onError(e);
        }
    }

    private AddOnStatusResp handleAddOnStatus() {
        return AddOnStatusResp.newBuilder()
                .setMessageLogEnabled(addOnStatusDiagnostics.isMessageLogEnabled())
                .setOpMonitoringEnabled(addOnStatusDiagnostics.isOpMonitoringEnabled())
                .build();
    }

    private MessageLogEncryptionStatusResp handleMessageLogEncryptionStatus() {
        if (messageLogEncryptionStatusDiagnostics == null) {
            messageLogEncryptionStatusDiagnostics = messageLogEncryptionStatusDiagnostics();
        }

        List<MessageLogArchiveEncryptionMember> members = messageLogEncryptionStatusDiagnostics.members().stream()
                .map(member -> MessageLogArchiveEncryptionMember.newBuilder()
                        .setMemberId(member.memberId())
                        .addAllKeys(member.keys())
                        .setDefaultKeyUsed(member.defaultKeyUsed())
                        .build())
                .toList();

        return MessageLogEncryptionStatusResp.newBuilder()
                .setMessageLogArchiveEncryptionStatus(messageLogEncryptionStatusDiagnostics.messageLogArchiveEncryptionStatus())
                .setMessageLogDatabaseEncryptionStatus(messageLogEncryptionStatusDiagnostics.messageLogDatabaseEncryptionStatus())
                .setMessageLogGroupingRule(messageLogEncryptionStatusDiagnostics.messageLogGroupingRule())
                .addAllMembers(members)
                .build();
    }

    private ProxyMemoryStatusResp handleProxyMemoryStatus() {
        ProxyMemory proxyMemory = proxyMemoryStatusService.getMemoryStatus();
        var responseBuilder = ProxyMemoryStatusResp.newBuilder()
                .setFreeMemory(proxyMemory.freeMemory())
                .setTotalMemory(proxyMemory.totalMemory())
                .setMaxMemory(proxyMemory.maxMemory())
                .setUsedMemory(proxyMemory.usedMemory())
                .setUsedPercent(proxyMemory.usedPercent());
        if (proxyMemory.threshold() != null) {
            responseBuilder.setThreshold(proxyMemory.threshold());
        }
        return responseBuilder.build();
    }

    private Empty handleClearConfCache() {
        serverConfProvider.clearCache();
        return Empty.getDefaultInstance();
    }


    private MessageLogEncryptionStatusDiagnostics messageLogEncryptionStatusDiagnostics() {
        return new MessageLogEncryptionStatusDiagnostics(
                messageLogProperties.archiver().encryptionEnabled(),
                messageLogProperties.databaseEncryption().enabled(),
                messageLogProperties.archiver().groupingStrategy().name(),
                getMessageLogArchiveEncryptionMembers());
    }

    private List<ClientId> getMembers() {
        try {
            return new ArrayList<>(serverConfProvider.getMembers());
        } catch (Exception e) {
            log.warn("Failed to get members from server configuration", e);
            return Collections.emptyList();
        }
    }

    private List<org.niis.xroad.proxy.proto.dto.MessageLogArchiveEncryptionMember> getMessageLogArchiveEncryptionMembers() {
        var members = getMembers();
        if (!encryptionConfigProvider.isEncryptionEnabled()) {
            return Collections.emptyList();
        }
        return encryptionConfigProvider.forDiagnostics(members).encryptionMembers()
                .stream()
                .map(member -> new org.niis.xroad.proxy.proto.dto.MessageLogArchiveEncryptionMember(member.memberId(),
                        member.keys(), member.defaultKeyUsed()))
                .toList();
    }

    private TimestampingPrioritizationStrategyResp handleGetTimestampingPrioritizationStrategy() {
        var strategy = messageLogProperties.timestampingPrioritizationStrategy();
        return TimestampingPrioritizationStrategyResp.newBuilder()
                .setStrategy(ee.ria.xroad.common.ServicePrioritizationStrategy.NONE.equals(strategy)
                        ? ServicePrioritizationStrategy.SERVICE_PRIORITIZATION_STRATEGY_NONE
                        : ServicePrioritizationStrategy.valueOf(strategy.name()))
                .build();
    }

}
