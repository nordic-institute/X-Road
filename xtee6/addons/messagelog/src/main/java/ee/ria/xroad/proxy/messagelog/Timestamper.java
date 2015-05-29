package ee.ria.xroad.proxy.messagelog;

import java.io.Serializable;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;

import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.messagelog.MessageRecord;

/**
 * Timestamper is responsible for routing timestamping tasks to the
 * timestamp worker.
 */
@Slf4j
public class Timestamper extends UntypedActor {

    @Data
    @RequiredArgsConstructor
    @ToString(exclude = "signatureHashes")
    static final class TimestampTask implements Serializable {
        private final Long[] messageRecords;
        private final String[] signatureHashes;

        TimestampTask(MessageRecord messageRecord) {
            this.messageRecords = new Long[] {messageRecord.getId()};
            this.signatureHashes =
                    new String[] {messageRecord.getSignatureHash()};
        }
    }

    @Data
    @ToString(exclude = { "timestampDer", "hashChains" })
    static final class TimestampSucceeded implements Serializable {
        private final Long[] messageRecords;
        private final byte[] timestampDer;
        private final String hashChainResult;
        private final String[] hashChains;
    }

    @Data
    static final class TimestampFailed implements Serializable {
        private final Long[] messageRecords;
        private final Exception cause;
    }

    @Override
    public void onReceive(Object message) throws Exception {
        log.trace("onReceive({})", message.getClass());

        if (message instanceof TimestampTask) {
            handleTimestampTask((TimestampTask) message);
        } else {
            unhandled(message);
        }
    }

    protected Class<? extends TimestamperWorker> getWorkerImpl() {
        return TimestamperWorker.class;
    }

    private void handleTimestampTask(TimestampTask message) {
        if (!GlobalConf.isValid()) {
            return;
        }

        // Spawn a new temporary child actor that will do the actual
        // time stamping, which is probably lengthy process.
        ActorRef worker = getContext().actorOf(
                Props.create(getWorkerImpl(), ServerConf.getTspUrl()));
        worker.tell(message, getSender());
    }
}
