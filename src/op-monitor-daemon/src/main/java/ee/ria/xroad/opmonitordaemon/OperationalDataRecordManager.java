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
package ee.ria.xroad.opmonitordaemon;

import ee.ria.xroad.common.db.HibernateUtil;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.opmonitoring.OpMonitoringSystemProperties;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.hibernate.query.Query;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static ee.ria.xroad.opmonitordaemon.OpMonitorDaemonDatabaseCtx.doInTransaction;
import static ee.ria.xroad.opmonitordaemon.OperationalDataOutputSpecFields.MONITORING_DATA_TS;

/**
 * This class encapsulates all the database access related to the
 * operational_data table, mapped by the OperationalDataRecord class.
 */
@Slf4j
final class OperationalDataRecordManager {

    private static final int DEFAULT_BATCH_SIZE = 50;

    @Setter
    private static int maxRecordsInPayload = OpMonitoringSystemProperties.getOpMonitorMaxRecordsInPayload();

    private static int configuredBatchSize = 0;

    private OperationalDataRecordManager() {
    }

    static void storeRecords(List<OperationalDataRecord> records, long timestamp) throws Exception {
        doInTransaction(session -> storeInTransaction(session, records, timestamp));
    }

    static OperationalDataRecords queryAllRecords() throws Exception {
        return doInTransaction(OperationalDataRecordManager::queryAllOperationalDataInTransaction);
    }

    static OperationalDataRecords queryRecords(long recordsFrom, long recordsTo) throws Exception {
        return queryRecords(recordsFrom, recordsTo, null, null, new HashSet<>());
    }

    static OperationalDataRecords queryRecords(long recordsFrom, long recordsTo, ClientId clientFilter)
            throws Exception {
        return queryRecords(recordsFrom, recordsTo, clientFilter, null, new HashSet<>());
    }

    static OperationalDataRecords queryRecords(long recordsFrom, long recordsTo, ClientId clientFilter,
            ClientId serviceProviderFilter, Set<String> outputFields) throws Exception {
        OperationalDataRecords records = doInTransaction(session -> queryOperationalDataInTransaction(session,
                recordsFrom, recordsTo, clientFilter, serviceProviderFilter, outputFields));

        removeMonitoringDataTsIfNotSpecified(records, outputFields);

        return records;
    }

    private static Void storeInTransaction(Session session, List<OperationalDataRecord> records, long timestamp) {
        int storedCount = 0;
        int batchSize = getConfiguredBatchSize(session);

        for (OperationalDataRecord record : records) {
            record.setMonitoringDataTs(timestamp);
            session.save(record);

            if (++storedCount % batchSize == 0) {
                session.flush();
                session.clear();
            }
        }

        return null;
    }

    private static int getConfiguredBatchSize(Session session) {
        if (configuredBatchSize == 0) {
            configuredBatchSize = HibernateUtil.getConfiguredBatchSize(session, DEFAULT_BATCH_SIZE);
        }

        return configuredBatchSize;
    }

    private static OperationalDataRecords queryAllOperationalDataInTransaction(Session session) {
        final Query<OperationalDataRecord> query =
                session.createQuery("SELECT r FROM OperationalDataRecord r", OperationalDataRecord.class)
                        .setReadOnly(true);
        return new OperationalDataRecords(query.getResultList());
    }

    /**
     * Queries operational data records from the database using search criteria parameters. The number of returned
     * records is limited by the configured value maxRecordsInPayload plus overflow records with the same
     * monitorindDataTs timestamp as the last included record.
     * @param session               database session
     * @param recordsFrom           records from timestamp seconds
     * @param recordsTo             records to timestamp seconds
     * @param clientFilter          filter records by client (if not null)
     * @param serviceProviderFilter filter records by service provider (if not null)
     * @param outputFields          list of the requested operational data field
     * @return operational data records.
     */
    @SuppressWarnings("unchecked")
    private static OperationalDataRecords queryOperationalDataInTransaction(Session session, long recordsFrom,
            long recordsTo, ClientId clientFilter, ClientId serviceProviderFilter, Set<String> outputFields) {

        final OperationalDataRecordQuery
                query = new OperationalDataRecordQuery(session, clientFilter, serviceProviderFilter, outputFields);
        query.between(recordsFrom, recordsTo);
        query.orderByAsc(MONITORING_DATA_TS);
        query.setMaxRecords(maxRecordsInPayload);
        OperationalDataRecords records = new OperationalDataRecords(query.list());

        // Check overflow.
        if (records.size() == maxRecordsInPayload) {
            log.trace("Check possible records overflow");

            long lastMonitoringDataTs = records.getLastMonitoringDataTs();
            final OperationalDataRecordQuery overflow =
                    new OperationalDataRecordQuery(session, clientFilter, serviceProviderFilter, outputFields);
            overflow.addOverflowCriteria(lastMonitoringDataTs);
            OperationalDataRecords overflowRecords = new OperationalDataRecords(overflow.list());

            records.removeRecordsByMonitoringDataTs(lastMonitoringDataTs);
            records.append(overflowRecords);

            if (recordsOverflow(session, lastMonitoringDataTs, recordsTo, clientFilter, serviceProviderFilter)) {
                log.debug("Records overflow, set nextRecordsFrom to {}", lastMonitoringDataTs + 1);

                records.setNextRecordsFrom(lastMonitoringDataTs + 1);
            }
        }

        return records;
    }

    private static void removeMonitoringDataTsIfNotSpecified(OperationalDataRecords records, Set<String> outputFields) {
        if (!outputFields.isEmpty() && !outputFields.contains(MONITORING_DATA_TS)) {
            records.getRecords().forEach(i -> i.setMonitoringDataTs(null));
        }
    }

    private static boolean recordsOverflow(Session session, long lastMonitoringDataTs, long recordsTo,
            ClientId clientFilter, ClientId serviceProviderFilter) {
        // Indicate overflow only if some records are not included.
        return lastMonitoringDataTs < recordsTo && hasRecordsLeft(session, lastMonitoringDataTs, recordsTo,
                clientFilter, serviceProviderFilter);
    }

    private static boolean hasRecordsLeft(Session session, long lastMonitoringDataTs, long recordsTo,
            ClientId clientFilter, ClientId serviceProviderFilter) {
        if (lastMonitoringDataTs == recordsTo) {
            return false;
        }

        final OperationalDataRecordQuery query =
                new OperationalDataRecordQuery(session, clientFilter, serviceProviderFilter,
                        Collections.singleton(MONITORING_DATA_TS));
        // BETWEEN treats the endpoint values as included in the range.
        query.between(lastMonitoringDataTs + 1, recordsTo);
        query.setMaxRecords(1);

        return query.list().size() > 0;
    }
}
