/**
 * The MIT License
 * Copyright (c) 2016 Estonian Information System Authority (RIA), Population Register Centre (VRK)
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

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Criteria;
import org.hibernate.Session;
import org.hibernate.criterion.Criterion;
import org.hibernate.criterion.Order;
import org.hibernate.criterion.ProjectionList;
import org.hibernate.criterion.Projections;
import org.hibernate.criterion.Restrictions;
import org.hibernate.transform.Transformers;

import ee.ria.xroad.common.db.HibernateUtil;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.opmonitoring.OpMonitoringSystemProperties;

import static ee.ria.xroad.common.opmonitoring.OpMonitoringData.*;
import static ee.ria.xroad.opmonitordaemon.OpMonitorDaemonDatabaseCtx.doInTransaction;
import static ee.ria.xroad.opmonitordaemon.OperationalDataOutputSpecFields.MONITORING_DATA_TS;
import static ee.ria.xroad.opmonitordaemon.OperationalDataOutputSpecFields.PUBLIC_OUTPUT_FIELDS;

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

    @SuppressWarnings("unchecked")
    private static OperationalDataRecords queryAllOperationalDataInTransaction(Session session) {
        return new OperationalDataRecords(session.createCriteria(OperationalDataRecord.class).list());
    }

    /**
     * Queries operational data records from the database using search criteria parameters. The number of returned
     * records is limited by the configured value maxRecordsInPayload plus overflow records with the same
     * monitorindDataTs timestamp as the last included record.
     * @param session database session
     * @param recordsFrom records from timestamp seconds
     * @param recordsTo records to timestamp seconds
     * @param clientFilter filter records by client (if not null)
     * @param serviceProviderFilter filter records by service provider (if not null)
     * @param outputFields list of the requested operational data field
     * @return operational data records.
     */
    @SuppressWarnings("unchecked")
    private static OperationalDataRecords queryOperationalDataInTransaction(Session session, long recordsFrom,
            long recordsTo, ClientId clientFilter, ClientId serviceProviderFilter, Set<String> outputFields) {
        Criteria criteria = createCriteria(session, recordsFrom, recordsTo, clientFilter, serviceProviderFilter,
                outputFields);
        OperationalDataRecords records = new OperationalDataRecords(criteria.list());

        // Check overflow.
        if (records.size() == maxRecordsInPayload) {
            log.trace("Check possible records overflow");

            long lastMonitoringDataTs = records.getLastMonitoringDataTs();

            Criteria overflowCriteria = createOverflowCriteria(session, lastMonitoringDataTs, clientFilter,
                    serviceProviderFilter, outputFields);
            OperationalDataRecords overflowRecords = new OperationalDataRecords(overflowCriteria.list());

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

        Criteria criteria = createCriteria(session, clientFilter, serviceProviderFilter,
                Collections.singleton(MONITORING_DATA_TS));
        // BETWEEN treats the endpoint values as included in the range.
        criteria.add(Restrictions.between(MONITORING_DATA_TS, lastMonitoringDataTs + 1, recordsTo));
        criteria.setMaxResults(1);

        return criteria.list().size() > 0;
    }

    private static Criteria createCriteria(Session session, long recordsFrom, long recordsTo, ClientId clientFilter,
            ClientId serviceProviderFilter, Set<String> outputFields) {
        Criteria criteria = createCriteria(session, clientFilter, serviceProviderFilter, outputFields);

        // BETWEEN treats the endpoint values as included in the range.
        criteria.add(Restrictions.between(MONITORING_DATA_TS, recordsFrom, recordsTo));
        criteria.addOrder(Order.asc(MONITORING_DATA_TS));
        criteria.setMaxResults(maxRecordsInPayload);

        return criteria;
    }

    private static Criteria createCriteria(Session session, ClientId clientFilter, ClientId serviceProviderFilter,
            Set<String> outputFields) {
        Criteria criteria = session.createCriteria(OperationalDataRecord.class);
        boolean publicFieldsOnly = clientFilter != null;

        configureOutputFields(criteria, publicFieldsOnly, outputFields);
        configureClientAndServiceProviderFilters(criteria, clientFilter, serviceProviderFilter);

        return criteria;
    }

    private static void configureOutputFields(Criteria criteria, boolean publicFieldsOnly, Set<String> outputFields) {
        if (publicFieldsOnly) {
            Set<String> fields;

            if (outputFields.isEmpty()) {
                fields = PUBLIC_OUTPUT_FIELDS;
            } else {
                fields = new HashSet<>(outputFields);
                fields.remove(SECURITY_SERVER_INTERNAL_IP);
            }

            setProjectionList(criteria, fields);
        } else {
            if (!outputFields.isEmpty()) {
                setProjectionList(criteria, outputFields);
            }
        }
    }

    private static void setProjectionList(Criteria criteria, Set<String> fields) {
        ProjectionList projList = Projections.projectionList();
        HashSet<String> fieldSet = new HashSet<>(fields);

        // Necessary for searching the records.
        fieldSet.add(MONITORING_DATA_TS);

        log.trace("setProjectionList(): {}", fieldSet);

        fieldSet.forEach(i -> projList.add(Projections.property(i), i));

        criteria.setProjection(projList);
        criteria.setResultTransformer(Transformers.aliasToBean(OperationalDataRecord.class));
    }

    private static void configureClientAndServiceProviderFilters(Criteria criteria, ClientId client,
            ClientId serviceProvider) {
        if (client != null) {
            if (serviceProvider != null) {
                // Filter by both the client and the service provider
                // using the fields corresponding to their roles.
                criteria.add(Restrictions.and(Restrictions.or(getMemberCriterion(client, true),
                        getMemberCriterion(client, false)),
                        getMemberCriterion(serviceProvider, false)));
            } else {
                // Filter by the client in either roles (client or
                // service provider).
                criteria.add(Restrictions.or(getMemberCriterion(client, true), getMemberCriterion(client, false)));
            }
        } else {
            if (serviceProvider != null) {
                // Filter by the service provider in its respective role.
                criteria.add(getMemberCriterion(serviceProvider, false));
            }
        }
    }

    private static Criterion getMemberCriterion(ClientId member, boolean isClient) {
        return Restrictions.and(
                Restrictions.eq(isClient ? CLIENT_XROAD_INSTANCE : SERVICE_XROAD_INSTANCE, member.getXRoadInstance()),
                Restrictions.eq(isClient ? CLIENT_MEMBER_CLASS : SERVICE_MEMBER_CLASS, member.getMemberClass()),
                Restrictions.eq(isClient ? CLIENT_MEMBER_CODE : SERVICE_MEMBER_CODE, member.getMemberCode()),
                member.getSubsystemCode() == null
                        ? Restrictions.isNull(isClient ? CLIENT_SUBSYSTEM_CODE : SERVICE_SUBSYSTEM_CODE)
                        : Restrictions.eq(isClient ? CLIENT_SUBSYSTEM_CODE : SERVICE_SUBSYSTEM_CODE,
                        member.getSubsystemCode()));
    }

    private static Criteria createOverflowCriteria(Session session, long monitoringDataTs, ClientId clientFilter,
            ClientId serviceProviderFilter, Set<String> outputFields) {
        Criteria criteria = createCriteria(session, clientFilter, serviceProviderFilter, outputFields);

        criteria.add(Restrictions.eq(MONITORING_DATA_TS, monitoringDataTs));

        return criteria;
    }
}
