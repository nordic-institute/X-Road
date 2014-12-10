package ee.cyber.sdsb.proxy.messagelog;

import java.util.Date;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import scala.concurrent.Await;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.pattern.Patterns;
import akka.util.Timeout;

import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.message.SoapMessageImpl;
import ee.cyber.sdsb.common.messagelog.AbstractLogManager;
import ee.cyber.sdsb.common.messagelog.FindByQueryId;
import ee.cyber.sdsb.common.messagelog.LogMessage;
import ee.cyber.sdsb.common.messagelog.LogRecord;
import ee.cyber.sdsb.common.signature.SignatureData;
import ee.cyber.sdsb.common.util.JobManager;

import static ee.cyber.sdsb.common.ErrorCodes.*;

@Slf4j
public class MessageLog {

    public static final String LOG_MANAGER = "LogManager";

    private static final String LOG_MANAGER_IMPL_CLASS =
            SystemProperties.PREFIX + "proxy.messageLogManagerImpl";

    private static ActorRef logManager;

    /**
     * Initializes the secure log using the provided actor system.
     */
    public static void init(ActorSystem actorSystem,
            JobManager jobManager) throws Exception {
        Class<? extends AbstractLogManager> clazz = getLogManagerImpl();
        log.trace("Using implementation class: {}", clazz);

        logManager = actorSystem.actorOf(Props.create(clazz, jobManager),
                LOG_MANAGER);
    }

    /**
     * Save the message and signature to secure log. Attachments are not logged.
     *
     * @throws Exception if an error occurs
     */
    public static void log(SoapMessageImpl message, SignatureData signature)
            throws Exception {
        log.trace("log()");
        try {
            ask(new LogMessage(message, signature));
        } catch (Exception e) {
            throw translateWithPrefix(X_LOGGING_FAILED_X, e);
        }
    }

    /**
     * Returns a log record for a given message Query Id, start and end time.
     * @param queryId the message query id
     * @param startTime the start time
     * @param endTime the end time
     * @return the log record or null, if log record is not found in database.
     * @throws Exception if an error occurs
     */
    public static LogRecord findByQueryId(String queryId, Date startTime,
            Date endTime) throws Exception {
        log.trace("findByQueryId({}, {}, {})",
                new Object[] {queryId, startTime, endTime});
        try {
            assertInitialized();
            return (LogRecord) ask(
                    new FindByQueryId(queryId, startTime, endTime));
        } catch (Exception e) {
            throw translateException(e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Class<? extends AbstractLogManager> getLogManagerImpl() {
        String logManagerImplClassName =
                System.getProperty(LOG_MANAGER_IMPL_CLASS,
                        NullLogManager.class.getName());
        try {
            Class<?> clazz = Class.forName(logManagerImplClassName);
            return (Class<? extends AbstractLogManager>) clazz;
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Unable to load log manager impl: "
                    + logManagerImplClassName, e);
        }
    }

    private static void assertInitialized() {
        if (logManager == null) {
            throw new IllegalStateException("not initialized");
        }
    }

    private static Object ask(Object message) throws Exception {
        assertInitialized();

        Timeout timeout = new Timeout(40, TimeUnit.SECONDS);
        Object result = Await.result(Patterns.ask(logManager, message,
                timeout), timeout.duration());
        if (result instanceof Exception) {
            throw (Exception) result;
        } else {
            return result;
        }
    }
}
