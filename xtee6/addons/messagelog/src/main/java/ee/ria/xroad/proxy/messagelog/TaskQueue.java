/**
 * The MIT License
 * Copyright (c) 2015 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ee.ria.xroad.proxy.messagelog;

import akka.actor.ActorSelection;
import akka.actor.UntypedActor;
import ee.ria.xroad.common.messagelog.MessageLogProperties;
import ee.ria.xroad.proxy.messagelog.Timestamper.TimestampFailed;
import ee.ria.xroad.proxy.messagelog.Timestamper.TimestampSucceeded;
import ee.ria.xroad.proxy.messagelog.Timestamper.TimestampTask;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;

import java.util.Arrays;
import java.util.List;

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

        sendLogManagerSavedTimestamp(message);
    }

    /**
     * Sends successfully time stamped data to logManager for storing
     * (previously logManager.saveTimestampRecord(message);)
     * @param message
     */
    private void sendLogManagerSavedTimestamp(TimestampSucceeded message) {
        SaveTimestampedDataMessage data = new SaveTimestampedDataMessage(message);
        getContext().parent().tell(data, getSelf());
    }

    /**
     * Sends time stamping status message to logManager
     * @param status
     */
    private void sendLogManagerTimestampingStatus(SetTimestampingStatusMessage.Status status) {
        SetTimestampingStatusMessage statusMessage = new SetTimestampingStatusMessage(status);
        getContext().parent().tell(statusMessage, getSelf());
    }

    protected void handleTimestampFailed(TimestampFailed message) {
        sendLogManagerTimestampingStatus(SetTimestampingStatusMessage.Status.FAILURE);
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

    /**
     * @return whether timestamping task queue is empty or not
     * @throws CannotDetermineTaskQueueSize if queue status could not be determined
     */
    public static boolean isTimestampTasksEmpty() throws CannotDetermineTaskQueueSize {
        try {
            Long number = doInTransaction(TaskQueue::getTimestampTasksCount);
            return number.longValue() == 0;
        } catch (Exception e) {
            throw new CannotDetermineTaskQueueSize("could not read timestamp task queue status", e);
        }
    }

    /**
     * Thrown if we cannot find out the size of timestamping task queue size (for example
     * because database is broken)
     */
    public static class CannotDetermineTaskQueueSize extends RuntimeException {
        public CannotDetermineTaskQueueSize(String s, Throwable throwable) {
            super(s, throwable);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Task> getTimestampTasks(Session session) {
        return session.createQuery(getTaskQueueQuery()).setMaxResults(
                MessageLogProperties.getTimestampRecordsLimit()).list();
    }

    @SuppressWarnings("unchecked")
    private static Long getTimestampTasksCount(Session session) {
        return (Long) session.createQuery(getTaskQueueSizeQuery()).uniqueResult();
    }

    static String getTaskQueueQuery() {
        return "select new " + Task.class.getName() + "(m.id, m.signatureHash) "
                + "from MessageRecord m where m.signatureHash is not null";
    }

    static String getTaskQueueSizeQuery() {
        return "select COUNT(*) "
                + "from MessageRecord m where m.signatureHash is not null";
    }

}
