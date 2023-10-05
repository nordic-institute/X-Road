/*
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

import ee.ria.xroad.common.db.HibernateUtil;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.messagelog.AbstractLogRecord;
import ee.ria.xroad.common.messagelog.LogRecord;
import ee.ria.xroad.common.messagelog.MessageRecord;
import ee.ria.xroad.common.messagelog.TimestampRecord;
import ee.ria.xroad.messagelog.database.MessageRecordEncryption;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.hibernate.query.Query;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.function.Function;

import static ee.ria.xroad.proxy.messagelog.MessageLogDatabaseCtx.doInTransaction;

/**
 * Log record manager handles saving of log records to database.
 */
@Slf4j
public final class LogRecordManager {

    private static final int DEFAULT_BATCH_SIZE = 50;
    private static final String GET_BY_QUERY_ID_LOG_FORMAT = "getByQueryId({}, {}, {})";

    private static int configuredBatchSize = 0;

    private static final String UPDATE_LOG_RECORD_STATEMENT = "UPDATE logrecord SET timestamprecord = ?, "
            + "timestamphashchain = ?, signaturehash = NULL WHERE id = ? AND timestamprecord IS NULL";

    private static final int INDEX_1 = 1;
    private static final int INDEX_2 = 2;
    private static final int INDEX_3 = 3;

    private LogRecordManager() {
    }

    /**
     * Returns a log record for a given message Query Id, start and end time.
     * @param queryId the message query id.
     * @param startTime the start time.
     * @param endTime the end time.
     * @return the log record or null, if log record is not found in database.
     * @throws Exception if an error occurs while communicating with database.
     */
    static LogRecord getByQueryId(String queryId, Date startTime, Date endTime) throws Exception {
        log.trace(GET_BY_QUERY_ID_LOG_FORMAT, queryId, startTime, endTime);

        return doInTransaction(session -> getMessageRecord(session, queryId, startTime, endTime));
    }

    /**
     * Returns a log record for a given message Query Id and sender Client Id.
     * @param queryId the message query id.
     * @param clientId the sender client id.
     * @param isResponse whether the response record should be retrieved.
     * @return the log record or null, if log record is not found in database.
     * @throws Exception if an error occurs while communicating with database.
     */
    public static <R> R getByQueryIdUnique(String queryId, ClientId clientId, Boolean isResponse,
            Function<MessageRecord, R> processor)
            throws Exception {
        log.trace(GET_BY_QUERY_ID_LOG_FORMAT, queryId, clientId, isResponse);

        return doInTransaction(session -> processor.apply(getMessageRecord(session, queryId, clientId, isResponse)));
    }

    /**
     * Returns a list of log records for a given message Query Id and sender Client Id.
     * @param queryId the message query id.
     * @param clientId the sender client id.
     * @param isResponse whether the response record should be retrieved.
     * @return the log record list or empty list, if no log records were not found in database.
     * @throws Exception if an error occurs while communicating with database.
     */
    public static <R> R getByQueryId(String queryId, ClientId clientId, Boolean isResponse,
            Function<List<MessageRecord>, R> processor)
            throws Exception {
        log.trace(GET_BY_QUERY_ID_LOG_FORMAT, queryId, clientId, isResponse);

        return doInTransaction(session -> processor.apply(getMessageRecords(session, queryId, clientId, isResponse)));
    }

    /**
     * Returns a log record for a given log record number.
     * @param number the log record number.
     * @return the log record or null, if log record is not found in database.
     * @throws Exception if an error occurs while communicating with database.
     */
    public static LogRecord get(Long number) throws Exception {
        log.trace("get({})", number);

        return doInTransaction(session -> getLogRecord(session, number));
    }

    /**
     * Saves the message record to database.
     * @param messageRecord the message record to be saved.
     * @throws Exception if an error occurs while communicating with database.
     */
    static void saveMessageRecord(MessageRecord messageRecord) throws Exception {

        final MessageRecordEncryption encryption = MessageRecordEncryption.getInstance();
        final boolean encrypt = encryption.encryptionEnabled();

        doInTransaction(session -> {
            //the blob must be created within hibernate session
            messageRecord.setId(getNextRecordId(session));

            if (encrypt) {
                encryption.prepareEncryption(messageRecord);
            }

            InputStream is = messageRecord.getAttachmentStream();
            if (is != null) {
                messageRecord.setAttachment(
                        session.getLobHelper().createBlob(is, messageRecord.getAttachmentStreamSize()));
            }

            save(session, messageRecord);
            return null;
        });
    }

    /**
     * Saves the message record in the database.
     * @param messageRecord the message record to be updated.
     * @throws Exception if an error occurs while communicating with database.
     */
    @SuppressWarnings("JpaQlInspection")
    static void updateMessageRecordSignature(MessageRecord messageRecord, String oldHash) throws Exception {
        doInTransaction(session -> {
            final Query<?> query = session.createQuery("update MessageRecord m "
                    + "set m.signature = :signature, m.signatureHash = :hash "
                    + "where m.id = :id and m.timestampRecord is null and m.signatureHash = :oldhash");

            query.setParameter("id", messageRecord.getId());
            query.setParameter("hash", messageRecord.getSignatureHash());
            query.setParameter("signature", messageRecord.getSignature());
            query.setParameter("oldhash", oldHash);
            query.executeUpdate();
            return null;
        });
    }

    /**
     * Saves the time-stamp record to database. Associates the message records with this time-stamp
     * record.
     * @param timestampRecord the time-stamp record to be saved.
     * @param timestampedLogRecords the message records that were time-stamped.
     * @param hashChains the time-stamp hash chains for each message record.
     * @throws Exception if an error occurs while communicating with database.
     */
    static void saveTimestampRecord(TimestampRecord timestampRecord, Long[]
            timestampedLogRecords, String[] hashChains)
            throws Exception {
        doInTransaction(session -> {
            timestampRecord.setId(getNextRecordId(session));
            save(session, timestampRecord);
            setMessageRecordsTimestamped(session, timestampedLogRecords, timestampRecord, hashChains);
            return null;
        });
    }

    /**
     * Saves the log record to database. Sets the number of the log record.
     * @param session the Hibernate session.
     * @param logRecord the log record to save.
     */
    static void save(Session session, LogRecord logRecord) {
        log.trace("save({})", logRecord.getClass());
        session.save(logRecord);
    }

    static long getNextRecordId(Session session) {
        return ((Number) session.createNativeQuery("SELECT nextval('logrecord_sequence')").getSingleResult()).longValue();
    }

    /**
     * Associates each log record with the time-stamp record.
     * @param session the Hibernate session.
     * @param messageRecords the message records.
     * @param timestampRecord the time-stamp record.
     * @param hashChains the time-stamp hash chains.
     */
    private static void setMessageRecordsTimestamped(Session session, Long[] messageRecords,
            TimestampRecord timestampRecord, String[] hashChains) {
        if (log.isTraceEnabled()) {
            log.trace("setMessageRecordsTimestamped({}, {})", Arrays.toString(messageRecords),
                    timestampRecord.getId());
        }

        if (hashChains != null && messageRecords.length != hashChains.length) {
            throw new RuntimeException("Must have hash chain for each log record");
        }

        // Let's perform directly JDBC related work for bulk update.
        // Needs to flush the session to get access to previously saved timestamp record.
        session.flush();
        session.doWork(connection -> setMessageRecordsTimestamped(messageRecords, timestampRecord,
                hashChains,
                connection, getConfiguredBatchSize(session)));
    }

    private static void setMessageRecordsTimestamped(Long[] messageRecords, TimestampRecord
            timestampRecord,
            String[] hashChains, Connection connection, int batchSize) throws SQLException {
        log.trace("setMessageRecordsTimestamped({})", messageRecords.length);

        int storedCount = 0;

        try (PreparedStatement stmt = connection.prepareStatement(UPDATE_LOG_RECORD_STATEMENT)) {
            for (int i = 0; i < messageRecords.length; i++) {
                String hashChain = hashChains != null ? hashChains[i] : null;

                stmt.setLong(INDEX_1, timestampRecord.getId());
                stmt.setString(INDEX_2, hashChain);
                stmt.setLong(INDEX_3, messageRecords[i]);
                stmt.addBatch();

                if (++storedCount % batchSize == 0) {
                    log.trace("setMessageRecordsTimestamped(): execute batch({})", batchSize);

                    stmt.executeBatch();
                    stmt.clearBatch();
                }
            }

            if (storedCount % batchSize != 0) {
                log.trace("setMessageRecordsTimestamped(): execute batch({})", storedCount % batchSize);

                stmt.executeBatch();
            }
        }
    }

    private static LogRecord getLogRecord(Session session, Long number) {
        return session.get(AbstractLogRecord.class, number);
    }

    private static MessageRecord getMessageRecord(Session session, String queryId, Date startTime, Date endTime) {
        final CriteriaBuilder cb = session.getCriteriaBuilder();
        final CriteriaQuery<MessageRecord> query = cb.createQuery(MessageRecord.class);
        final Root<MessageRecord> m = query.from(MessageRecord.class);

        query.select(m)
                .where(cb.and(
                        cb.equal(m.get("queryId"), queryId),
                        cb.between(m.get("time"), startTime.getTime(), endTime.getTime())
                ));
        return session.createQuery(query).setMaxResults(1).uniqueResult();
    }

    private static MessageRecord getMessageRecord(Session session, String queryId, ClientId clientId,
            Boolean isResponse) {
        final CriteriaQuery<MessageRecord> query = createRecordCriteria(session, queryId, clientId, isResponse);
        return session.createQuery(query).setReadOnly(true).setMaxResults(1).uniqueResult();
    }

    private static List<MessageRecord> getMessageRecords(Session session, String queryId, ClientId
            clientId,
            Boolean isResponse) {
        final CriteriaQuery<MessageRecord> query = createRecordCriteria(session, queryId, clientId, isResponse);
        return session.createQuery(query).setReadOnly(true).getResultList();
    }

    private static CriteriaQuery<MessageRecord> createRecordCriteria(Session session, String queryId, ClientId clientId,
            Boolean isResponse) {

        final CriteriaBuilder cb = session.getCriteriaBuilder();
        final CriteriaQuery<MessageRecord> query = cb.createQuery(MessageRecord.class);
        final Root<MessageRecord> m = query.from(MessageRecord.class);

        Predicate pred = cb.and(
                cb.equal(m.get("queryId"), queryId),
                cb.equal(m.get("memberClass"), clientId.getMemberClass()),
                cb.equal(m.get("memberCode"), clientId.getMemberCode()),
                cb.equal(m.get("memberCode"), clientId.getMemberCode()));

        final String subsystemCode = clientId.getSubsystemCode();
        if (subsystemCode == null) {
            pred = cb.and(pred, cb.isNull(m.get("subsystemCode")));
        } else {
            pred = cb.and(pred, cb.equal(m.get("subsystemCode"), subsystemCode));
        }

        if (isResponse != null) {
            pred = cb.and(pred, cb.equal(m.get("response"), isResponse));
        }

        return query.select(m).where(pred);
    }

    private static int getConfiguredBatchSize(Session session) {
        if (configuredBatchSize == 0) {
            configuredBatchSize = HibernateUtil.getConfiguredBatchSize(session, DEFAULT_BATCH_SIZE);
        }
        return configuredBatchSize;
    }

}
