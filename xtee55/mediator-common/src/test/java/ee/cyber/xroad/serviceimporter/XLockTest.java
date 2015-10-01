package ee.cyber.xroad.serviceimporter;

import lombok.extern.slf4j.Slf4j;

/**
 * XLock test program.
 */
@Slf4j
public final class XLockTest {

    private static final String LOCK_KEY = "/tmp";

    private XLockTest() {
    }

    /**
     * Main program access point.
     * @param args command-line arguments
     * @throws Exception in case of any errors
     */
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

        @Override
        public void run() {
            log.info("Acquiring read lock.");

            XLock lock = new XLock(LOCK_KEY);
            lock.readLock();

            log.info("Got lock, sleeping for {} milliseconds.", sleep);
            try {
                Thread.sleep(sleep);
            } catch (InterruptedException e) {
                log.error("Thread interrupted!");
            }

            log.info("Unlocking semaphore.");
            lock.unlock();
        }
    }

    static class Writer extends Thread {
        private int sleep;

        public Writer(int sleep) {
            this.sleep = sleep;
        }

        @Override
        public void run() {
            log.info("Acquiring write lock.");

            XLock lock = new XLock(LOCK_KEY);
            lock.writeLock();

            log.info("Got lock, sleeping for {} milliseconds.", sleep);
            try {
                Thread.sleep(sleep);
            } catch (InterruptedException e) {
                log.error("Thread interrupted!");
            }

            log.info("Unlocking semaphore.");
            lock.unlock();
        }
    }
}
