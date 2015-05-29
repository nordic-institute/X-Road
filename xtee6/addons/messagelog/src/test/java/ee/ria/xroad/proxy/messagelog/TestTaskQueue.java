package ee.ria.xroad.proxy.messagelog;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import ee.ria.xroad.proxy.messagelog.Timestamper.TimestampFailed;
import ee.ria.xroad.proxy.messagelog.Timestamper.TimestampSucceeded;

class TestTaskQueue extends TaskQueue {

    private static CountDownLatch gate = new CountDownLatch(1);
    private static Object lastMessage;

    TestTaskQueue(LogManager logManager) {
        super(logManager);
    }

    public static void waitForMessage() throws Exception {
        try {
            gate.await(5, TimeUnit.SECONDS);
        } finally {
            gate = new CountDownLatch(1);
        }
    }

    public static Object getLastMessage() {
        return lastMessage;
    }

    @Override
    protected void handleTimestampSucceeded(TimestampSucceeded message) {
        super.handleTimestampSucceeded(message);

        lastMessage = message;
        gate.countDown();
    }

    @Override
    protected void handleTimestampFailed(TimestampFailed message) {
        super.handleTimestampFailed(message);

        lastMessage = message;
        gate.countDown();
    }

    @Override
    protected void handleStartTimestamping() {
        super.handleStartTimestamping();
    }

}
