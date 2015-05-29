package ee.ria.xroad.asyncdb;

import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * To see exactly when the locks are acquired and released, run the main method
 * simultaneously in two separate processes.
 */
public final class LockingTest {
    private LockingTest() {
    }

    private static final Logger LOG = LoggerFactory.getLogger(
            LockingTest.class);

    /**
     * Invokes locking
     * @param args - main method parameters, not used.
     * @throws Exception - when invocation of locking fails.
     */
    public static void main(String[] args) throws Exception {
        LOG.debug("Starting up");

        Callable<Object> task = () -> {
            LOG.debug("Got lock");

            // FileUtils.writeStringToFile(
            // new File(getFilePath()),
            // "hello, world!\n" + this);

            Thread.sleep(20000);

            LOG.debug("Releasing lock");
            return null;
        };

        AsyncDBUtil.performLocked(task, "/tmp/locktest", new Object());
        LOG.debug("Shutting down");
    }
}
