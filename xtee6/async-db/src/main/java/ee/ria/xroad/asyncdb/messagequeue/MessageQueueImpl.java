/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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
package ee.ria.xroad.asyncdb.messagequeue;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;

import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.ria.xroad.asyncdb.AsyncDBUtil;
import ee.ria.xroad.asyncdb.AsyncLogWriter;
import ee.ria.xroad.asyncdb.SendingCtx;
import ee.ria.xroad.asyncdb.WritingCtx;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.message.SoapMessageConsumer;
import ee.ria.xroad.common.message.SoapMessageEncoder;

import static ee.ria.xroad.asyncdb.AsyncDBUtil.makeFile;
import static ee.ria.xroad.asyncdb.AsyncDBUtil.makePath;

/**
 * Implementation of the message queue.
 */
public class MessageQueueImpl implements MessageQueue {

    private static final Logger LOG = LoggerFactory
            .getLogger(MessageQueueImpl.class);

    private static final String CORRUPT_MSG_DIR_PREFIX = "CORRUPT_";
    public static final int RANDOM_DIR_NAME_LENGTH = 10;

    enum SingleRequestOperations {
        MARK_AS_REMOVED, RESTORE
    }

    private final String providerDirPath;
    private final ClientId provider;

    private final AsyncLogWriter logWriter;

    /**
     * Creates new message queue.
     *
     * @param provider - provider the queue belongs to
     * @param logWriter - component that is responsible of writing message log.
     * @throws Exception - thrown if creating queue fails.
     */
    public MessageQueueImpl(ClientId provider, AsyncLogWriter logWriter)
            throws Exception {
        this.provider = provider;

        this.providerDirPath = makePath(SystemProperties.getAsyncDBPath(),
                AsyncDBUtil.getQueueName(provider));
        this.logWriter = logWriter;

        if (providerDirPath == null) {
            throw new IllegalStateException(
                    "Provider directory path cannot be null.");
        }

        String providerSavedStatus = new File(providerDirPath).exists()
                ? "SAVED" : "NOT SAVED";

        LOG.debug("Created provider with directory path '{}', provider is {}.",
                providerDirPath, providerSavedStatus);
    }

    class StartWritingOperation implements Callable<WritingCtx> {
        @Override
        public WritingCtx call() throws Exception {
            return new WritingCtxImpl(loadQueueInfo());
        }

    }

    @Override
    public WritingCtx startWriting() throws Exception {
        File providerDir = new File(providerDirPath);

        WritingCtx writingCtx;

        if (!providerDir.exists()) {
            QueueInfo initialQueueInfo = QueueInfo.getNew(provider);
            createQueueBranch(initialQueueInfo);
            writingCtx = new WritingCtxImpl(initialQueueInfo);
        } else {
            StartWritingOperation operation = new StartWritingOperation();
            writingCtx = performLocked(operation);
        }

        LOG.info(
                "Starting writing for provider '{}' with following writing ctx: '{}'",
                provider, writingCtx);

        return writingCtx;
    }

    class StartSendingOperation implements Callable<SendingCtx> {
        @Override
        public SendingCtx call() throws Exception {
            QueueInfo queueInfo = loadQueueInfo();

            for (;;) {
                if (queueInfo.getRequestCount() == 0) {
                    return null;
                }

                RequestInfo firstRequestInfo = loadExistingRequest(
                        queueInfo.getFirstRequestNo());

                if (firstRequestInfo.getRemovedTime() != null) {
                    FileUtils.deleteDirectory(new File(
                            getFirstRequestDirPath(queueInfo)));
                    queueInfo = QueueInfo.removeFirstRequest(
                            queueInfo, queueInfo.getLastSuccessId());

                    saveQueueInfo(queueInfo);

                    logWriter.appendToLog(
                            firstRequestInfo,
                            "Removed from queue",
                            queueInfo.getFirstRequestSendCount());
                } else {
                    break;
                }
            }

            return new SendingCtxImpl(loadQueueInfo());
        }
    }

    @Override
    public SendingCtx startSending() throws Exception {

        StartSendingOperation operation = new StartSendingOperation();


        SendingCtx sendingCtx = performLocked(operation);
        LOG.info("Starting sending for provider '{}' with following sending ctx: '{}'",
                provider, sendingCtx);

        return sendingCtx;
    }

    @Override
    public void markAsRemoved(String requestId) throws Exception {
        LOG.info("Marking request for provider '{}' with id '{}' as removed",
                provider, requestId);

        performSingleRequestOperation(requestId,
                SingleRequestOperations.MARK_AS_REMOVED);
    }

    @Override
    public void restore(String requestId) throws Exception {
        LOG.info("Restoring request for provider '{}' with id '{}'",
                provider, requestId);

        performSingleRequestOperation(requestId,
                SingleRequestOperations.RESTORE);
    }

    @Override
    public void resetCount() throws Exception {
        LOG.info("Resetting request count for provider '{}'", provider);

        performLocked(() -> {
            QueueInfo initial = loadQueueInfo();
            QueueInfo reset = QueueInfo.resetSendCount(initial);
            saveQueueInfo(reset);
            return null;
        });
    }

    @Override
    public QueueInfo getQueueInfo() throws Exception {
        LOG.info("Getting queue info for provider '{}' ", provider);

        QueueInfo queueInfo = loadQueueInfo();
        LOG.info("Got queue info for provider '{}': '{}'", provider,
                queueInfo);

        return queueInfo;
    }

    @Override
    public List<RequestInfo> getRequests() throws Exception {
        LOG.info("Getting requests for provider '{}' ", provider);

        List<RequestInfo> requests = new ArrayList<>();
        String[] requestDirectories = AsyncDBUtil.getDirectoriesList(new File(
                providerDirPath));

        if (requestDirectories == null) {
            throw new IllegalStateException(
                    "Request directories must be present when requests are read!");
        }

        List<Integer> requestDirectoryNumbers = new ArrayList<>(
                requestDirectories.length);

        for (String requestDirectory : requestDirectories) {
            if (!StringUtils.isNumeric(requestDirectory)) {
                continue;
            }

            requestDirectoryNumbers.add(Integer.parseInt(requestDirectory));
        }

        Collections.sort(requestDirectoryNumbers);

        for (Integer requestDirectoryNo : requestDirectoryNumbers) {
            requests.add(loadExistingRequest(requestDirectoryNo));
        }

        LOG.info("Got requests for provider '{}': '{}'", provider, requests);
        return requests;
    }

    private <T> T performLocked(Callable<T> task) throws Exception {
        return performLocked(task, this);
    }

    private <T> T performLocked(Callable<T> task, Object lockable)
            throws Exception {
        String lockFilePath = makePath(providerDirPath, LOCK_FILE_NAME);
        return AsyncDBUtil.performLocked(task, lockFilePath, lockable);
    }

    private QueueInfo loadQueueInfo() throws Exception {

        String json;
        try (InputStream is = new FileInputStream(
                getMetadataPath(providerDirPath))) {
            json = IOUtils.toString(is, StandardCharsets.UTF_8);
        }

        LOG.debug("Loaded queue info as json: '{}'", json);

        return QueueInfo.fromJson(json);
    }

    /**
     * Creates queue branch with metainfo directory in the filesystem.
     */
    private synchronized void createQueueBranch(final QueueInfo queueInfo)
            throws Exception {

        final File queueDir = new File(providerDirPath);

        File lockFile = new File(AsyncDBUtil.getGlobalLockFilePath());

        LOG.info("Creating directory for provider {} ({})",
                queueInfo.getName(), providerDirPath);

        // Global lock file must be present in order to create queues safely
        // under the lock
        if (!lockFile.exists()) {
            FileUtils.touch(lockFile);
        }

        if (!queueDir.exists()) {
            Callable<Object> queueDirCreationTask = () -> {
                queueDir.mkdirs();
                saveQueueInfo(queueInfo);
                // We use separate file for locking because
                // continuous overwriting of the metadata
                // file clears the locked file regions.
                FileUtils.touch(makeFile(providerDirPath, LOCK_FILE_NAME));
                return null;
            };

            AsyncDBUtil.performLocked(queueDirCreationTask,
                    AsyncDBUtil.getGlobalLockFilePath(), this);
        }
    }

    private void saveQueueInfo(QueueInfo queueInfo)
            throws IOException {
        String providerMetadataPath = getMetadataPath(providerDirPath);

        try (OutputStream os = new FileOutputStream(
                providerMetadataPath)) {

            LOG.debug("Saving queue info as JSON: '{}'", queueInfo.toJson());

            IOUtils.write(queueInfo.toJson(), os, StandardCharsets.UTF_8);
        } catch (IOException e) {
            String msg = String.format(
                    "Saving queue info for provider '%s' failed,"
                            + "check file '%s' for consistency",
                    provider.toShortString(),
                    providerMetadataPath);
            LOG.error(msg, e);
            throw e;
        }
    }

    private void saveRequestInfo(RequestInfo requestInfo,
            String requestDirPath) throws IOException {
        String requestMetadataPath = getMetadataPath(requestDirPath);

        try (OutputStream os = new FileOutputStream(
                requestMetadataPath)) {
            IOUtils.write(requestInfo.toJson(), os, StandardCharsets.UTF_8);
        } catch (IOException e) {
            String msg = String.format(
                    "Saving request info for provider '%s' failed,"
                            + "check file '%s' for consistency",
                    provider.toShortString(),
                    requestMetadataPath);
            LOG.error(msg, e);
            throw e;
        }
    }

    /**
     * Loads requests by directory path (can be either in temp requests
     * directory or directly under queue directory.
     */
    private RequestInfo loadRequest(String requestDirPath)
            throws Exception {
        String json;
        try (InputStream is = new FileInputStream(
                getMetadataPath(requestDirPath))) {
            json = IOUtils.toString(is, StandardCharsets.UTF_8);
        }

        LOG.debug("Request info loaded as JSON string: '{}'", json);

        try {
            return RequestInfo.fromJson(json);
        } catch (CorruptQueueException e) {
            LOG.error("Load request failed: {}", e);
            return recoverFromRequestCorruption();
        }
    }

    /**
     * Applies only to requests that are saved directly under queue directory.
     */
    private RequestInfo loadExistingRequest(int firstRequestNo)
            throws Exception {
        return loadRequest(providerDirPath + File.separator
                + firstRequestNo);
    }

    private RequestInfo recoverFromRequestCorruption() throws Exception {
        QueueInfo originalQueue = loadQueueInfo();

        int originalFirstRequestNo = originalQueue.getFirstRequestNo();

        String corruptionErrorMsg = String.format(
                "Request no '%d' of provider '%s' is corrupt, for details"
                        + " check contents of folder '%s'",
                originalFirstRequestNo,
                provider,
                providerDirPath
                );
        LOG.error(corruptionErrorMsg);

        QueueInfo corruptRequestRemoved =
                QueueInfo.removeCorruptRequest(originalQueue);

        saveQueueInfo(corruptRequestRemoved);

        if (corruptRequestRemoved.getRequestCount() == 0) {
            String outOfMessagesErrorMsg = String.format(
                    "There are only corrupt requests in the queue '%s', "
                            + "check directory '%s' for consistency.",
                    provider, providerDirPath);
            throw new CorruptQueueException(outOfMessagesErrorMsg);
        }

        markRequestDirAsCorrupt(originalFirstRequestNo);

        return loadExistingRequest(corruptRequestRemoved
                .getFirstRequestNo());
    }

    private void markRequestDirAsCorrupt(int corruptRequestNo) {
        File oldRequestDir =
                new File(providerDirPath + File.separator
                        + corruptRequestNo);

        File newRequestDir =
                new File(providerDirPath + File.separator
                        + CORRUPT_MSG_DIR_PREFIX + corruptRequestNo);

        oldRequestDir.renameTo(newRequestDir);
    }

    private static String createTempDir(File root) {
        boolean dirCreated = false;
        String tempDirPath = makePath(root.getAbsolutePath(),
                getRandomDirName());

        if (tempDirPath == null) {
            throw new RuntimeException(
                    "Path of temporary directory must exist!");
        }

        if (new File(tempDirPath).exists()) {
            throw new RuntimeException("Temp directory with path '"
                    + tempDirPath
                    + "' already exists, cannot create new one.");
        }

        while (!dirCreated) {
            try {
                LOG.debug("Creating temp directory onto path '{}'",
                        tempDirPath);
                dirCreated = new File(tempDirPath).mkdirs();
            } catch (Exception e) {
                LOG.warn("Could not create temp directory, reason: '{}'",
                        e);
            }
        }
        return tempDirPath;
    }

    private static String getMessageFilePath(String requestDirPath) {
        return makePath(requestDirPath, MESSAGE_FILE_NAME);
    }

    private static String getMetadataPath(String rootDirPath) {
        return makePath(rootDirPath, METADATA_FILE_NAME);
    }

    private static String getContentTypePath(String requestDirPath) {
        return makePath(requestDirPath, CONTENT_TYPE_FILE_NAME);
    }

    private void performSingleRequestOperation(final String requestId,
            final SingleRequestOperations operation)
            throws Exception {

        performLocked(() -> {
            for (RequestInfo initialRequestInfo : getRequests()) {
                if (StringUtils.equals(requestId,
                        initialRequestInfo.getId())) {
                    processAndSaveRequestInfo(operation, initialRequestInfo);
                    return null;
                }
            }

            throw new RuntimeException("No request with id '" + requestId
                    + "' found for provider '" + provider + "'.");
        });
    }

    private void processAndSaveRequestInfo(
            final SingleRequestOperations operation,
            RequestInfo initialRequestInfo) throws IOException {
        String requestDirPath = providerDirPath + File.separator
                + initialRequestInfo.getOrderNo();

        RequestInfo processed;

        switch (operation) {
            case MARK_AS_REMOVED:
                processed = RequestInfo.markAsRemoved(initialRequestInfo);
                break;
            case RESTORE:
                processed = RequestInfo.restore(initialRequestInfo);
                break;
            default:
                throw new IllegalArgumentException(
                        "Operation '" + operation + "' is not supported.");
        }
        saveRequestInfo(processed, requestDirPath);
    }

    private String getFirstRequestDirPath(QueueInfo initialQueueInfo) {
        return providerDirPath + File.separator
                + initialQueueInfo.getFirstRequestNo();
    }

    private class WritingCtxImpl implements WritingCtx {
        private String tempRequestsRootPath;

        private SoapMessageEncoder encoder;
        private AsyncDBSoapMessageConsumer consumer;
        private QueueInfo initialQueueInfo;

        WritingCtxImpl(QueueInfo initialQueueInfo)
                throws Exception {
            this.tempRequestsRootPath = createTempDir(new File(providerDirPath));

            OutputStream messageFile = new FileOutputStream(
                    getMessageFilePath(tempRequestsRootPath));
            this.encoder = new SoapMessageEncoder(messageFile);
            this.consumer = new AsyncDBSoapMessageConsumer(encoder);
            this.initialQueueInfo = initialQueueInfo;
        }

        @Override
        public SoapMessageConsumer getConsumer() {
            return consumer;
        }

        @Override
        public void commit() throws Exception {
            LOG.info("Committing DB writing operation...");
            encoder.close();

            performLocked(() -> {
                QueueInfo queueInfo = getQueueInfo();

                int nextRequestDirNo = queueInfo.getNextRequestNo();

                RequestInfo requestInfo = RequestInfo.getNew(
                        nextRequestDirNo, consumer.getMessage());

                saveRequestInfo(requestInfo, tempRequestsRootPath);

                String contentTypePath = getContentTypePath(tempRequestsRootPath);

                FileUtils.writeStringToFile(new File(contentTypePath),
                        encoder.getContentType(), StandardCharsets.UTF_8);

                String nextRequestDirPath = providerDirPath
                        + File.separator + nextRequestDirNo;

                LOG.debug("Starting to commit directory tree changes");

                commitDirTreeChanges(nextRequestDirPath);

                LOG.debug("Directory tree changes committed "
                        + "successfully, about to save queue info");

                saveQueueInfo(QueueInfo.addRequest(queueInfo));
                return null;
            }, MessageQueueImpl.this);
            LOG.info("Successfully committed DB writing operation...");
        }

        @Override
        public void rollback() throws Exception {
            LOG.info("Starting to roll back file tree changes");

            encoder.close();

            performLocked(() -> {
                saveQueueInfo(initialQueueInfo);
                return null;
            }, MessageQueueImpl.this);
            FileUtils.deleteDirectory(new File(tempRequestsRootPath));

            LOG.info("File tree changes rolled back successfully");
        }

        private void commitDirTreeChanges(String nextRequestDirPath)
                throws IOException {
            try {
                Path srcPath = FileSystems.getDefault().getPath(
                        tempRequestsRootPath);
                Path destPath = FileSystems.getDefault().getPath(
                        nextRequestDirPath);
                Files.move(srcPath, destPath, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                LOG.error("Committing directory tree changes failed: ", e);
                FileUtils.deleteDirectory(new File(tempRequestsRootPath));
                throw new IOException(
                        "Committing provider tree changes failed: ", e);
            }
        }

        @Override
        public String toString() {
            return ToStringBuilder.reflectionToString(this,
                    ToStringStyle.MULTI_LINE_STYLE);
        }
    }

    private final class SendingCtxImpl implements SendingCtx {
        private InputStream inputStream;
        private String contentType;
        private String requestId;

        private SendingCtxImpl(QueueInfo queueInfo) throws Exception {
            int firstRequestNo = queueInfo.getFirstRequestNo();
            LOG.info("Creating sending ctx with first request no '{}'",
                    firstRequestNo);

            String firstRequestDirPath = getFirstRequestDirPath(queueInfo);

            this.inputStream = new FileInputStream(
                    getMessageFilePath(firstRequestDirPath));

            RequestInfo initial = loadRequest(firstRequestDirPath);

            this.requestId = initial.getId();

            if (!initial.isSending()) {
                RequestInfo markedSending = RequestInfo.markSending(initial);
                saveRequestInfo(markedSending, firstRequestDirPath);
            }

            this.contentType = FileUtils.readFileToString(new File(
                    getContentTypePath(firstRequestDirPath)),
                    StandardCharsets.UTF_8);
        }

        @Override
        public InputStream getInputStream() {
            return inputStream;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public void success(final String lastSendResult) throws Exception {
            performLocked(() -> {
                inputStream.close();

                QueueInfo initialQueueInfo = getQueueInfo();

                String firstRequestDirPath = getFirstRequestDirPath(initialQueueInfo);

                RequestInfo firstRequestInfo = getAndValidateRequest(
                        firstRequestDirPath);

                QueueInfo updatedQueueInfo = QueueInfo.removeFirstRequest(
                        initialQueueInfo, firstRequestInfo.getId(),
                        lastSendResult);

                saveQueueInfo(updatedQueueInfo);

                FileUtils.deleteDirectory(new File(firstRequestDirPath));

                logWriter.appendToLog(firstRequestInfo, "OK",
                        initialQueueInfo.getFirstRequestSendCount());
                return null;
            }, MessageQueueImpl.this);
        }


        @Override
        public void failure(final String fault, final String lastSendResult)
                throws Exception {
            performLocked(() -> {
                inputStream.close();

                QueueInfo initialQueueInfo = getQueueInfo();

                String firstRequestDirPath = getFirstRequestDirPath(initialQueueInfo);

                RequestInfo firstRequestInfo = getAndValidateRequest(
                        firstRequestDirPath);

                QueueInfo updatedQueueInfo = QueueInfo.handleFailedRequest(
                        initialQueueInfo, lastSendResult);

                saveQueueInfo(updatedQueueInfo);

                RequestInfo unmarkedSending = RequestInfo
                        .unmarkSending(firstRequestInfo);

                saveRequestInfo(unmarkedSending, firstRequestDirPath);

                logWriter.appendToLog(firstRequestInfo, fault,
                        initialQueueInfo.getFirstRequestSendCount());
                return null;
            }, MessageQueueImpl.this);
        }

        private RequestInfo getAndValidateRequest(String firstRequestDirPath)
                throws Exception {
            RequestInfo firstRequestInfo = loadRequest(firstRequestDirPath);

            String firstRequestId = firstRequestInfo.getId();

            if (!StringUtils.equals(firstRequestId, requestId)) {
                String errorMsg = String.format(
                        "The first request ID was supposed to be '%s', "
                                + "but was actually '%s'.",
                        requestId, firstRequestId);
                throw new IllegalStateException(errorMsg);
            }
            return firstRequestInfo;
        }

        @Override
        public String toString() {
            return ToStringBuilder.reflectionToString(this,
                    ToStringStyle.MULTI_LINE_STYLE);
        }
    }

    private static String getRandomDirName() {
        return "." + RandomStringUtils.randomAlphanumeric(
                RANDOM_DIR_NAME_LENGTH);
    }

    @Override
    public String toString() {
        return ToStringBuilder.reflectionToString(this,
                ToStringStyle.MULTI_LINE_STYLE);
    }
}
