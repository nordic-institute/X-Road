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

package org.niis.xroad.backupmanager.proto;

import ee.ria.xroad.common.CodedException;

import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import io.grpc.ManagedChannel;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.exception.BadRequestException;
import org.niis.xroad.common.exception.InternalServerErrorException;
import org.niis.xroad.common.exception.NotFoundException;
import org.niis.xroad.common.rpc.client.AbstractRpcClient;
import org.niis.xroad.common.rpc.client.RpcChannelFactory;
import org.niis.xroad.restapi.exceptions.DeviationAwareException;
import org.niis.xroad.restapi.exceptions.DeviationAwareRuntimeException;
import org.niis.xroad.restapi.exceptions.ErrorDeviation;
import org.niis.xroad.restapi.exceptions.WarningDeviation;
import org.niis.xroad.rpc.common.Empty;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

import static org.niis.xroad.common.exception.util.CommonDeviationMessage.BACKUP_DELETION_FAILED;
import static org.niis.xroad.common.exception.util.CommonDeviationMessage.BACKUP_FILE_NOT_FOUND;
import static org.niis.xroad.common.exception.util.CommonDeviationMessage.BACKUP_GENERATION_FAILED;
import static org.niis.xroad.common.exception.util.CommonDeviationMessage.BACKUP_GENERATION_INTERRUPTED;
import static org.niis.xroad.common.exception.util.CommonDeviationMessage.BACKUP_RESTORATION_FAILED;
import static org.niis.xroad.common.exception.util.CommonDeviationMessage.BACKUP_RESTORATION_INTERRUPTED;
import static org.niis.xroad.common.exception.util.CommonDeviationMessage.INVALID_BACKUP_FILE;
import static org.niis.xroad.common.exception.util.CommonDeviationMessage.INVALID_FILENAME;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.ERROR_WARNINGS_DETECTED;
import static org.niis.xroad.restapi.exceptions.DeviationCodes.WARNING_FILE_ALREADY_EXISTS;

@Slf4j
@RequiredArgsConstructor
@ApplicationScoped
public class BackupManagerRpcClient extends AbstractRpcClient {

    private final RpcChannelFactory rpcChannelFactory;
    private final BackupManagerRpcChannelProperties rpcChannelProperties;

    private ManagedChannel channel;
    private BackupServiceGrpc.BackupServiceBlockingStub backupServiceBlockingStub;

    @PostConstruct
    public void init() throws Exception {
        log.info("Initializing {} rpc client to {}:{}", getClass().getSimpleName(), rpcChannelProperties.host(),
                rpcChannelProperties.port());
        channel = rpcChannelFactory.createChannel(rpcChannelProperties);

        this.backupServiceBlockingStub = BackupServiceGrpc.newBlockingStub(channel).withWaitForReady();
    }

    @Override
    @PreDestroy
    public void close() {
        if (channel != null) {
            channel.shutdown();
        }
    }

    public Collection<BackupInfo> listBackups() {
        try {
            var response = exec(() -> backupServiceBlockingStub.listBackups(Empty.getDefaultInstance()));
            return response.getBackupItemsList().stream()
                    .map(item -> new BackupInfo(item.getName(), toInstant(item.getCreatedAt())))
                    .toList();
        } catch (Exception e) {
            throw new InternalServerErrorException("Failed to list backups", e);
        }
    }

    public void deleteBackup(String name) {
        try {
            exec(() -> backupServiceBlockingStub.deleteBackup(DeleteBackupReq.newBuilder().setBackupName(name).build()));
        } catch (CodedException ce) {
            throw mapException(ce, new InternalServerErrorException(ce, BACKUP_DELETION_FAILED.build(ce.getFaultString())));
        } catch (Exception e) {
            throw new InternalServerErrorException(e, BACKUP_DELETION_FAILED.build(name));
        }
    }

    public byte[] downloadBackup(String name) {
        try {
            var response = exec(
                    () -> backupServiceBlockingStub.downloadBackup(DownloadBackupReq.newBuilder()
                            .setBackupName(name)
                            .build()));
            return response.getBackupFile().toByteArray();
        } catch (CodedException ce) {
            throw mapException(ce, new NotFoundException(BACKUP_FILE_NOT_FOUND.build(ce.getFaultString())));
        } catch (Exception e) {
            throw new InternalServerErrorException("Failed to download backup", e);
        }
    }

    public BackupInfo uploadBackup(String name, byte[] data, boolean ignoreWarnings) {
        try {
            var response = exec(() -> backupServiceBlockingStub
                    .uploadBackup(UploadBackupReq.newBuilder()
                            .setBackupName(name)
                            .setBackupFile(ByteString.copyFrom(data))
                            .setIgnoreWarnings(ignoreWarnings)
                            .build()));

            return new BackupInfo(response.getName(), toInstant(response.getCreatedAt()));
        } catch (CodedException ce) {
            if (ce.getFaultCode().equals(WARNING_FILE_ALREADY_EXISTS)) {
                throw new BadRequestException(new DeviationAwareException("Warnings detected",
                        new ErrorDeviation(ERROR_WARNINGS_DETECTED), List.of(new WarningDeviation(WARNING_FILE_ALREADY_EXISTS, name))));
            }
            throw mapException(ce, new InternalServerErrorException("Failed to upload backup", ce));
        } catch (Exception e) {
            throw new InternalServerErrorException("Failed to upload backup", e);
        }
    }

    public BackupInfo createBackup(String securityServerId) {
        try {
            CreateBackupReq request = CreateBackupReq.newBuilder()
                    .setSecurityServerId(securityServerId)
                    .build();

            var response = exec(() -> backupServiceBlockingStub.createBackup(request));
            return new BackupInfo(response.getName(), toInstant(response.getCreatedAt()));
        } catch (CodedException ce) {
            throw mapException(ce, new InternalServerErrorException(ce, BACKUP_GENERATION_FAILED.build()));
        } catch (Exception e) {
            throw new InternalServerErrorException("Failed to create backup", e);
        }
    }

    public void restoreFromBackup(String name, String securityServerId) {
        try {
            RestoreBackupReq request = RestoreBackupReq.newBuilder()
                    .setBackupName(name)
                    .setSecurityServerId(securityServerId)
                    .build();
            exec(() -> backupServiceBlockingStub.restoreFromBackup(request));
        } catch (CodedException ce) {
            throw mapException(ce, new InternalServerErrorException(ce, BACKUP_RESTORATION_FAILED.build()));
        } catch (Exception e) {
            throw new InternalServerErrorException(e, BACKUP_RESTORATION_FAILED.build());
        }
    }

    private DeviationAwareRuntimeException mapException(CodedException ce, DeviationAwareRuntimeException defaultEx) {
        if (ce.getFaultCode().equals(INVALID_FILENAME.code())) {
            return new BadRequestException(INVALID_FILENAME.build(ce.getFaultString()));
        } else if (ce.getFaultCode().equals(INVALID_BACKUP_FILE.code())) {
            throw new InternalServerErrorException(INVALID_BACKUP_FILE.build(ce.getFaultString()));
        } else if (ce.getFaultCode().equals(BACKUP_FILE_NOT_FOUND.code())) {
            throw new NotFoundException(BACKUP_FILE_NOT_FOUND.build(ce.getFaultString()));
        } else if (ce.getFaultCode().equals(BACKUP_GENERATION_FAILED.code())) {
            return new InternalServerErrorException(BACKUP_GENERATION_FAILED.build());
        } else if (ce.getFaultCode().equals(BACKUP_GENERATION_INTERRUPTED.code())) {
            return new InternalServerErrorException(BACKUP_GENERATION_INTERRUPTED.build());
        } else if (ce.getFaultCode().equals(BACKUP_RESTORATION_FAILED.code())) {
            return new InternalServerErrorException(BACKUP_RESTORATION_FAILED.build());
        } else if (ce.getFaultCode().equals(BACKUP_RESTORATION_INTERRUPTED.code())) {
            return new InternalServerErrorException(BACKUP_RESTORATION_INTERRUPTED.build());
        } else if (ce.getFaultCode().equals(BACKUP_DELETION_FAILED.code())) {
            throw new NotFoundException(BACKUP_DELETION_FAILED.build(ce.getFaultString()));
        } else {
            return defaultEx;
        }
    }

    private Instant toInstant(Timestamp timestamp) {
        return Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
    }

}
