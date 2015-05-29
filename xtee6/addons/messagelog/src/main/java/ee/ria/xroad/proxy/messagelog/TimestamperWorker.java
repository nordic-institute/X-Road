package ee.ria.xroad.proxy.messagelog;

import java.util.Arrays;
import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import akka.actor.ActorRef;
import akka.actor.UntypedActor;

import ee.ria.xroad.proxy.messagelog.Timestamper.TimestampTask;

/**
 * Timestamper worker is responsible for creating timestamps.
 */
@Slf4j
@RequiredArgsConstructor
public class TimestamperWorker extends UntypedActor {

    private final List<String> tspUrls;

    @Override
    public void onReceive(Object message) throws Exception {
        log.trace("onReceive({})", message.getClass());

        if (message instanceof TimestampTask) {
            try {
                handleTimestampTask((TimestampTask) message);
            } catch (Exception e) {
                handleFailure((TimestampTask) message, e);
            } finally {
                getContext().stop(getSelf());
            }
        } else {
            unhandled(message);
        }
    }

    private void handleFailure(TimestampTask message, Exception e) {
        log.error("Timestamper failed for message records {}: {}",
                Arrays.toString(message.getMessageRecords()), e.getMessage());

        getSender().tell(new Timestamper.TimestampFailed(
                message.getMessageRecords(), e), ActorRef.noSender());
    }

    private void handleTimestampTask(TimestampTask message) throws Exception {
        if (tspUrls.isEmpty()) {
            throw new RuntimeException(
                    "Cannot time-stamp, no TSP URLs configured");
        }

        Long[] logRecords = message.getMessageRecords();
        if (logRecords == null || logRecords.length == 0) {
            throw new RuntimeException(
                    "Cannot time-stamp, no log records specified");
        }

        String[] signatureHashes = message.getSignatureHashes();
        if (signatureHashes == null
                || logRecords.length != signatureHashes.length) {
            throw new RuntimeException(
                    "Cannot time-stamp, no signature hashes specified");
        }

        long start = System.currentTimeMillis();

        AbstractTimestampRequest tsRequest =
                createTimestampRequest(logRecords, signatureHashes);

        Object result = tsRequest.execute(tspUrls);

        log.info("Timestamped {} message records in {} ms",
                message.getMessageRecords().length,
                (System.currentTimeMillis() - start));

        getSender().tell(result, ActorRef.noSender());
    }

    private AbstractTimestampRequest createTimestampRequest(Long[] logRecords,
            String[] signatureHashes) throws Exception {
        if (logRecords.length == 1) {
            log.debug("Creating regular time-stamp");

            return createSingleTimestampRequest(logRecords[0]);
        } else {
            log.debug("Creating batch time-stamp for {} hashes",
                    signatureHashes.length);

            return createBatchTimestampRequest(logRecords, signatureHashes);
        }
    }

    protected AbstractTimestampRequest createSingleTimestampRequest(
            Long logRecord) {
        return new SingleTimestampRequest(logRecord);
    }

    protected AbstractTimestampRequest createBatchTimestampRequest(
            Long[] logRecords, String[] signatureHashes) {
        return new BatchTimestampRequest(logRecords, signatureHashes);
    }

}
