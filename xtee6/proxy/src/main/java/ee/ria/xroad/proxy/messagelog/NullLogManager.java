package ee.ria.xroad.proxy.messagelog;

import java.util.Date;

import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.messagelog.AbstractLogManager;
import ee.ria.xroad.common.messagelog.LogRecord;
import ee.ria.xroad.common.signature.SignatureData;
import ee.ria.xroad.common.util.JobManager;

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
