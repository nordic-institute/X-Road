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

import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.ria.xroad.asyncdb.messagequeue.MessageQueue;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.util.SystemMetrics;

/**
 * This test is intended to assure that async-db uses memory effectively. All
 * files that are to be opened must be closed as well.
 */
public final class AsyncDBMemoryUsageTest {
    private AsyncDBMemoryUsageTest() {
    }

    private static final Logger LOG = LoggerFactory
            .getLogger(AsyncDBMemoryUsageTest.class);
    private static final MessageQueue QUEUE;

    static {
        AsyncDBTestUtil.setTestenvProps();
        ClientId provider = ClientId.create("ee", "member",
                AsyncDBTestUtil.getProviderName());
        try {
            QUEUE = AsyncDB.getMessageQueue(provider);
        } catch (Exception e) {
            LOG.error("Could not create message QUEUE for service '{}'",
                    provider, e);
            throw new RuntimeException(e);
        }
    }
    private static final int ITERATIONS = 10;

    /**
     * @param args - arguments of main method, here not used.
     * @throws Exception - thrown when memory usage test fails.
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

                WritingCtx writingCtx = QUEUE.startWriting();
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

            if (logDir != null) {
                FileUtils.deleteDirectory(logDir);
            }
        }
    }

}
