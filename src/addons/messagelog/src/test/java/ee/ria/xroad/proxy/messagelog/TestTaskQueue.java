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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import ee.ria.xroad.proxy.messagelog.Timestamper.TimestampFailed;
import ee.ria.xroad.proxy.messagelog.Timestamper.TimestampSucceeded;
import lombok.extern.slf4j.Slf4j;

@Slf4j
class TestTaskQueue extends TaskQueue {

    private static CountDownLatch gate = new CountDownLatch(1);
    private static Object lastMessage;

    TestTaskQueue() {
        super();
    }

    public static void waitForMessage() throws Exception {
        try {
            boolean result = gate.await(5, TimeUnit.SECONDS);
        } finally {
            gate = new CountDownLatch(1);
        }
    }

    public static Object getLastMessage() {
        return lastMessage;
    }

    @Override
    protected void handleTimestampSucceeded(TimestampSucceeded message) {
        log.info("handleTimestampSucceeded");
        super.handleTimestampSucceeded(message);

        lastMessage = message;
        gate.countDown();
    }

    @Override
    protected void handleTimestampFailed(TimestampFailed message) {
        log.info("handleTimestampSucceeded");
        super.handleTimestampFailed(message);

        lastMessage = message;
        gate.countDown();
    }

    @Override
    protected void handleStartTimestamping() {
        super.handleStartTimestamping();
    }

}
