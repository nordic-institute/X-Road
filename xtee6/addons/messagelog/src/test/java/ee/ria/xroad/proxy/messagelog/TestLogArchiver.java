package ee.ria.xroad.proxy.messagelog;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import ee.ria.xroad.common.messagelog.LogRecord;
import ee.ria.xroad.common.messagelog.archive.DigestEntry;

class TestLogArchiver extends LogArchiver {

    private static CountDownLatch gate = new CountDownLatch(1);

    public static void waitForArchiveSuccessful() throws Exception {
        try {
            gate.await(5, TimeUnit.SECONDS);
        } finally {
            gate = new CountDownLatch(1);
        }
    }

    @Override
    protected void setLogRecordsArchived(
            final List<LogRecord> logRecords, final DigestEntry lastArchive)
            throws Exception {
        super.setLogRecordsArchived(logRecords, lastArchive);

        gate.countDown();
    }
}
