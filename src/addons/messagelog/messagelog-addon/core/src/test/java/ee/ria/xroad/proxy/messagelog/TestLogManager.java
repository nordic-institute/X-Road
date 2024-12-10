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
package ee.ria.xroad.proxy.messagelog;

import ee.ria.xroad.common.conf.globalconf.GlobalConfProvider;
import ee.ria.xroad.common.conf.serverconf.ServerConfProvider;
import ee.ria.xroad.common.db.DatabaseCtxV2;
import ee.ria.xroad.common.messagelog.MessageRecord;

import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
class TestLogManager extends LogManager {
    // Countdownlatch for waiting for next timestamp record save.
    private static CountDownLatch setTimestampingStatusLatch = new CountDownLatch(1);

    TestLogManager(String origin, GlobalConfProvider globalConfProvider, ServerConfProvider serverConfProvider, DatabaseCtxV2 databaseCtx) {
        super(origin, globalConfProvider, serverConfProvider, databaseCtx);
    }

    static void initSetTimestampingStatusLatch() {
        log.trace("initSetTimestampingStatusLatch()");

        setTimestampingStatusLatch = new CountDownLatch(1);
    }

    /**
     * Tests expect that they can control when timestamping starts, as in:
     *
     * @return
     * @Test public void timestampingFailed() throws Exception {
     * TestTimestamperWorker.failNextTimestamping(true);
     * log(createMessage(), createSignature);
     * log(createMessage(), createSignature());
     * log(createMessage(), createSignature());
     * assertTaskQueueSize(3);
     * startTimestamping();
     * <p>
     * <p>
     * Now if TimestamperJob starts somewhere before startTimestamping (which
     * is a likely outcome with the default initial delay of 1 sec) the results
     * will not be what the test expects.
     * <p>
     * To avoid this problem, tests have "long enough" initial delay for TimestamperJob.
     */
    @Override
    protected Duration getTimestamperJobInitialDelay() {
        return Duration.of(1, ChronoUnit.MINUTES);
    }

    @Override
    protected TestTaskQueue getTaskQueueImpl(Timestamper timestamper, String origin, DatabaseCtxV2 databaseCtx) {
        return new TestTaskQueue(timestamper, this, origin, databaseCtx);
    }

    /**
     * This method is synchronized in the test class
     */
    @Override
    synchronized void setTimestampSucceeded() {
        super.setTimestampSucceeded();
    }

    @Override
    protected TestTimestamper getTimestamperImpl() {
        return new TestTimestamper(globalConfProvider, serverConfProvider, logRecordManager);
    }

    @Override
    protected MessageRecord saveMessageRecord(MessageRecord messageRecord) throws Exception {
        log.info("saving message record");

        if (MessageLogTest.logRecordTime != null) {
            messageRecord.setTime(MessageLogTest.logRecordTime.getTime());
        }

        return super.saveMessageRecord(messageRecord);
    }

    @Override
    void setTimestampingStatus(SetTimestampingStatusMessage statusMessage) {
        super.setTimestampingStatus(statusMessage);

        setTimestampingStatusLatch.countDown();
    }

    static boolean waitForSetTimestampingStatus() throws Exception {
        log.trace("waitForSetTimestampingStatus()");

        try {
            return setTimestampingStatusLatch.await(5, TimeUnit.SECONDS);
        } finally {
            setTimestampingStatusLatch = new CountDownLatch(1);
        }
    }
}