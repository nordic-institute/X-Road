/**
 * The MIT License
 *
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
package ee.ria.xroad.messagelog.archiver;

import ee.ria.xroad.common.SystemPropertiesLoader;
import ee.ria.xroad.common.Version;
import ee.ria.xroad.common.messagelog.MessageLogProperties;
import ee.ria.xroad.common.util.JobManager;

import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.CoordinatedShutdown;
import akka.actor.Props;
import com.typesafe.config.ConfigFactory;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobDataMap;
import org.quartz.JobExecutionContext;

import java.nio.file.Paths;

import static ee.ria.xroad.common.SystemProperties.CONF_FILE_MESSAGE_LOG;
import static ee.ria.xroad.common.SystemProperties.CONF_FILE_NODE;

@Slf4j
public final class LogArchiverMain {

    private static ActorSystem actorSystem;
    private static JobManager jobManager;

    private LogArchiverMain() {
    }

    public static void main(String[] args) {
        Version.outputVersionInfo("MessageLogArchiver");

        try {
            SystemPropertiesLoader.create()
                    .withCommonAndLocal()
                    .with(CONF_FILE_MESSAGE_LOG)
                    .withLocalOptional(CONF_FILE_NODE)
                    .load();

            jobManager = new JobManager();
            actorSystem = ActorSystem.create("MessageLogArchiver", ConfigFactory.load().getConfig("messagelog-archiver")
                    .withFallback(ConfigFactory.load()));

            final ActorRef archiver = actorSystem.actorOf(
                    Props.create(LogArchiver.class, Paths.get(MessageLogProperties.getArchivePath())));
            final ActorRef cleaner = actorSystem.actorOf(Props.create(LogCleaner.class));

            CoordinatedShutdown.get(actorSystem).addJvmShutdownHook(() -> {
                log.info("MessageLogArchiver shutting down...");
                try {
                    if (jobManager != null) {
                        jobManager.stop();
                        jobManager = null;
                    }
                } catch (Exception e) {
                    log.warn("JobManager failed to stop", e);
                }
            });


            jobManager.registerJob(ArchiverJob.class, "ArchiverJob", MessageLogProperties.getArchiveInterval(),
                    jobData(archiver, LogArchiver.START_ARCHIVING));

            jobManager.registerJob(ArchiverJob.class, "CleanerJob", MessageLogProperties.getCleanInterval(),
                    jobData(cleaner, LogCleaner.START_CLEANING));

            jobManager.start();

        } catch (Exception e) {
            log.error("LogArchiver failed to start", e);
            System.exit(1);
        }
    }

    private static JobDataMap jobData(ActorRef actor, Object message) {
        final JobDataMap dataMap = new JobDataMap();
        dataMap.put(ArchiverJob.ACTOR_PROPERTY, actor);
        dataMap.put(ArchiverJob.MESSAGE_PROPERTY, message);
        return dataMap;
    }

    @Setter
    public static class ArchiverJob implements Job {
        private static final String ACTOR_PROPERTY = "actor";
        private static final String MESSAGE_PROPERTY = "message";

        private ActorRef actor;
        private Object message;

        @Override
        public void execute(JobExecutionContext context) {
            actor.tell(message, ActorRef.noSender());
        }
    }
}
