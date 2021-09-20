/**
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

import ee.ria.xroad.common.messagelog.FindByQueryId;
import ee.ria.xroad.common.messagelog.SoapLogMessage;
import ee.ria.xroad.common.util.JobManager;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.pattern.Patterns;
import akka.testkit.javadsl.TestKit;
import com.typesafe.config.ConfigFactory;
import com.typesafe.config.ConfigValueFactory;
import lombok.extern.slf4j.Slf4j;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.FiniteDuration;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests logmanager messaging
 */
@Slf4j
public class LogManagerTest {

    private static ActorSystem system;
    private static JobManager jobManager;

    @BeforeClass
    public static void setup() throws Exception {
        system = ActorSystem.create("Proxy", ConfigFactory.load().getConfig("proxy")
                .withValue("akka.remote.artery.canonical.port", ConfigValueFactory.fromAnyRef(0)));
        jobManager = new JobManager();
    }

    @AfterClass
    public static void teardown() throws Exception {
        jobManager.stop();
        TestKit.shutdownActorSystem(system);
        system = null;
    }

    @Test
    public void testControlMessageOvertakesOthers() throws Exception {
        new TestKit(system) {
            {
                final Props props = Props.create(MessageRecordingLogManager.class, jobManager)
                        .withDispatcher("akka.control-aware-dispatcher");
                final ActorRef subject = system.actorOf(props);

                // request direct access to logmanager instance
                subject.tell(MessageRecordingLogManager.GET_INSTANCE_MESSAGE, getRef());
                // wait for response with handle to logmanager
                final int timeout = 5000;
                final Duration timeoutDuration = Duration.ofMillis(timeout);
                MessageRecordingLogManager logManager =
                        expectMsgClass(timeoutDuration, MessageRecordingLogManager.class);

                // stop processing messages
                log.debug("stopping processing");

                logManager.stopProcessingMessages();
                // send bunch of messages. first one will be received and
                // then processing stops. once processing is freed, the
                // next one (2nd overall) should be the control message
                List<Future<?>> replies = new ArrayList<>();

                log.debug("asking first message");

                replies.add(Patterns.ask(subject, "dummy first message guaranteed to be processed as first item",
                        timeout));
                // wait until the first message has arrived
                // (this is needed for predictable results, otherwise 2nd message may overtake the first
                // on the way to mailbox)
                log.debug("waiting for first message");

                logManager.waitForFirstMessageToArrive();

                // then the rest of the messages - these are the actual test targets
                log.debug("asking the rest of messages");

                replies.add(Patterns.ask(subject, "another-foostring", timeout));
                replies.add(Patterns.ask(subject, new SoapLogMessage(null, null, false), timeout));
                replies.add(Patterns.ask(subject, new FindByQueryId(null, null, null), timeout));
                replies.add(Patterns.ask(subject, new SetTimestampingStatusMessage(
                        SetTimestampingStatusMessage.Status.SUCCESS), timeout));
                // enable processing
                logManager.resumeProcessingMessages();

                // wait for all processed
                for (Future<?> f : replies) {
                    Await.ready(f, FiniteDuration.fromNanos(timeoutDuration.toNanos()));
                }

                List<Object> messages = MessageRecordingLogManager.getMessages();

                log.debug("logManager mailbox contents: " + dumpMailbox(messages));

                assertEquals(5, messages.size());
                // check that item #2 is the control message

                assertTrue("message should have been SetTimestampingStatusMessage, was "
                        + messages.get(1), messages.get(1) instanceof SetTimestampingStatusMessage);
            }
        };
    }

    private String dumpMailbox(List<Object> messages) {
        StringBuilder buf = new StringBuilder();
        int number = 1;

        for (Object o : messages) {
            buf.append(number++);
            buf.append(".");
            buf.append(o);
            buf.append(" ");
        }

        return buf.toString();
    }
}
