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

package org.niis.xroad.proxy.proto;

import ee.ria.xroad.common.AddOnStatusDiagnostics;
import ee.ria.xroad.common.BackupEncryptionStatusDiagnostics;
import ee.ria.xroad.common.DiagnosticsStatus;
import ee.ria.xroad.common.MessageLogArchiveEncryptionMember;
import ee.ria.xroad.common.MessageLogEncryptionStatusDiagnostics;
import ee.ria.xroad.common.SystemProperties;

import io.grpc.Channel;
import lombok.Getter;
import org.niis.xroad.common.rpc.client.RpcClient;
import org.springframework.beans.factory.DisposableBean;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.time.Instant.ofEpochMilli;
import static org.niis.xroad.restapi.util.FormatUtils.fromInstantToOffsetDateTime;

public class ProxyRpcClient implements DisposableBean {
    private final RpcClient<ProxyRpcExecutionContext> proxyRpcClient;

    public ProxyRpcClient() throws Exception {
        this.proxyRpcClient = RpcClient.newClient(SystemProperties.getGrpcInternalHost(),
                SystemProperties.getProxyGrpcPort(), ProxyRpcExecutionContext::new);
    }

    @Override
    public void destroy() {
        proxyRpcClient.shutdown();
    }

    public BackupEncryptionStatusDiagnostics getBackupEncryptionStatus() throws Exception {
        var response = proxyRpcClient.execute(ctx -> ctx.getAdminServiceBlockingStub()
                .getBackupEncryptionStatus(Empty.getDefaultInstance()));
        return new BackupEncryptionStatusDiagnostics(
                response.getBackupEncryptionStatus(),
                response.getBackupEncryptionKeysList());
    }

    public AddOnStatusDiagnostics getAddOnStatus() throws Exception {
        var response = proxyRpcClient.execute(ctx -> ctx.getAdminServiceBlockingStub()
                .getAddOnStatus(Empty.getDefaultInstance()));
        return new AddOnStatusDiagnostics(response.getMessageLogEnabled());
    }

    public MessageLogEncryptionStatusDiagnostics getMessageLogEncryptionStatus() throws Exception {
        var response = proxyRpcClient.execute(ctx -> ctx.getAdminServiceBlockingStub()
                .getMessageLogEncryptionStatus(Empty.getDefaultInstance()));

        List<MessageLogArchiveEncryptionMember> memberList = response.getMembersList().stream()
                .map(member -> new MessageLogArchiveEncryptionMember(
                        member.getMemberId(),
                        new HashSet<>(member.getKeysList()),
                        member.getDefaultKeyUsed()))
                .toList();

        return new MessageLogEncryptionStatusDiagnostics(
                response.getMessageLogArchiveEncryptionStatus(),
                response.getMessageLogDatabaseEncryptionStatus(),
                response.getMessageLogGroupingRule(),
                memberList
        );
    }

    public Map<String, DiagnosticsStatus> getTimestampingStatus() throws Exception {
        var statuses = proxyRpcClient.execute(ctx -> ctx.getAdminServiceBlockingStub()
                .getTimestampStatus(Empty.getDefaultInstance()));

        return statuses.getDiagnosticsStatusMap().entrySet()
                .stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> {
                    var val = entry.getValue();
                    return new ee.ria.xroad.common.DiagnosticsStatus(
                            val.getReturnCode(),
                            val.hasPrevUpdate() ? fromInstantToOffsetDateTime(ofEpochMilli(val.getPrevUpdate())) : null,
                            val.hasNextUpdate() ? fromInstantToOffsetDateTime(ofEpochMilli(val.getNextUpdate())) : null,
                            val.hasDescription() ? val.getDescription() : null
                    );
                }));
    }

    public void clearConfCache() throws Exception {
        proxyRpcClient.execute(ctx -> ctx.getAdminServiceBlockingStub()
                .clearConfCache(Empty.getDefaultInstance()));
    }

    public void triggerDsAssetUpdate() throws Exception {
        proxyRpcClient.execute(ctx -> ctx.getAdminServiceBlockingStub()
                .triggerDSAssetUpdate(Empty.getDefaultInstance()));
    }

    @Getter
    private static class ProxyRpcExecutionContext implements RpcClient.ExecutionContext {
        private final AdminServiceGrpc.AdminServiceBlockingStub adminServiceBlockingStub;

        ProxyRpcExecutionContext(Channel channel) {
            adminServiceBlockingStub = AdminServiceGrpc.newBlockingStub(channel).withWaitForReady();
        }
    }
}
