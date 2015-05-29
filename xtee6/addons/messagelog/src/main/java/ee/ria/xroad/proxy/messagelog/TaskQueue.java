package ee.ria.xroad.proxy.messagelog;

import java.util.Arrays;
import java.util.List;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.hibernate.Session;
import org.joda.time.DateTime;

import akka.actor.ActorSelection;
import akka.actor.UntypedActor;

import ee.ria.xroad.common.messagelog.MessageLogProperties;
import ee.ria.xroad.proxy.messagelog.Timestamper.TimestampFailed;
import ee.ria.xroad.proxy.messagelog.Timestamper.TimestampSucceeded;
import ee.ria.xroad.proxy.messagelog.Timestamper.TimestampTask;

import static ee.ria.xroad.proxy.messagelog.LogManager.TIMESTAMPER_NAME;
import static ee.ria.xroad.proxy.messagelog.MessageLogDatabaseCtx.doInTransaction;

/**
 * Handles the TaskQueues -- adds tasks to the queue and sends the active queue
 * for time-stamping.
 */
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class TaskQueue extends UntypedActor {

    public static final String START_TIMESTAMPING = "StartTimestamping";

    private final LogManager logManager;

    @Override
    public void onReceive(Object message) throws Exception {
        log.trace("onReceive({})", message);

        if (message.equals(START_TIMESTAMPING)) {
            handleStartTimestamping();
        } else if (message instanceof Timestamper.TimestampSucceeded) {
            handleTimestampSucceeded((Timestamper.TimestampSucceeded) message);
        } else if (message instanceof Timestamper.TimestampFailed) {
            handleTimestampFailed((Timestamper.TimestampFailed) message);
        } else {
            unhandled(message);
        }
    }

    protected void handleTimestampSucceeded(TimestampSucceeded message) {
        if (log.isTraceEnabled()) {
            log.trace("Time-stamped message records {}",
                    Arrays.toString(message.getMessageRecords()));
        }

        logManager.setTimestampSucceeded();
        try {
            logManager.saveTimestampRecord(message);
        } catch (Exception e) {
            log.error("Failed to save time-stamp record to database", e);

            logManager.setTimestampFailed(new DateTime());
        }
    }

    protected void handleTimestampFailed(TimestampFailed message) {
        log.trace("handleTimestampFailed()");

        logManager.setTimestampFailed(new DateTime());
    }

    protected void handleStartTimestamping() {
        List<Task> timestampTasks;
        try {
            timestampTasks = doInTransaction(this::getTimestampTasks);
        } catch (Exception e) {
            log.error("Error getting time-stamp tasks", e);
            return;
        }

        if (timestampTasks.isEmpty()) {
            log.trace("Nothing to time-stamp, task queue is empty");
            return;
        }

        log.info("Start time-stamping {} message records",
                timestampTasks.size());

        TimestampTask timestampTask = createTimestampTask(timestampTasks);

        ActorSelection timestamper =
                getContext().actorSelection("../" + TIMESTAMPER_NAME);
        timestamper.tell(timestampTask, getSelf());
    }

    private TimestampTask createTimestampTask(List<Task> timestampTasks) {
        Long[] messageRecords = new Long[timestampTasks.size()];
        String[] signatureHashes = new String[timestampTasks.size()];
        for (int i = 0; i < timestampTasks.size(); i++) {
            messageRecords[i] = timestampTasks.get(i).getMessageRecordNo();
            signatureHashes[i] = timestampTasks.get(i).getSignatureHash();
        }

        return new TimestampTask(messageRecords, signatureHashes);
    }

    @SuppressWarnings("unchecked")
    private List<Task> getTimestampTasks(Session session) {
        return session.createQuery(getTaskQueueQuery()).setMaxResults(
                MessageLogProperties.getTimestampRecordsLimit()).list();
    }

    static String getTaskQueueQuery() {
        return "select new " + Task.class.getName() + "(m.id, m.signatureHash) "
                + "from MessageRecord m where m.signatureHash is not null";
    }
}
