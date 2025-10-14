/*
 * The MIT License
 * Copyright (c) 2019- Nordic Institute for Interoperability Solutions (NIIS)
 * Copyright (c) 2018 Estonian Information System Authority (RIA),
 * Nordic Institute for Interoperability Solutions (NIIS), Population Register Centre (VRK)
 * Copyright (c) 2015-2017 Estonian Information System Authority (RIA), Population Register Centre (VRK)
 * <p>
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 * <p>
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 * <p>
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package org.niis.xroad.opmonitor.core;

import ee.ria.xroad.common.identifier.ClientId;

import com.google.common.collect.Sets;
import io.quarkus.scheduler.Scheduled;
import io.quarkus.scheduler.Scheduler;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.niis.xroad.opmonitor.core.config.OpMonitorProperties;
import org.niis.xroad.opmonitor.core.mapper.OperationalDataRecordMapper;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.niis.xroad.opmonitor.core.OperationalDataTestUtil.OBJECT_READER;
import static org.niis.xroad.opmonitor.core.OperationalDataTestUtil.formatFullOperationalDataAsJson;
import static org.niis.xroad.opmonitor.core.OperationalDataTestUtil.storeFullOperationalDataRecord;
import static org.niis.xroad.opmonitor.core.OperationalDataTestUtil.storeFullOperationalDataRecords;

/**
 * Test cases related to the operations with the operational monitoring
 * database at the level of OperationalDataRecordManager.
 */
@Slf4j
public class OperationalDataRecordManagerTest extends BaseTestUsingDB {

    private static final String LONG_STRING = "I'm a long string. At the very"
            + " least longer than two hundred and fifty five characters. Should"
            + " be long enough to fail a database operation in case no string"
            + " truncation is performed. It's nice to be this long. But if"
            + " strings are truncated then i should end already.";

    /**
     * Cleanup the stored records before each test.
     *
     * @throws Exception if an error occurs.
     */
    @Before
    public void beforeTest() throws Exception {
        int cleaned = DATABASE_CTX.doInTransaction(
                session -> session.createMutationQuery("delete OperationalDataRecordEntity")
                        .executeUpdate());

        log.info("Cleaned {} records", cleaned);
    }

    @Test
    public void storeAndQueryOperationalData() throws Exception {
        OperationalDataRecord record = OBJECT_READER.readValue(
                formatFullOperationalDataAsJson(), OperationalDataRecord.class);
        operationalDataRecordManager.storeRecords(Collections.singletonList(record), 1474968979L);

        OperationalDataRecords result =
                operationalDataRecordManager.queryAllRecords();

        assertEquals(1, result.size());

        OperationalDataRecord resultRecord = result.getRecords().getFirst();

        // The id field is the only field that differs between the original
        // data and the stored record, but storeRecords() adds
        // the missing id to the record variable.
        resultRecord.setId(null);
        assertEquals(record, resultRecord);
    }

    @Test
    public void storeAndQueryDataFromPeriods() throws Exception {
        storeFullOperationalDataRecords(1, 1474968960L, operationalDataRecordManager);
        storeFullOperationalDataRecords(1, 1474968980L, operationalDataRecordManager);

        OperationalDataRecords result = operationalDataRecordManager.queryRecords(234, 123);
        assertTrue(result.getRecords().isEmpty());

        result = operationalDataRecordManager.queryRecords(1474968960L, 1474968980L);
        assertEquals(2, result.size());

        for (OperationalDataRecord rec : result.getRecords()) {
            assertTrue(rec.getMonitoringDataTs() >= 1474968960
                    && rec.getMonitoringDataTs() <= 1474968980);
        }

        result = operationalDataRecordManager.queryRecords(1474968970L, 1474968990L);
        assertEquals(1, result.size());
        assertNotNull(result.getRecords().getFirst().getMessageId());
        assertEquals(1474968980L, result.getRecords().getFirst()
                .getMonitoringDataTs().longValue());

        result = operationalDataRecordManager.queryRecords(1474968950L, 1474968970L);
        assertEquals(1, result.size());
        assertEquals(1474968960L, result.getRecords().getFirst()
                .getMonitoringDataTs().longValue());
    }

    @Test
    public void storeAndQueryDataCausingOverflow() throws Exception {
        storeFullOperationalDataRecords(8, 1474968980L, operationalDataRecordManager);
        storeFullOperationalDataRecords(17, 1474968981L, operationalDataRecordManager);

        // Less than max records.
        operationalDataRecordManager = new OperationalDataRecordManager(DATABASE_CTX, 10);
        OperationalDataRecords result = operationalDataRecordManager.queryRecords(1474968980L, 1474968980L);
        assertEquals(8, result.size());
        assertNull(result.getNextRecordsFrom());

        // Max records, no overflow indication since there are no records left.
        operationalDataRecordManager = new OperationalDataRecordManager(DATABASE_CTX, 8);
        result = operationalDataRecordManager.queryRecords(1474968980L, 1474968980L);
        assertEquals(8, result.size());
        assertNull(result.getNextRecordsFrom());

        // Additional records, no overflow indication since the last record
        // timestamp equals to the timestamp recordsTo.
        operationalDataRecordManager = new OperationalDataRecordManager(DATABASE_CTX, 5);
        result = operationalDataRecordManager.queryRecords(1474968980L, 1474968980L);
        assertEquals(8, result.size());
        assertNull(result.getNextRecordsFrom());

        // Additional records, no overflow indication since the overflowing
        // records are from the same second than the last ones that fit
        // in the limit.
        operationalDataRecordManager = new OperationalDataRecordManager(DATABASE_CTX, 10);
        result = operationalDataRecordManager.queryRecords(1474968980L, 1474968990L);
        assertEquals(25, result.size());
        assertNull(result.getNextRecordsFrom());

        storeFullOperationalDataRecords(1, 1474968985L, operationalDataRecordManager);

        // Max records, overflow indication since there are records left
        // than were stored later than the records that fit into the limit.
        operationalDataRecordManager = new OperationalDataRecordManager(DATABASE_CTX, 8);
        result = operationalDataRecordManager.queryRecords(1474968960L, 1474968990L);
        assertEquals(8, result.size());
        assertNotNull(result.getNextRecordsFrom());
        assertEquals(1474968981, result.getNextRecordsFrom().longValue());

        // Additional records, overflow indication since there is 1 record left
        // that was stored later than the last record that fits into the limit.
        operationalDataRecordManager = new OperationalDataRecordManager(DATABASE_CTX, 10);
        result = operationalDataRecordManager.queryRecords(1474968960L, 1474968990L);
        assertEquals(25, result.size());
        assertNotNull(result.getNextRecordsFrom());
        assertEquals(1474968982L, result.getNextRecordsFrom().longValue());

        // Additional records, overflow indication since there is 1 record left
        // that was stored later than the last record that fits into the limit.
        operationalDataRecordManager = new OperationalDataRecordManager(DATABASE_CTX, 10);
        result = operationalDataRecordManager.queryRecords(1474968981L, 1474968990L);
        assertEquals(17, result.size());
        assertNotNull(result.getNextRecordsFrom());
        assertEquals(1474968982L, result.getNextRecordsFrom().longValue());
    }

    @Test
    public void storeAndQueryDataFilteringByOutputFields() throws Exception {
        ClientId client = ClientId.Conf.create(
                "XTEE-CI-XM", "GOV", "00000001", "System1");

        storeFullOperationalDataRecords(1, 1474968960L, operationalDataRecordManager);

        // Request by regular client, return the data
        // with securityServerInternalId as NULL.
        OperationalDataRecords result = operationalDataRecordManager.queryRecords(1474968960L,
                1474968980L, client, null, Sets.newHashSet("requestInTs",
                        "securityServerInternalIp", "securityServerType"));

        assertEquals(1, result.size());

        OperationalDataRecord record = result.getRecords().getFirst();

        assertNotNull(record.getRequestInTs());
        assertEquals(14749689780000L, record.getRequestInTs().longValue());
        assertNull(record.getSecurityServerInternalIp());
        assertEquals("Client",
                record.getSecurityServerType());
        // Other fields are nulls, check some of them..
        assertNull(record.getId());
        assertNull(record.getMonitoringDataTs());
        assertNull(record.getMessageId());
        assertNull(record.getMessageProtocolVersion());
        assertNull(record.getSucceeded());

        // Request by owner or central monitoring client, return the data
        // including the actual value of securityServerInternalId.
        result = operationalDataRecordManager.queryRecords(1474968960L, 1474968980L, null, null,
                Sets.newHashSet("requestInTs",
                        "securityServerInternalIp", "securityServerType"));

        assertEquals(1, result.size());

        record = result.getRecords().getFirst();

        assertEquals("192.168.3.250", record.getSecurityServerInternalIp());

        // The output spec has a single field that is null in DB.
        result = operationalDataRecordManager.queryRecords(1474968960L, 1474968980L, client, null,
                Sets.newHashSet("faultCode"));
        assertEquals(1, result.size());

        final OperationalDataRecord r = result.getRecords().getFirst();

        OperationalDataOutputSpecFields.OUTPUT_FIELDS.forEach(i -> {
            try {
                Field field = OperationalDataRecord.class.getDeclaredField(i);
                field.setAccessible(true);

                assertNull(field.get(r));
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException("Internal error (" + i + ")", e);
            }
        });
    }

    @Test
    public void storeAndQueryDataFilteringByClientAndServiceProvider()
            throws Exception {
        ClientId client = ClientId.Conf.create(
                "XTEE-CI-XM", "GOV", "00000001", "System1");
        ClientId unknownClient = ClientId.Conf.create(
                "XTEE-CI-XM", "GOV", "00000001", "UNKNOWN");
        ClientId serviceProvider = ClientId.Conf.create(
                "XTEE-CI-XM", "GOV", "00000000", "Center");
        ClientId unknownServiceProvider = ClientId.Conf.create(
                "XTEE-CI-XM", "GOV", "00000000", "UNKNOWN");

        ClientId member = ClientId.Conf.create("XTEE-CI-XM", "GOV", "00000011");

        storeFullOperationalDataRecord(1474968960L, client, serviceProvider, operationalDataRecordManager);
        storeFullOperationalDataRecord(1474968970L, client, serviceProvider, operationalDataRecordManager);
        storeFullOperationalDataRecord(1474968980L, client, client, operationalDataRecordManager);
        storeFullOperationalDataRecord(1474968980L, serviceProvider, client, operationalDataRecordManager);
        storeFullOperationalDataRecord(1474968980L, serviceProvider,
                serviceProvider, operationalDataRecordManager);
        storeFullOperationalDataRecord(1474968990L, client, member, operationalDataRecordManager);
        storeFullOperationalDataRecord(1474968991L, member, member, operationalDataRecordManager);
        storeFullOperationalDataRecord(1474968992L, member, client, operationalDataRecordManager);

        // Known client
        OperationalDataRecords result = operationalDataRecordManager.queryRecords(1474968960L, 1474968980L,
                client);
        assertEquals(4, result.size());

        // Unknown client
        result = operationalDataRecordManager.queryRecords(1474968960L, 1474968980L, unknownClient);
        assertEquals(0, result.size());

        // Known client, known service provider
        result = operationalDataRecordManager.queryRecords(1474968960L, 1474968980L, client, serviceProvider,
                new HashSet<>());
        assertEquals(2, result.size());

        // Known client, unknown service provider
        result = operationalDataRecordManager.queryRecords(1474968960L, 1474968980L, client,
                unknownServiceProvider, new HashSet<>());
        assertEquals(0, result.size());

        // Unknown client, known service provider
        result = operationalDataRecordManager.queryRecords(1474968960L, 1474968980L, unknownClient,
                serviceProvider, new HashSet<>());
        assertEquals(0, result.size());

        // Unknown client, unknown service provider
        result = operationalDataRecordManager.queryRecords(1474968960L, 1474968980L, unknownClient,
                unknownServiceProvider, new HashSet<>());
        assertEquals(0, result.size());

        // Same client and service provider
        result = operationalDataRecordManager.queryRecords(1474968960L, 1474968980L, client, client,
                new HashSet<>());
        assertEquals(2, result.size());

        // Known service provider
        result = operationalDataRecordManager.queryRecords(1474968960L, 1474968980L, null, serviceProvider,
                new HashSet<>());
        assertEquals(3, result.size());

        // Unknown service provider
        result = operationalDataRecordManager.queryRecords(1474968960L, 1474968980L, null,
                unknownServiceProvider, new HashSet<>());
        assertEquals(0, result.size());

        // Known member
        result = operationalDataRecordManager.queryRecords(1474968990L, 1474968993L, member);
        assertEquals(3, result.size());

        // Known client, known service provider (member)
        result = operationalDataRecordManager.queryRecords(1474968990L, 1474968993L, client, member,
                new HashSet<>());
        assertEquals(1, result.size());
    }

    @Test
    public void storeAndQueryDataFilteringByClientAndServiceProviderOverflow()
            throws Exception {
        ClientId client = ClientId.Conf.create(
                "XTEE-CI-XM", "GOV", "00000001", "System1");
        ClientId serviceProvider = ClientId.Conf.create(
                "XTEE-CI-XM", "GOV", "00000000", "Center");

        storeFullOperationalDataRecord(1474968960L, client, serviceProvider, operationalDataRecordManager);
        storeFullOperationalDataRecord(1474968961L, client, serviceProvider, operationalDataRecordManager);
        storeFullOperationalDataRecord(1474968962L, client, serviceProvider, operationalDataRecordManager);
        storeFullOperationalDataRecord(1474968963L, client, serviceProvider, operationalDataRecordManager);
        storeFullOperationalDataRecord(1474968964L, client, serviceProvider, operationalDataRecordManager);
        storeFullOperationalDataRecord(1474968960L, serviceProvider, client, operationalDataRecordManager);
        storeFullOperationalDataRecord(1474968961L, serviceProvider, client, operationalDataRecordManager);
        storeFullOperationalDataRecord(1474968962L, serviceProvider, client, operationalDataRecordManager);
        storeFullOperationalDataRecord(1474968963L, serviceProvider, client, operationalDataRecordManager);
        storeFullOperationalDataRecord(1474968964L, serviceProvider, client, operationalDataRecordManager);

        // Less than max records.
        operationalDataRecordManager = new OperationalDataRecordManager(DATABASE_CTX, 10);
        OperationalDataRecords result = operationalDataRecordManager.queryRecords(1474968960L, 1474968980L);
        assertEquals(10, result.size());
        assertNull(result.getNextRecordsFrom());

        // Known client
        result = operationalDataRecordManager.queryRecords(1474968960L, 1474968980L,
                client);
        assertEquals(10, result.size());

        // Known client, known service provider
        result = operationalDataRecordManager.queryRecords(1474968960L, 1474968980L, client, serviceProvider,
                new HashSet<>());
        assertEquals(5, result.size());

        // Known client, known service provider
        result = operationalDataRecordManager.queryRecords(1474968960L, 1474968980L, serviceProvider, client,
                new HashSet<>());
        assertEquals(5, result.size());

        // Result has more records than max
        // Filter by client and service provider
        operationalDataRecordManager = new OperationalDataRecordManager(DATABASE_CTX, 4);
        result = operationalDataRecordManager.queryRecords(1474968960L, 1474968980L, client, serviceProvider,
                new HashSet<>());
        assertEquals(4, result.size());
        assertNotNull(result.getNextRecordsFrom());
        result = operationalDataRecordManager.queryRecords(result.getNextRecordsFrom(), 1474968980L, client, serviceProvider,
                new HashSet<>());
        assertEquals(1, result.size());
    }

    @Test
    public void cleanupLogRecords() throws Exception {
        storeFullOperationalDataRecords(1, 1474968970L, operationalDataRecordManager);
        storeFullOperationalDataRecords(1, 1474968980L, operationalDataRecordManager);

        OperationalDataRecordCleaner operationalDataRecordCleanerJob = new OperationalDataRecordCleaner(mock(OpMonitorProperties.class),
                DATABASE_CTX,
                mock(Scheduler.class), mock(Scheduled.ApplicationNotRunning.class));
        operationalDataRecordCleanerJob.cleanRecords(Instant.ofEpochMilli(1474968975000L));

        OperationalDataRecords result = operationalDataRecordManager.queryRecords(1474968960L, 1474968980L);

        assertEquals(1, result.size());
    }

    @Test
    public void stringTruncation() throws Exception {
        OperationalDataRecord record = OBJECT_READER.readValue(
                formatFullOperationalDataAsJson(), OperationalDataRecord.class);

        record.setMessageIssue(LONG_STRING);
        record.setFaultString(LONG_STRING);

        // test truncate on save
        operationalDataRecordManager.storeRecords(Collections.singletonList(record), 1474968965L);

        OperationalDataRecords result = operationalDataRecordManager.queryAllRecords();

        assertEquals(1, result.size());

        OperationalDataRecord resultRecord = result.getRecords().getFirst();

        assertEquals(LONG_STRING.substring(0, 255),
                resultRecord.getMessageIssue());
        assertEquals(LONG_STRING, resultRecord.getFaultString());

        // test truncate on update
        resultRecord.setMessageIssue("2" + LONG_STRING);

        DATABASE_CTX.doInTransaction(session -> {
            var entity = OperationalDataRecordMapper.get().toEntity(resultRecord);

            session.merge(entity);
            return null;
        });

        result = operationalDataRecordManager.queryAllRecords();
        assertEquals(1, result.size());
        OperationalDataRecord updatedResultRecord = result.getRecords().getFirst();

        assertEquals(("2" + LONG_STRING).substring(0, 255),
                updatedResultRecord.getMessageIssue());
    }
}
