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
import ee.ria.xroad.common.DiagnosticsStatus;
import ee.ria.xroad.common.MessageLogArchiveEncryptionMember;
import ee.ria.xroad.common.MessageLogEncryptionStatusDiagnostics;
import ee.ria.xroad.common.ProxyMemory;
import ee.ria.xroad.common.util.CryptoUtils;

import com.google.protobuf.ByteString;
import io.grpc.ManagedChannel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.core.exception.ErrorCode;
import org.niis.xroad.common.core.exception.ErrorOrigin;
import org.niis.xroad.common.rpc.client.AbstractRpcClient;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.common.rpc.mapper.DiagnosticStatusMapper;
import org.niis.xroad.rpc.common.Empty;

import java.security.cert.X509Certificate;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.time.Instant.ofEpochMilli;
import static org.niis.xroad.restapi.util.FormatUtils.fromInstantToOffsetDateTime;

@Slf4j
@RequiredArgsConstructor
@ApplicationScoped
public class ProxyRpcClient extends AbstractRpcClient {
    private final RpcChannelFactory proxyRpcChannelFactory;
    private final ProxyRpcChannelProperties rpcChannelProperties;

    private ManagedChannel channel;
    private AdminServiceGrpc.AdminServiceBlockingStub adminServiceBlockingStub;
    private InternalTlsServiceGrpc.InternalTlsServiceBlockingStub internalTlsServiceBlockingStub;

    @Override
    public ErrorOrigin getRpcOrigin() {
        return ErrorOrigin.PROXY;
    }

    @PostConstruct
    public void init() {
        log.info("Initializing {} rpc client to {}:{}", getClass().getSimpleName(), rpcChannelProperties.host(),
                rpcChannelProperties.port());
        channel = proxyRpcChannelFactory.createChannel(rpcChannelProperties);

        adminServiceBlockingStub = AdminServiceGrpc.newBlockingStub(channel).withInterceptors().withWaitForReady();
        internalTlsServiceBlockingStub = InternalTlsServiceGrpc.newBlockingStub(channel).withInterceptors().withWaitForReady();
    }

    @Override
    @PreDestroy
    public void close() {
        if (channel != null) {
            channel.shutdown();
        }
    }

    public AddOnStatusDiagnostics getAddOnStatus() {
        var response = exec(() -> adminServiceBlockingStub
                .getAddOnStatus(Empty.getDefaultInstance()));
        return new AddOnStatusDiagnostics(response.getMessageLogEnabled(), response.getOpMonitoringEnabled());
    }

    public MessageLogEncryptionStatusDiagnostics getMessageLogEncryptionStatus() {
        var response = exec(() -> adminServiceBlockingStub
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

    public Map<String, DiagnosticsStatus> getTimestampingStatus() {
        var statuses = exec(() -> adminServiceBlockingStub
                .getTimestampStatus(Empty.getDefaultInstance()));

        return statuses.getDiagnosticsStatusMap().entrySet()
                .stream().collect(Collectors.toMap(Map.Entry::getKey, ProxyRpcClient::convertDiagnosticsStatus));
    }

    private static DiagnosticsStatus convertDiagnosticsStatus(Map.Entry<String, org.niis.xroad.rpc.common.DiagnosticsStatus> entry) {
        var val = entry.getValue();
        DiagnosticsStatus diagnosticsStatus = new DiagnosticsStatus(
                DiagnosticStatusMapper.mapStatus(val.getStatus()),
                val.hasPrevUpdate() ? fromInstantToOffsetDateTime(ofEpochMilli(val.getPrevUpdate())) : null,
                val.hasNextUpdate() ? fromInstantToOffsetDateTime(ofEpochMilli(val.getNextUpdate())) : null);
        if (val.hasDescription()) {
            diagnosticsStatus.setDescription(val.getDescription());
        }
        if (val.hasErrorCode()) {
            diagnosticsStatus.setErrorCode(ErrorCode.valueOf(val.getErrorCode()));
        }
        if (val.getErrorCodeMetadataCount() > 0) {
            diagnosticsStatus.setErrorCodeMetadata(val.getErrorCodeMetadataList());
        }
        return diagnosticsStatus;
    }

    public ProxyMemory getProxyMemoryStatus() {
        var response = exec(() -> adminServiceBlockingStub.getProxyMemoryStatus(Empty.getDefaultInstance()));
        return new ProxyMemory(response.getTotalMemory(),
                response.getFreeMemory(),
                response.getMaxMemory(),
                response.getUsedMemory(),
                response.hasThreshold() ? response.getThreshold() : null,
                response.getUsedPercent());
    }

    public void clearConfCache() {
        exec(() -> adminServiceBlockingStub.clearConfCache(Empty.getDefaultInstance()));
    }

    public void triggerDsAssetUpdate() {
        exec(() -> adminServiceBlockingStub.triggerDSAssetUpdate(Empty.getDefaultInstance()));
    }

    // Internal TLS management methods
    public X509Certificate getInternalTlsCertificate() {
        var response = exec(() -> internalTlsServiceBlockingStub.getInternalTlsCertificate(Empty.getDefaultInstance()));
        return CryptoUtils.readCertificate(response.getInternalTlsCertificate().toByteArray());
    }

    public X509Certificate generateInternalTlsKeyAndCertificate() {
        var response = exec(() -> internalTlsServiceBlockingStub.generateInternalTlsKeyAndCertificate(Empty.getDefaultInstance()));
        return CryptoUtils.readCertificate(response.getInternalTlsCertificate().toByteArray());
    }

    public byte[] generateInternalCsr(String distinguishedName) {
        var request = GenerateInternalCsrRequest.newBuilder()
                .setDistinguishedName(distinguishedName)
                .build();
        var response = exec(() -> internalTlsServiceBlockingStub.generateInternalTlsCsr(request));
        return response.getTlsCsr().toByteArray();
    }

    public X509Certificate importInternalTlsCertificate(byte[] certificateBytes) {
        var request = InternalTlsCertificateMessage.newBuilder()
                .setInternalTlsCertificate(ByteString.copyFrom(certificateBytes))
                .build();
        var response = exec(() -> internalTlsServiceBlockingStub.importInternalTlsCertificate(request));
        return CryptoUtils.readCertificate(response.getInternalTlsCertificate().toByteArray());
    }
}
