package ee.ria.xroad.proxy.messagelog;

import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.hibernate.Session;

import ee.ria.xroad.common.messagelog.archive.DigestEntry;

class TestLogArchiver extends LogArchiver {

    private static CountDownLatch gate = new CountDownLatch(1);

    TestLogArchiver(Path arhivePath, Path workingPath) {
        super(arhivePath, workingPath);
    }

    public static void waitForArchiveSuccessful() throws Exception {
        try {
            gate.await(5, TimeUnit.SECONDS);
        } finally {
            gate = new CountDownLatch(1);
        }
    }

    @Override
    protected void markArchiveCreated(final DigestEntry lastArchive,
            final Session session) throws Exception {
        super.markArchiveCreated(lastArchive, session);

        gate.countDown();
    }
}
