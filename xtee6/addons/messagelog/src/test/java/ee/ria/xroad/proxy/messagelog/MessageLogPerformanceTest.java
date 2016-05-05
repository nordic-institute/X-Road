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
package ee.ria.xroad.proxy.messagelog;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import ee.ria.xroad.common.hashchain.HashChainBuilder;
import ee.ria.xroad.common.messagelog.MessageLogProperties;

import static ee.ria.xroad.common.util.CryptoUtils.SHA256_ID;
import static ee.ria.xroad.proxy.messagelog.TestUtil.createMessage;
import static ee.ria.xroad.proxy.messagelog.TestUtil.createSignature;

/**
 * Messagelog performance test program.
 */
@Slf4j
public class MessageLogPerformanceTest extends AbstractMessageLogTest {

    // number of iterations
    private static final long NUM_ITERATIONS = 2; //3;

    // number of messages per logger
    private static final long NUM_MESSAGES = 500; //1234;

    // number of logger threads
    private static final long NUM_THREADS = 5; //5;

    private static int queryId;

    /**
     * Main program access point.
     * @param args command-line arguments
     * @throws Exception in case of any errors
     */
    public static void main(String[] args) throws Exception {
        log.info("Starting SecureLog performance test...");

        new MessageLogPerformanceTest().run();
    }

    void run() throws Exception {
        initHashChainBuilder();

        try {
            timestampAsynchronously();
            //timestampSynchronously();

            awaitTermination();
        } finally {
            testTearDown();
        }
    }

    private void timestampAsynchronously() throws Exception {
        testSetUp(false);

        for (int i = 0; i < NUM_ITERATIONS; i++) {
            for (int j = 0; j < NUM_THREADS; j++) {
                new Thread(new Logger(this)).start();
            }

            Thread.sleep(15000);
        }
    }

    private void timestampSynchronously() throws Exception {
        testSetUp(true);

        log(createMessage(nextQueryId()), createSignature());
        log(createMessage(nextQueryId()), createSignature());
    }

    @Override
    protected void testSetUp(boolean timestampImmediately) throws Exception {
        TestUtil.initForTest();

        System.setProperty(MessageLogProperties.ARCHIVE_PATH, "build/slog");
        System.setProperty(MessageLogProperties.ARCHIVE_MAX_FILESIZE,
                "2000000");

        super.testSetUp(timestampImmediately);

        initLogManager();
    }

    @RequiredArgsConstructor
    private static class Logger implements Runnable {

        private final MessageLogPerformanceTest test;

        @Override
        public void run() {
            for (int i = 0; i < NUM_MESSAGES; i++) {
                try {
                    test.log(createMessage(nextQueryId()), createSignature());
                } catch (Exception e) {
                    log.error("failed to log message {}: {}", i, e.toString());
                    return;
                }
            }
        }
    }

    private void initHashChainBuilder() throws Exception {
        new HashChainBuilder(SHA256_ID); // just to init JAXBContext...

        for (int iter = 0; iter < 100; iter++) {
            HashChainBuilder builder = new HashChainBuilder(SHA256_ID);

            for (int i = 0; i < 10; ++i) {
                builder.addInputHash(new byte[] {(byte) i });
            }

            builder.finishBuilding();
        }
    }

    private static synchronized String nextQueryId() {
        return "" + queryId++;
    }
}
