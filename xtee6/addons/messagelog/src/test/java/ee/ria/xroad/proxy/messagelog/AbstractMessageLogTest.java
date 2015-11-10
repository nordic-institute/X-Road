package ee.ria.xroad.proxy.messagelog;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.testkit.TestActorRef;

import com.typesafe.config.ConfigFactory;

import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.messagelog.AbstractLogManager;
import ee.ria.xroad.common.messagelog.MessageLogProperties;
import ee.ria.xroad.common.messagelog.MessageRecord;
import ee.ria.xroad.common.messagelog.TimestampRecord;
import ee.ria.xroad.common.signature.SignatureData;
import ee.ria.xroad.common.util.JobManager;

import static org.junit.Assert.*;

abstract class AbstractMessageLogTest {

    protected JobManager jobManager;
    protected ActorSystem actorSystem;
    protected LogManager logManager;

    protected void testSetUp() throws Exception {
        testSetUp(false);
    }

    protected void testSetUp(boolean timestampImmediately) throws Exception {
        jobManager = new JobManager();
        jobManager.start();

        actorSystem = ActorSystem.create("Proxy",
                ConfigFactory.load().getConfig("proxy"));

        System.setProperty(MessageLogProperties.TIMESTAMP_IMMEDIATELY,
                timestampImmediately ? "true" : "false");

        if (!timestampImmediately) {
            System.setProperty(MessageLogProperties.TIMESTAMP_IMMEDIATELY,
                    "false");
        }

        TestActorRef<LogManager> testActor = TestActorRef.create(actorSystem,
                Props.create(getLogManagerImpl(), jobManager),
                MessageLog.LOG_MANAGER);
        logManager = testActor.underlyingActor();
    }

    protected void testTearDown() throws Exception {
        jobManager.stop();
        actorSystem.shutdown();
    }

    protected Class<? extends AbstractLogManager> getLogManagerImpl()
            throws Exception {
        return LogManager.class;
    }

    protected void initLogManager() throws Exception {
        logManager.setTimestampSucceeded();
    }

    protected void log(SoapMessageImpl message, SignatureData signature)
            throws Exception {
        logManager.log(message, signature, true);
    }

    protected TimestampRecord timestamp(MessageRecord record) throws Exception {
        return logManager.timestamp(record.getId());
    }

    protected void startTimestamping() {
        actorSystem.actorSelection(component(LogManager.TASK_QUEUE_NAME)).tell(
                TaskQueue.START_TIMESTAMPING, ActorRef.noSender());
    }

    protected void startArchiving() {
        actorSystem.actorSelection(component(LogManager.ARCHIVER_NAME)).tell(
                LogArchiver.START_ARCHIVING, ActorRef.noSender());
    }

    protected void startCleaning() {
        actorSystem.actorSelection(component(LogManager.CLEANER_NAME)).tell(
                LogCleaner.START_CLEANING, ActorRef.noSender());
    }

    protected void awaitTermination() {
        actorSystem.awaitTermination();
    }

    protected static void assertMessageRecord(Object o, String queryId)
            throws Exception {
        assertNotNull(o);
        assertTrue(o instanceof MessageRecord);

        MessageRecord messageRecord = (MessageRecord) o;
        assertEquals(queryId, messageRecord.getQueryId());
    }

    private static String component(String name) {
        return "/user/" + MessageLog.LOG_MANAGER + "/" + name;
    }
}
