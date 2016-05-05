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
