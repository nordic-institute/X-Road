package ee.cyber.sdsb.asyncdb;

import java.io.File;

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.message.SoapMessageImpl;
import ee.cyber.sdsb.common.util.SystemMetrics;

/**
 * This test is intended to assure that async-db uses memory effectively. All
 * files that are to be opened must be closed as well.
 */
public class AsyncDBMemoryUsageTest {

    private static final Logger LOG = LoggerFactory
            .getLogger(AsyncDBMemoryUsageTest.class);
    private static final MessageQueue queue;

    static {
        AsyncDBTestUtil.setTestenvProps();
        ClientId provider = ClientId.create("ee", "member",
                AsyncDBTestUtil.getProviderName());
        try {
            queue = AsyncDB.getMessageQueue(provider);
        } catch (Exception e) {
            LOG.error("Could not create message queue for service '{}'",
                    provider, e);
            throw new RuntimeException(e);
        }
    }
    private static final int ITERATIONS = 10;

    /**
     * @param args
     * @throws Exception
     */
    public static void main(String[] args) throws Exception {
        File logDir = null;

        try {
            SoapMessageImpl requestMessage = AsyncDBTestUtil.getFirstSoapRequest();
            File logFile = new File(AsyncDBTestUtil.getAsyncLogFilePath());
            logDir = logFile.getParentFile();

            long previousFreeFileDescriptorCount = 0;
            boolean first = true;

            for (int i = 0; i < ITERATIONS; i++) {
                LOG.info("Adding request number {}...", i);

                WritingCtx writingCtx = queue.startWriting();
                writingCtx.getConsumer().soap(requestMessage);
                writingCtx.commit();

                long freeFileDescriptorCount = SystemMetrics
                        .getFreeFileDescriptorCount();
                LOG.info("Free file descriptor count: {}",
                        freeFileDescriptorCount);

                if (!first
                        && freeFileDescriptorCount < previousFreeFileDescriptorCount) {
                    throw new RuntimeException(
                            "File descriptor count must not increase as requests are added!");
                }

                previousFreeFileDescriptorCount = freeFileDescriptorCount;
                first = false;
            }

            LOG.info("Async DB memory usage test accomplished successfully");
        } finally {
            FileUtils.deleteDirectory(new File(AsyncDBTestUtil
                    .getProviderDirPath()));
            FileUtils.deleteDirectory(logDir);
        }
    }

}
