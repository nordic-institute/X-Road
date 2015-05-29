package ee.ria.xroad.proxy.messagelog;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.hibernate.Session;

class TestLogCleaner extends LogCleaner {

    private static CountDownLatch gate = new CountDownLatch(1);

    public static void waitForCleanSuccessful() throws Exception {
        try {
            gate.await(5, TimeUnit.SECONDS);
        } finally {
            gate = new CountDownLatch(1);
        }
    }

    @Override
    protected void handleClean(Session session) {
        super.handleClean(session);

        gate.countDown();
    }
}
