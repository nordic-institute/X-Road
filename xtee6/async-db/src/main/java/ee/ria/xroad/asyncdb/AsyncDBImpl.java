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
package ee.ria.xroad.asyncdb;

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

import ee.ria.xroad.asyncdb.messagequeue.MessageQueue;
import ee.ria.xroad.asyncdb.messagequeue.MessageQueueImpl;
import ee.ria.xroad.asyncdb.messagequeue.QueueInfo;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.identifier.ClientId;

import static ee.ria.xroad.asyncdb.AsyncDBUtil.makeFile;
import static ee.ria.xroad.asyncdb.AsyncDBUtil.makePath;

class AsyncDBImpl implements AsyncDBProvider {
    private static final Logger LOG = LoggerFactory.getLogger(AsyncDBImpl.class);

    /** Directory name to message queue mapping */
    private static final Map<String, MessageQueue> MESSAGE_QUEUES =
            new ConcurrentHashMap<>();

    @Override
    public List<MessageQueue> getMessageQueues() throws Exception {

        Callable<Void> task = () -> {
            String dbDirPath = SystemProperties.getAsyncDBPath();
            String[] messageQueueDirs = AsyncDBUtil
                    .getDirectoriesList(new File(dbDirPath));

            if (messageQueueDirs == null) {
                return null;
            }

            for (String queueName : messageQueueDirs) {
                LOG.debug("Message queue dir to process: '{}'", queueName);

                if (!MESSAGE_QUEUES.containsKey(queueName)) {
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
        };

        AsyncDBUtil.performLocked(task, AsyncDBUtil.getGlobalLockFilePath(),
                this);

        return new ArrayList<>(MESSAGE_QUEUES.values());
    }

    @Override
    public synchronized MessageQueue getMessageQueue(ClientId provider)
            throws Exception {
        String queueName = AsyncDBUtil.getQueueName(provider);
        MessageQueue queue = MESSAGE_QUEUES.get(queueName);

        if (queue == null) {
            queue = composeMessageQueue(provider);
            MESSAGE_QUEUES.put(queueName, queue);
        }
        return queue;
    }

    private static MessageQueue composeMessageQueue(ClientId provider)
            throws Exception {
        return new MessageQueueImpl(provider, new AsyncLogWriter(provider));
    }

    private static class AddQueueToCache implements Callable<Void> {
        private String messageQueueDir;
        private String queueName;

        AddQueueToCache(String messageQueueDir, String queueName)
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

            MESSAGE_QUEUES.put(queueName, composeMessageQueue(queue.getName()));
            return null;
        }
    }
}
