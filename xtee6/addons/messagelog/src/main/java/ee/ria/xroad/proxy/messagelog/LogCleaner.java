package ee.ria.xroad.proxy.messagelog;

import lombok.extern.slf4j.Slf4j;

import org.hibernate.Session;
import org.joda.time.DateTime;

import akka.actor.UntypedActor;

import ee.ria.xroad.common.messagelog.MessageLogProperties;

import static ee.ria.xroad.proxy.messagelog.MessageLogDatabaseCtx.doInTransaction;



/**
 * Deletes all archived log records from the database.
 */
@Slf4j
public class LogCleaner extends UntypedActor {

    public static final String START_CLEANING = "doClean";

    @Override
    public void onReceive(Object message) throws Exception {
        log.trace("onReceive({})", message);

        if (message.equals(START_CLEANING)) {
            try {
                doInTransaction(session -> {
                    handleClean(session);
                    return null;
                });
            } catch (Exception e) {
                log.error("Failed to clean archived records from database", e);
            }
        } else {
            unhandled(message);
        }
    }

    protected void handleClean(Session session) {
        DateTime date = new DateTime();
        date = date.minusDays(MessageLogProperties.getKeepRecordsForDays());

        String hql = "delete AbstractLogRecord r where r.archived = true and "
                + "r.time <= " + date.getMillis();
        int removed = session.createQuery(hql).executeUpdate();
        if (removed == 0) {
            log.info("No archived records to remove from database");
        } else {
            log.info("Removed {} archived records from database", removed);
        }
    }
}
