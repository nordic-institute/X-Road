package ee.cyber.sdsb.asyncsender;

import java.io.InputStream;
import java.util.Date;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.asyncdb.MessageQueue;
import ee.cyber.sdsb.asyncdb.QueueInfo;
import ee.cyber.sdsb.asyncdb.SendingCtx;
import ee.cyber.sdsb.common.ErrorCodes;
import ee.cyber.sdsb.common.message.SoapMessageImpl;

class MessageQueueWorker implements Runnable {

    private static final Logger LOG =
            LoggerFactory.getLogger(MessageQueueWorker.class);

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
            LOG.error("Failed to get QueueInfo", e);
            return null;
        }

        Date nextAttempt = queueInfo.getNextAttempt();
        LOG.trace("getNextAttempt(): {}", nextAttempt);
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
        LOG.trace("trySendNextMessage({})", nextAttempt);

        if (new Date().after(nextAttempt)) {
            LOG.trace("Start sending message at {}", nextAttempt);
            doSendNextMessage();
        }
    }

    void doSendNextMessage() {
        LOG.trace("doSendNextMessage()");

        SendingCtx sendingCtx = null;
        try {
            sendingCtx = queue.startSending();
            if (sendingCtx == null) {
                LOG.trace("Did not get SendingCtx, assuming no more messages");
                return;
            }
        } catch (Exception e) {
            LOG.error("Failed to get SendingCtx", e);
            return;
        }

        SoapMessageImpl response = null;
        try {
            response = sendMessage(sendingCtx);
            sendingCtx.success(response != null ? response.getXml() : "");
            LOG.trace("Message successfully sent!");
        } catch (Exception e) {
            LOG.error("Failed to send message", e);

            String faultCode = ErrorCodes.translateException(e).getFaultCode();
            try {
                String result = response != null
                        ? response.getXml() : e.toString();
                sendingCtx.failure(faultCode, result);
            } catch (Exception e1) {
                LOG.error("Error when calling sendingCtx.failure(" + faultCode
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
