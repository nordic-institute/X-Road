package ee.cyber.xroad.serviceimporter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class XLockTest {

    private static final Logger LOG = LoggerFactory.getLogger(
            XLockTest.class);

    private static final String LOCK_KEY = "/tmp";

    public static void main(String[] args) throws Exception {
        new Reader(3000).start();
        new Reader(3000).start();
        new Reader(3000).start();

        Thread.sleep(1000);

        new Writer(5000).start();
        new Writer(5000).start();

        new Reader(3000).start();
        new Reader(3000).start();
    }

    static class Reader extends Thread {
        private int sleep;

        public Reader(int sleep) {
            this.sleep = sleep;
        }

        public void run() {
            LOG.info("Acquiring read lock.");

            XLock lock = new XLock(LOCK_KEY);
            lock.readLock();

            LOG.info("Got lock, sleeping for {} milliseconds.", sleep);
            try {
                Thread.sleep(sleep);
            } catch (InterruptedException e) {
                LOG.error("Thread interrupted!");
            }

            LOG.info("Unlocking semaphore.");
            lock.unlock();
        }
    }

    static class Writer extends Thread {
        private int sleep;

        public Writer(int sleep) {
            this.sleep = sleep;
        }

        public void run() {
            LOG.info("Acquiring write lock.");

            XLock lock = new XLock(LOCK_KEY);
            lock.writeLock();

            LOG.info("Got lock, sleeping for {} milliseconds.", sleep);
            try {
                Thread.sleep(sleep);
            } catch (InterruptedException e) {
                LOG.error("Thread interrupted!");
            }

            LOG.info("Unlocking semaphore.");
            lock.unlock();
        }
    }
}
