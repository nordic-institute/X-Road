package ee.cyber.sdsb.proxy.securelog;

import java.util.Date;

import ee.cyber.sdsb.common.message.SoapMessageImpl;
import ee.cyber.sdsb.common.securelog.AbstractLogManager;
import ee.cyber.sdsb.common.securelog.LogRecord;
import ee.cyber.sdsb.common.signature.SignatureData;
import ee.cyber.sdsb.common.util.JobManager;

/**
 * A dummy implementation of message log that does nothing.
 * Actual implementation can be provided by addon.
 */
public class NullLogManager extends AbstractLogManager {

    NullLogManager(JobManager jobManager) throws Exception {
        super(jobManager);
    }

    @Override
    protected void log(SoapMessageImpl message, SignatureData signature)
            throws Exception {
        // do nothing
    }

    @Override
    protected LogRecord findByQueryId(String queryId, Date startTime,
            Date endTime) throws Exception {
        return null;
    }

}
