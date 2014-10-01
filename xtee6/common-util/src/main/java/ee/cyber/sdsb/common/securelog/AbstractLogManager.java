package ee.cyber.sdsb.common.securelog;

import java.util.Date;

import akka.actor.UntypedActor;

import ee.cyber.sdsb.common.message.SoapMessageImpl;
import ee.cyber.sdsb.common.signature.SignatureData;
import ee.cyber.sdsb.common.util.JobManager;

public abstract class AbstractLogManager extends UntypedActor {

    protected AbstractLogManager(JobManager jobManager) {
        if (jobManager == null) {
            throw new IllegalArgumentException("jobManager cannot be null");
        }
    }

    @Override
    public void onReceive(Object message) throws Exception {
        try {
            if (message instanceof LogMessage) {
                LogMessage m = (LogMessage) message;
                log(m.getMessage(), m.getSignature());
                getSender().tell(new Object(), getSelf());
            } else if (message instanceof FindByQueryId) {
                FindByQueryId f = (FindByQueryId) message;
                LogRecord result = findByQueryId(f.getQueryId(),
                        f.getStartTime(), f.getEndTime());
                getSender().tell(result, getSelf());
            } else {
                unhandled(message);
            }
        } catch (Exception e) {
            getSender().tell(e, getSelf());
        }
    }

    protected abstract void log(SoapMessageImpl message,
            SignatureData signature) throws Exception;

    protected abstract LogRecord findByQueryId(String queryId, Date startTime,
            Date endTime) throws Exception;
}
