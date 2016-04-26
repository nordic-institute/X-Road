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

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.Restrictions;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.messagelog.AbstractLogRecord;
import ee.ria.xroad.common.messagelog.LogRecord;
import ee.ria.xroad.common.messagelog.MessageRecord;
import ee.ria.xroad.common.messagelog.TimestampRecord;

import static ee.ria.xroad.proxy.messagelog.MessageLogDatabaseCtx.doInTransaction;

/**
 * Log record manager handles saving of log records to database.
 */
@Slf4j
public class LogRecordManager {

    /**
     * Returns a log record for a given message Query Id, start and end time.
     * @param queryId the message query id
     * @param startTime the start time
     * @param endTime the end time
     * @return the log record or null, if log record is not found in database.
     * @throws Exception if an error occurs while communicating with database
     */
    public LogRecord getByQueryId(String queryId, Date startTime, Date endTime)
            throws Exception {
        log.trace("getByQueryId({}, {}, {})",
                new Object[] {queryId, startTime, endTime});

        return doInTransaction(session -> getMessageRecord(session, queryId,
                startTime, endTime));
    }

    /**
     * Returns a log record for a given message Query Id and sender Client Id.
     * @param queryId the message query id
     * @param clientId the sender client id
     * @param isResponse whether the response record should be retrieved
     * @return the log record or null, if log record is not found in database.
     * @throws Exception if an error occurs while communicating with database
     */
    public MessageRecord getByQueryIdUnique(String queryId, ClientId clientId,
            boolean isResponse) throws Exception {
        log.trace("getByQueryId({}, {}, {})",
                new Object[] {queryId, clientId, isResponse});

        return doInTransaction(session -> getMessageRecord(session, queryId,
                clientId, isResponse));
    }

    /**
     * Returns a list of log records for a given message Query Id and sender Client Id.
     * @param queryId the message query id
     * @param clientId the sender client id
     * @param isResponse whether the response record should be retrieved
     * @return the log record list or empty list, if no log records were not found in database.
     * @throws Exception if an error occurs while communicating with database
     */
    public List<MessageRecord> getByQueryId(String queryId, ClientId clientId,
            boolean isResponse) throws Exception {
        log.trace("getByQueryId({}, {}, {})",
                new Object[] {queryId, clientId, isResponse});

        return doInTransaction(session -> getMessageRecords(session, queryId,
                clientId, isResponse));
    }

    /**
     * Returns a log record for a given log record number.
     * @param number the log record number
     * @return the log record or null, if log record is not found in database.
     * @throws Exception if an error occurs while communicating with database
     */
    public LogRecord get(Long number) throws Exception {
        log.trace("get({})", number);

        return doInTransaction(session -> getLogRecord(session, number));
    }

    /**
     * Saves the message record to database.
     * @param messageRecord the message record to be saved
     * @throws Exception if an error occurs while communicating with database
     */
    public void saveMessageRecord(MessageRecord messageRecord)
            throws Exception {
        doInTransaction(session -> {
            save(session, messageRecord);
            return null;
        });
    }

    /**
     * Saves the message record in the database.
     * @param messageRecord the message record to be updated
     * @throws Exception if an error occurs while communicating with database
     */
    public void updateMessageRecord(MessageRecord messageRecord)
            throws Exception {
        doInTransaction(session -> {
            session.update(messageRecord);
            return null;
        });
    }

    /**
     * Saves the time-stamp record to database. Associates the message records
     * with this time-stamp record.
     * @param timestampRecord the time-stamp record to be saved
     * @param timestampedLogRecords the message records that were time-stamped
     * @param hashChains the time-stamp hash chains for each message record
     * @throws Exception if an error occurs while communicating with database
     */
    public void saveTimestampRecord(TimestampRecord timestampRecord,
            Long[] timestampedLogRecords, String[] hashChains)
                    throws Exception {
        doInTransaction(session -> {
                save(session, timestampRecord);
                setMessageRecordsTimestamped(session, timestampedLogRecords,
                        timestampRecord, hashChains);
                return null;
        });
    }

    /**
     * Saves the log record to database. Sets the number of the log record.
     * @param session the Hibernate session
     * @param logRecord the log record to save
     * @throws Exception if an error occurs while communicating with database
     */
    protected void save(Session session, LogRecord logRecord) {
        log.trace("save({})", logRecord.getClass());

        session.save(logRecord);
    }

    /**
     * Associates each log record with the time-stamp record.
     * @param session the Hibernate session
     * @param messageRecords the message records
     * @param timestampRecord the time-stamp record
     * @param hashChains the time-stamp hash chains
     * @throws Exception if an error occurs while communicating with database
     */
    protected void setMessageRecordsTimestamped(Session session,
            Long[] messageRecords, TimestampRecord timestampRecord,
            String[] hashChains) {
        if (log.isTraceEnabled()) {
            log.trace("setMessageRecordsTimestamped({}, {})",
                    Arrays.toString(messageRecords),
                    timestampRecord.getId());
        }

        if (hashChains != null && messageRecords.length != hashChains.length) {
            throw new RuntimeException(
                    "Must have hash chain for each log record");
        }

        for (int i = 0; i < messageRecords.length; i++) {
            String hashChain = hashChains != null ? hashChains[i] : null;
            setMessageRecordTimestamped(session, messageRecords[i],
                    timestampRecord.getId(), hashChain);
        }
    }

    private void setMessageRecordTimestamped(Session session,
            Long messageRecordId, Long timestampRecordId, String hashChain) {
        log.trace("setMessageRecordTimestamped({})", messageRecordId);

        String query = "update MessageRecord set timestampRecord = :ts, "
                + "timestampHashChain = :hc, "
                + "signatureHash = null where id = :id "
                + "and timestampRecord = null";

        session.createQuery(query)
            .setString("hc", hashChain)
            .setLong("ts", timestampRecordId)
            .setLong("id", messageRecordId)
            .executeUpdate();
    }

    private LogRecord getLogRecord(Session session, Long number) {
        return (AbstractLogRecord) session.get(AbstractLogRecord.class, number);
    }

    @SneakyThrows
    private MessageRecord getMessageRecord(Session session, String queryId,
            Date startTime, Date endTime) {
        Criteria criteria = session.createCriteria(MessageRecord.class);
        criteria.add(Restrictions.eq("queryId", queryId));
        criteria.add(Restrictions.between("time", startTime.getTime(),
                endTime.getTime()));
        criteria.setMaxResults(1);
        return (MessageRecord) criteria.uniqueResult();
    }

    @SneakyThrows
    private MessageRecord getMessageRecord(Session session, String queryId,
            ClientId clientId, boolean isResponse) {
        Criteria criteria = createRecordCriteria(session, queryId, clientId,
                isResponse);
        return (MessageRecord) criteria.uniqueResult();
    }

    @SneakyThrows
    private List<MessageRecord> getMessageRecords(Session session,
            String queryId, ClientId clientId, boolean isResponse) {
        Criteria criteria = createRecordCriteria(session, queryId, clientId,
                isResponse);
        return criteria.list();
    }

    private Criteria createRecordCriteria(Session session, String queryId,
            ClientId clientId, boolean isResponse) {
        Criteria criteria = session.createCriteria(MessageRecord.class);
        criteria.add(Restrictions.eq("queryId", queryId));
        criteria.add(Restrictions.eq("memberClass", clientId.getMemberClass()));
        criteria.add(Restrictions.eq("memberCode", clientId.getMemberCode()));
        String subsystemCode = clientId.getSubsystemCode();
        criteria.add(subsystemCode == null
                ? Restrictions.isNull("subsystemCode")
                : Restrictions.eq("subsystemCode", subsystemCode));
        criteria.add(Restrictions.eq("response", isResponse));
        return criteria;
    }
}
