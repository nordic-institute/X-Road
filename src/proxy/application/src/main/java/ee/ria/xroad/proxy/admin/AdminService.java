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

package ee.ria.xroad.proxy.admin;

import ee.ria.xroad.common.AddOnStatusDiagnostics;
import ee.ria.xroad.common.BackupEncryptionStatusDiagnostics;

import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.niis.xroad.proxy.proto.AddOnStatusResp;
import org.niis.xroad.proxy.proto.AdminServiceGrpc;
import org.niis.xroad.proxy.proto.BackupEncryptionStatusResp;
import org.niis.xroad.proxy.proto.Empty;

import java.util.function.Supplier;

import static java.util.Collections.unmodifiableList;

@RequiredArgsConstructor
public class AdminService extends AdminServiceGrpc.AdminServiceImplBase {

    private final BackupEncryptionStatusDiagnostics backupEncryptionStatusDiagnostics;
    private final AddOnStatusDiagnostics addOnStatusDiagnostics;

    @Override
    public void getBackupEncryptionStatus(Empty request, StreamObserver<BackupEncryptionStatusResp> responseObserver) {
        handleRequest(responseObserver, this::handleGetBackupEncryptionStatus);
    }

    @Override
    public void getAddOnStatus(Empty request, StreamObserver<AddOnStatusResp> responseObserver) {
        handleRequest(responseObserver, this::handleAddOnStatus);
    }

    private <T> void handleRequest(StreamObserver<T> responseObserver, Supplier<T> handler) {
        try {
            responseObserver.onNext(handler.get());
        } catch (Exception e) {
            responseObserver.onError(e);
        }
        responseObserver.onCompleted();
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
}
