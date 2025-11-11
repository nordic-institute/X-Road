/*
 * The MIT License
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
package org.niis.xroad.messagelog.archiver.core;

import ee.ria.xroad.common.crypto.identifier.DigestAlgorithm;

import io.grpc.stub.StreamObserver;
import jakarta.annotation.Nonnull;
import lombok.extern.slf4j.Slf4j;
import org.niis.xroad.common.messagelog.archive.GroupingStrategy;
import org.niis.xroad.messagelog.archiver.core.config.LogArchiverExecutionProperties;
import org.niis.xroad.messagelog.archiver.proto.MessageLogArchivalRequest;
import org.niis.xroad.messagelog.archiver.proto.MessageLogArchivalResp;
import org.niis.xroad.messagelog.archiver.proto.MessageLogArchiverServiceGrpc;
import org.niis.xroad.messagelog.archiver.proto.MessageLogCleanupRequest;
import org.niis.xroad.messagelog.archiver.proto.MessageLogCleanupResp;
import org.niis.xroad.messagelog.archiver.proto.MessageLogConfig;
import org.niis.xroad.messagelog.archiver.proto.StringList;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class MessageLogArchiverService extends MessageLogArchiverServiceGrpc.MessageLogArchiverServiceImplBase {

    private static final long SHUTDOWN_TIMEOUT_SECONDS = 120;

    private final ExecutorService executorService = Executors.newSingleThreadExecutor(Thread.ofVirtual().factory());
    private final LogArchiver logArchiver;
    private final LogCleaner logCleaner;

    public MessageLogArchiverService(@Nonnull LogArchiver logArchiver, @Nonnull LogCleaner logCleaner) {
        this.logArchiver = logArchiver;
        this.logCleaner = logCleaner;
    }

    public void destroy() {
        log.info("Shutting down MessageLogArchiverService executor");
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("Executor did not terminate within {} seconds, forcing shutdown", SHUTDOWN_TIMEOUT_SECONDS);
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Shutdown interrupted, forcing shutdown", e);
            executorService.shutdownNow();
        }
    }

    @Override
    public void triggerArchival(MessageLogArchivalRequest request, StreamObserver<MessageLogArchivalResp> responseObserver) {
        log.info("Received archival trigger request");

        var executionProperties = mapToExecutionProperties(request.getMessageLogConfig());

        executorService.submit(() -> {
            try {
                log.info("Starting archival operation");
                logArchiver.execute(executionProperties);
                log.info("Archival operation completed successfully");

                var response = MessageLogArchivalResp.newBuilder()
                        .setSuccess(true)
                        .setMessage("Archival completed successfully")
                        .build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            } catch (Exception e) {
                log.error("Archival operation failed", e);

                var response = MessageLogArchivalResp.newBuilder()
                        .setSuccess(false)
                        .setMessage("Archival failed: " + e.getMessage())
                        .build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }
        });
    }

    @Override
    public void triggerCleanup(MessageLogCleanupRequest request, StreamObserver<MessageLogCleanupResp> responseObserver) {
        log.info("Received cleanup trigger request");

        var executionProperties = mapToExecutionProperties(request.getMessageLogConfig());

        executorService.submit(() -> {
            try {
                log.info("Starting cleanup operation");
                logCleaner.execute(executionProperties);
                log.info("Cleanup operation completed successfully");

                var response = MessageLogCleanupResp.newBuilder()
                        .setSuccess(true)
                        .setMessage("Cleanup completed successfully")
                        .setRecordsRemoved(0)
                        .build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            } catch (Exception e) {
                log.error("Cleanup operation failed", e);

                var response = MessageLogCleanupResp.newBuilder()
                        .setSuccess(false)
                        .setMessage("Cleanup failed: " + e.getMessage())
                        .setRecordsRemoved(0)
                        .build();
                responseObserver.onNext(response);
                responseObserver.onCompleted();
            }
        });
    }

    private LogArchiverExecutionProperties mapToExecutionProperties(MessageLogConfig config) {
        var archiveGroupingStrategy = parseGroupingStrategy(config.getArchiveGroupingStrategy());
        var digestAlgorithm = DigestAlgorithm.ofName(config.getDigestAlgorithm());
        var archiveGrouping = convertArchiveGrouping(config.getArchiveGroupingMap());

        var archiveEncryption = new LogArchiverExecutionProperties.ArchiveEncryptionProperties(
                config.getArchiveEncryptionEnabled(),
                optionalOf(config.hasArchiveEncryptionDefaultKeyId(), config::getArchiveEncryptionDefaultKeyId),
                archiveGroupingStrategy,
                archiveGrouping
        );

        var databaseEncryption = new LogArchiverExecutionProperties.DatabaseEncryptionProperties(
                config.getMessagelogEncryptionEnabled(),
                nullableOf(config.hasMessagelogKeystore(), config::getMessagelogKeystore),
                nullableOf(config.hasMessagelogKeystorePassword(), config::getMessagelogKeystorePassword),
                nullableOf(config.hasMessagelogKeyId(), config::getMessagelogKeyId)
        );

        return new LogArchiverExecutionProperties(
                archiveEncryption,
                databaseEncryption,
                config.getCleanTransactionBatchSize(),
                config.getCleanKeepRecordsFor(),
                config.getArchiveTransactionBatchSize(),
                config.getArchivePath(),
                nullableOf(config.hasArchiveTransferCommand(), config::getArchiveTransferCommand),
                digestAlgorithm,
                config.getArchiveMaxFilesize(),
                config.getTmpDir()
        );
    }

    private <T> Optional<T> optionalOf(boolean condition, java.util.function.Supplier<T> supplier) {
        return condition ? Optional.of(supplier.get()) : Optional.empty();
    }

    private <T> T nullableOf(boolean condition, java.util.function.Supplier<T> supplier) {
        return condition ? supplier.get() : null;
    }

    private GroupingStrategy parseGroupingStrategy(String strategy) {
        if (strategy == null || strategy.isEmpty()) {
            return GroupingStrategy.NONE;
        }
        try {
            return GroupingStrategy.valueOf(strategy.toUpperCase());
        } catch (IllegalArgumentException e) {
            log.warn("Invalid grouping strategy: {}, defaulting to NONE", strategy);
            return GroupingStrategy.NONE;
        }
    }

    private Map<String, Set<String>> convertArchiveGrouping(Map<String, StringList> protoMap) {
        return protoMap.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> Set.copyOf(entry.getValue().getValuesList())
                ));
    }
}
