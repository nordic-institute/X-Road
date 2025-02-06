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
import ee.ria.xroad.common.BackupEncryptionStatusDiagnostics;
import ee.ria.xroad.common.MessageLogEncryptionStatusDiagnostics;

import io.grpc.stub.StreamObserver;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.proxy.core.admin.handler.TimestampStatusHandler;
import org.niis.xroad.proxy.proto.AddOnStatusResp;
import org.niis.xroad.proxy.proto.AdminServiceGrpc;
import org.niis.xroad.proxy.proto.BackupEncryptionStatusResp;
import org.niis.xroad.proxy.proto.Empty;
import org.niis.xroad.proxy.proto.MessageLogArchiveEncryptionMember;
import org.niis.xroad.proxy.proto.MessageLogEncryptionStatusResp;
import org.niis.xroad.proxy.proto.TimestampStatusResp;
import org.niis.xroad.serverconf.ServerConfProvider;

import java.util.List;
import java.util.function.Supplier;

import static java.util.Collections.unmodifiableList;

@ApplicationScoped
@RequiredArgsConstructor
public class AdminService extends AdminServiceGrpc.AdminServiceImplBase {

    private final ServerConfProvider serverConfProvider;
    private final BackupEncryptionStatusDiagnostics backupEncryptionStatusDiagnostics;
    private final AddOnStatusDiagnostics addOnStatusDiagnostics;
    private final MessageLogEncryptionStatusDiagnostics messageLogEncryptionStatusDiagnostics;
    private final TimestampStatusHandler timestampStatusHandler;

    @Override
    public void getBackupEncryptionStatus(Empty request, StreamObserver<BackupEncryptionStatusResp> responseObserver) {
        handleRequest(responseObserver, this::handleGetBackupEncryptionStatus);
    }

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
    public void clearConfCache(Empty request, StreamObserver<Empty> responseObserver) {
        handleRequest(responseObserver, this::handleClearConfCache);
    }

    private <T> void handleRequest(StreamObserver<T> responseObserver, Supplier<T> handler) {
        try {
            responseObserver.onNext(handler.get());
            responseObserver.onCompleted();
        } catch (Exception e) {
            responseObserver.onError(e);
        }
    }

    private AddOnStatusResp handleAddOnStatus() {
        return AddOnStatusResp.newBuilder()
                .setMessageLogEnabled(addOnStatusDiagnostics.isMessageLogEnabled())
                .build();
    }

    private BackupEncryptionStatusResp handleGetBackupEncryptionStatus() {
        return BackupEncryptionStatusResp.newBuilder()
                .setBackupEncryptionStatus(backupEncryptionStatusDiagnostics.isBackupEncryptionStatus())
                .addAllBackupEncryptionKeys(unmodifiableList(backupEncryptionStatusDiagnostics.getBackupEncryptionKeys()))
                .build();
    }

    private MessageLogEncryptionStatusResp handleMessageLogEncryptionStatus() {
        List<MessageLogArchiveEncryptionMember> members = messageLogEncryptionStatusDiagnostics.getMembers().stream()
                .map(member -> MessageLogArchiveEncryptionMember.newBuilder()
                        .setMemberId(member.getMemberId())
                        .addAllKeys(member.getKeys())
                        .setDefaultKeyUsed(member.isDefaultKeyUsed())
                        .build())
                .toList();

        return MessageLogEncryptionStatusResp.newBuilder()
                .setMessageLogArchiveEncryptionStatus(messageLogEncryptionStatusDiagnostics.isMessageLogArchiveEncryptionStatus())
                .setMessageLogDatabaseEncryptionStatus(messageLogEncryptionStatusDiagnostics.isMessageLogDatabaseEncryptionStatus())
                .setMessageLogGroupingRule(messageLogEncryptionStatusDiagnostics.getMessageLogGroupingRule())
                .addAllMembers(members)
                .build();
    }

    private Empty handleClearConfCache() {
        serverConfProvider.clearCache();
        return Empty.getDefaultInstance();
    }

}
