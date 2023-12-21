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
package ee.ria.xroad.proxy.messagelog;

import ee.ria.xroad.proxy.messagelog.Timestamper.TimestampFailed;
import ee.ria.xroad.proxy.messagelog.Timestamper.TimestampSucceeded;

import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
class TestTaskQueue extends TaskQueue {

    static List<Integer> successfulMessageSizes = new ArrayList<>();

    private static CountDownLatch gate = new CountDownLatch(1);
    private static Object lastMessage;

    // Countdownlatch for waiting for next timestamp record save.
    private static CountDownLatch timestampSavedLatch = new CountDownLatch(1);

    static Exception throwWhenSavingTimestamp;

    TestTaskQueue(Timestamper timestamper, LogManager logManager) {
        super(timestamper, logManager);
    }

    static void initGateLatch() {
        log.trace("initGateLatch()");

        gate = new CountDownLatch(1);
    }

    static void initTimestampSavedLatch() {
        log.trace("initTimestampSavedLatch");

        timestampSavedLatch = new CountDownLatch(1);
    }

    static boolean waitForMessage() throws Exception {
        log.trace("waitForMessage()");

        try {
            return gate.await(10, TimeUnit.SECONDS);
        } finally {
            gate = new CountDownLatch(1);
        }
    }

    static Object getLastMessage() {
        return lastMessage;
    }

    /**
     * Waits for a call to saveTimestampRecord for a defined time.
     * @return true when call came, false if timeouted waiting.
     * @throws Exception
     */
    static boolean waitForTimestampSaved() throws Exception {
        log.trace("waitForTimestampSaved()");

        try {
            return timestampSavedLatch.await(5, TimeUnit.SECONDS);
        } finally {
            timestampSavedLatch = new CountDownLatch(1);
        }
    }

    @Override
    protected void saveTimestampRecord(TimestampSucceeded message) throws Exception {
        try {
            if (throwWhenSavingTimestamp != null) {
                throw throwWhenSavingTimestamp;
            }

            successfulMessageSizes.add(message.getMessageRecords().length);
            super.saveTimestampRecord(message);
        } finally {
            timestampSavedLatch.countDown();
        }
    }

    @Override
    protected void handleTimestampSucceeded(TimestampSucceeded message) {
        log.trace("handleTimestampSucceeded()");

        try {
            lastMessage = message;
            super.handleTimestampSucceeded(message);
        } finally {
            gate.countDown();
        }
    }

    @Override
    protected void handleTimestampFailed(TimestampFailed message) {
        log.info("handleTimestampFailed");

        try {
            lastMessage = message;
            super.handleTimestampFailed(message);
        } finally {
            gate.countDown();
        }
    }

    @Override
    protected void handleStartTimestamping() {
        super.handleStartTimestamping();
    }
}
