package ee.cyber.xroad.proxy.securelog;

import java.util.Date;

import lombok.extern.slf4j.Slf4j;

import ee.cyber.sdsb.common.message.SoapMessageImpl;
import ee.cyber.sdsb.common.messagelog.AbstractLogManager;
import ee.cyber.sdsb.common.messagelog.LogRecord;
import ee.cyber.sdsb.common.signature.SignatureData;
import ee.cyber.sdsb.common.util.JobManager;
import ee.cyber.xroad.common.signature.Signature;

@Slf4j
public class SecureLogManager extends AbstractLogManager {

    public SecureLogManager(JobManager jobManager) {
        super(jobManager);
    }

    @Override
    public void preStart() throws Exception {
        super.preStart();

        LogManager.getInstance().start();
    }

    @Override
    public void postStop() throws Exception {
        super.postStop();

        LogManager.getInstance().stop();
        LogManager.getInstance().join();
    }

    @Override
    protected void log(SoapMessageImpl message, SignatureData signature)
            throws Exception {
        log.trace("log()");

        SignatureRecord sigRecord =
                SignatureRecord.create(new Signature(
                        signature.getSignatureXml()));
        LogManager.log(sigRecord);

        SoapRecord soapRecord = new SoapRecord(message, sigRecord, signature);
        LogManager.log(soapRecord);

        sigRecord.waitUntilDone();
        soapRecord.waitUntilDone();
    }

    @Override
    protected LogRecord findByQueryId(String queryId, Date startTime,
            Date endTime) throws Exception {
        // do nothing
        return null;
    }

}
