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

import akka.actor.*;
import akka.testkit.TestActorRef;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigRenderOptions;
import ee.ria.xroad.common.message.SoapMessageImpl;
import ee.ria.xroad.common.messagelog.AbstractLogManager;
import ee.ria.xroad.common.messagelog.MessageLogProperties;
import ee.ria.xroad.common.messagelog.MessageRecord;
import ee.ria.xroad.common.messagelog.TimestampRecord;
import ee.ria.xroad.common.signature.SignatureData;
import ee.ria.xroad.common.util.JobManager;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

@Slf4j
abstract class AbstractMessageLogTest {

    protected JobManager jobManager;
    protected ActorSystem actorSystem;
    protected LogManager logManager;

    @Getter
    private TestActorRef<LogManager> logManagerRef;

    protected void testSetUp() throws Exception {
        testSetUp(false);
    }

    private List<DeadLetter> deadLetters = new ArrayList<DeadLetter>();
    public List<DeadLetter> getDeadLetters() {
        return deadLetters;
    }
    public void clearDeadLetters() {
        deadLetters = new ArrayList<DeadLetter>();
    }
    public synchronized void addDeadLetter(DeadLetter d) {
        deadLetters.add(d);
    }

    public static class DeadLetterActor extends UntypedActor {

        private AbstractMessageLogTest test;
        DeadLetterActor(AbstractMessageLogTest test) {
            this.test = test;
        }

        public void onReceive(Object message) {
            if (message instanceof DeadLetter) {
                log.info("dead letter: " + message);
                test.addDeadLetter((DeadLetter) message);
            }
        }
    }

    protected void testSetUp(boolean timestampImmediately) throws Exception {
        jobManager = new JobManager();
        jobManager.start();
        clearDeadLetters();

        actorSystem = ActorSystem.create("Proxy",
                ConfigFactory.load().getConfig("proxy"));

        actorSystem.eventStream().subscribe(
                actorSystem.actorOf(Props.create(DeadLetterActor.class, this)),
                DeadLetter.class);

        System.setProperty(MessageLogProperties.TIMESTAMP_IMMEDIATELY,
                timestampImmediately ? "true" : "false");

        System.setProperty(MessageLogProperties.SOAP_BODY_LOGGING_ENABLED, "true");

        logManagerRef = TestActorRef.create(actorSystem,
                Props.create(getLogManagerImpl(), jobManager),
                MessageLog.LOG_MANAGER);

        logManager = logManagerRef.underlyingActor();
    }

    /**
     * Use this to print Akka configuration out to log.
     * May be useful when solving problems.
     */
    protected void logAkkaConfiguration() {
        ConfigRenderOptions renderOpts = ConfigRenderOptions.defaults().setOriginComments(false).
                setComments(false).setJson(false);
        String configString = ConfigFactory.load().root().render(renderOpts);
        log.info("akka configuration:");
        log.info(configString);
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
        signalTimestampingStatus(SetTimestampingStatusMessage.Status.SUCCESS);
    }

    /**
     * Sends time stamping status message to logManager
     * @param status
     */
    private void signalTimestampingStatus(SetTimestampingStatusMessage.Status status) {
        SetTimestampingStatusMessage statusMessage = new SetTimestampingStatusMessage(status);
        logManagerRef.tell(statusMessage, ActorRef.noSender());
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
