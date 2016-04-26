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
package ee.ria.xroad.asyncsender;

import java.io.InputStream;
import java.util.Date;

import lombok.extern.slf4j.Slf4j;

import ee.ria.xroad.asyncdb.SendingCtx;
import ee.ria.xroad.asyncdb.messagequeue.MessageQueue;
import ee.ria.xroad.asyncdb.messagequeue.QueueInfo;
import ee.ria.xroad.common.message.SoapMessageImpl;

import static ee.ria.xroad.common.ErrorCodes.translateException;

@Slf4j
class MessageQueueWorker implements Runnable {

    private static final int UPDATE_INTERVAL = 1000; // ms

    private final MessageQueue queue;

    private volatile boolean running;

    MessageQueueWorker(MessageQueue queue) {
        if (queue == null) {
            throw new IllegalArgumentException("Queue must not be null");
        }

        this.queue = queue;
        this.running = true;
    }

    @Override
    public void run() {
        try {
            Date nextAttempt = getNextAttempt();
            while (running) {
                if (nextAttempt == null) {
                    // No more messages, this worker can go home!
                    break;
                }

                trySendNextMessage(nextAttempt);

                sleep();

                nextAttempt = getNextAttempt();
            }
        } finally {
            running = false;
        }
    }

    boolean isRunning() {
        return running;
    }

    Date getNextAttempt() {
        QueueInfo queueInfo;
        try {
            queueInfo = queue.getQueueInfo();
        } catch (Exception e) {
            // Failure to get queue info is fatal!
            log.error("Failed to get QueueInfo", e);
            return null;
        }

        Date nextAttempt = queueInfo.getNextAttempt();
        log.trace("getNextAttempt(): {}", nextAttempt);
        return nextAttempt;
    }

    void sleep() {
        try {
            Thread.sleep(UPDATE_INTERVAL);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    void trySendNextMessage(Date nextAttempt) {
        log.trace("trySendNextMessage({})", nextAttempt);

        if (new Date().after(nextAttempt)) {
            log.trace("Start sending message at {}", nextAttempt);
            doSendNextMessage();
        }
    }

    void doSendNextMessage() {
        log.trace("doSendNextMessage()");

        SendingCtx sendingCtx = null;
        try {
            sendingCtx = queue.startSending();
            if (sendingCtx == null) {
                log.trace("Did not get SendingCtx, assuming no more messages");
                return;
            }
        } catch (Exception e) {
            log.error("Failed to get SendingCtx", e);
            return;
        }

        SoapMessageImpl response = null;
        try {
            response = sendMessage(sendingCtx);
            sendingCtx.success(response != null ? response.getXml() : "");
            log.trace("Message successfully sent!");
        } catch (Exception e) {
            log.error("Failed to send message", e);

            String faultCode = translateException(e).getFaultCode();
            try {
                String result = response != null
                        ? response.getXml() : e.toString();
                sendingCtx.failure(faultCode, result);
            } catch (Exception e1) {
                log.error("Error when calling sendingCtx.failure(" + faultCode
                        + ")", e1);
            }
        }
    }

    SoapMessageImpl sendMessage(SendingCtx sendingCtx) throws Exception {
        String contentType = sendingCtx.getContentType();
        InputStream message = sendingCtx.getInputStream();
        return ProxyClient.getInstance().send(contentType, message);
    }

}
