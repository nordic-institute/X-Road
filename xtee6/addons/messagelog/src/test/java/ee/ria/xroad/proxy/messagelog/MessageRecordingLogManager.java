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

import ee.ria.xroad.common.util.JobManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Log manager which just records messages, to test that control mailbox
 * works properly. Can be controlled from outside to start / stop processing
 * messages.
 */
@Slf4j
public class MessageRecordingLogManager extends LogManager {
    MessageRecordingLogManager() throws Exception {
        super(new JobManager());
    }
    @Getter
    private static List messages = Collections.synchronizedList(new ArrayList<Object>());

    // continue processing messages when 1) first message has been sent 2) test signals that it
    // is ready to continue (it has sent the first message)
    private static CountDownLatch continueWhenFirstMessageHasArrivedLatch = new CountDownLatch(2);

    // locked from outside when stopProcessingMessages() is called
    // (if stopProcessingMessages is used, it has to be called before sending ay messages to this actor)
    private static Lock messageProcessingStoppedLock = new ReentrantLock();

    public static final String GET_INSTANCE_MESSAGE = "getInstance";

    @Override
    public void onReceive(Object message) throws Exception {
        log.info("onReceive {}", message);
        try {
            if (message instanceof String && GET_INSTANCE_MESSAGE.equals(message)) {
                // send "this" back to caller
                getSender().tell(this, getSelf());
            } else {
                continueWhenFirstMessageHasArrivedLatch.countDown();
                log.debug("(2) first message latch = " + continueWhenFirstMessageHasArrivedLatch.getCount());
                continueWhenFirstMessageHasArrivedLatch.await();
                messageProcessingStoppedLock.lock();
                messages.add(message);
                getSender().tell("done", getSelf());
            }
        } finally {
            messageProcessingStoppedLock.unlock();
        }
    }

    public void stopProcessingMessages() {
        messageProcessingStoppedLock.lock();
    }

    public void resumeProcessingMessages() {
        messageProcessingStoppedLock.unlock();
    }

    /**
     * Continue when first actual message (not GET_INSTANCE) has arrived in onReceive
     * @throws InterruptedException
     */
    public void waitForFirstMessageToArrive() throws InterruptedException {
        continueWhenFirstMessageHasArrivedLatch.countDown();
        log.debug("(1) waiting for message latch = " + continueWhenFirstMessageHasArrivedLatch.getCount());
        continueWhenFirstMessageHasArrivedLatch.await();
    }
}
