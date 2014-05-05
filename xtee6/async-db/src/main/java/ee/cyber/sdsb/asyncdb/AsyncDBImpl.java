package ee.cyber.sdsb.asyncdb;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.identifier.ClientId;

import static ee.cyber.sdsb.asyncdb.AsyncDBUtil.makeFile;
import static ee.cyber.sdsb.asyncdb.AsyncDBUtil.makePath;

class AsyncDBImpl implements AsyncDBProvider {
    private static final Logger LOG = LoggerFactory.getLogger(AsyncDBImpl.class);

    /** Directory name to message queue mapping */
    private static final Map<String, MessageQueue> messageQueues =
            new ConcurrentHashMap<>();

    @Override
    public List<MessageQueue> getMessageQueues() throws Exception {

        Callable<Void> task = new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                String dbDirPath = SystemProperties.getAsyncDBPath();
                String[] messageQueueDirs = AsyncDBUtil
                        .getDirectoriesList(new File(dbDirPath));

                if (messageQueueDirs == null) {
                    return null;
                }

                for (String queueName : messageQueueDirs) {
                    LOG.debug("Message queue dir to process: '{}'", queueName);

                    if (!messageQueues.containsKey(queueName)) {
                        String messageQueueDir = makePath(dbDirPath, queueName);
                        // In this case, we lock the directory for
                        // message queue. However, this JVM has not accessed
                        // this queue (because it is not in cache). Therefore
                        // we are the first to access the queue in this JVM
                        // and do not have to use JVM-level locking. And we
                        // already have the global lock for this JVM.
                        // The file-based locking (to synchronize with other
                        // JVMs) is sufficient in our case.
                        // We'll use new Object() as lockable object so that
                        // there will be no lock contention.
                        AsyncDBUtil.performLocked(
                                new AddQueueToCache(messageQueueDir, queueName),
                                makePath(messageQueueDir,
                                        MessageQueue.LOCK_FILE_NAME),
                                new Object());
                    }
                }
                return null;
            }

        };

        AsyncDBUtil.performLocked(task, AsyncDBUtil.getGlobalLockFilePath(),
                this);

        return new ArrayList<>(messageQueues.values());
    }

    @Override
    public synchronized MessageQueue getMessageQueue(ClientId provider)
            throws Exception {
        String queueName = AsyncDBUtil.getQueueName(provider);
        MessageQueue queue = messageQueues.get(queueName);

        if (queue == null) {
            queue = composeMessageQueue(provider);
            messageQueues.put(queueName, queue);
        }
        return queue;
    }

    private static MessageQueue composeMessageQueue(ClientId provider)
            throws Exception {
        return new MessageQueueImpl(provider, new AsyncLogWriterImpl(provider));
    }

    private static class AddQueueToCache implements Callable<Void> {
        private String messageQueueDir;
        private String queueName;

        public AddQueueToCache(String messageQueueDir, String queueName)
                throws IOException {
            this.messageQueueDir = messageQueueDir;
            this.queueName = queueName;
        }

        @Override
        public Void call() throws Exception {
            String metadataAsJson = FileUtils.readFileToString(
                    makeFile(messageQueueDir, MessageQueue.METADATA_FILE_NAME),
                    StandardCharsets.UTF_8);

            LOG.debug("Queue metadata as JSON: '{}'", metadataAsJson);

            QueueInfo queue = QueueInfo.fromJson(metadataAsJson);

            LOG.debug("Queue read from directory '{}': {}",
                    messageQueueDir, queue);

            messageQueues.put(queueName, composeMessageQueue(queue.getName()));
            return null;
        }
    }
}
