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
package ee.ria.xroad.opmonitordaemon;

import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.opmonitoring.OpMonitoringSystemProperties;

import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;

import static ee.ria.xroad.opmonitordaemon.OpMonitorDaemonDatabaseCtx.doInTransaction;
import static ee.ria.xroad.opmonitordaemon.OperationalDataRecordManager.queryAllRecords;
import static ee.ria.xroad.opmonitordaemon.OperationalDataRecordManager.queryRecords;
import static ee.ria.xroad.opmonitordaemon.OperationalDataRecordManager.storeRecords;
import static ee.ria.xroad.opmonitordaemon.OperationalDataTestUtil.OBJECT_READER;
import static ee.ria.xroad.opmonitordaemon.OperationalDataTestUtil.formatFullOperationalDataAsJson;
import static ee.ria.xroad.opmonitordaemon.OperationalDataTestUtil.storeFullOperationalDataRecord;
import static ee.ria.xroad.opmonitordaemon.OperationalDataTestUtil.storeFullOperationalDataRecords;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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

    @Rule
    public final ExpectedException expectedException = ExpectedException.none();

    /**
     * Cleanup the stored records before each test.
     * @throws Exception if an error occurs.
     */
    @Before
    public void beforeTest() throws Exception {
        int cleaned = doInTransaction(
                session -> session.createQuery("delete OperationalDataRecord")
                        .executeUpdate());

        log.info("Cleaned {} records", cleaned);

        OperationalDataRecordManager.setMaxRecordsInPayload(
                OpMonitoringSystemProperties.getOpMonitorMaxRecordsInPayload());
    }

    @Test
    public void storeAndQueryOperationalData() throws Exception {
        OperationalDataRecord record = OBJECT_READER.readValue(
                formatFullOperationalDataAsJson(), OperationalDataRecord.class);
        storeRecords(Collections.singletonList(record), 1474968979L);

        OperationalDataRecords result =
                OperationalDataRecordManager.queryAllRecords();

        assertEquals(1, result.size());

        OperationalDataRecord resultRecord = result.getRecords().get(0);

        // The id field is the only field that differs between the original
        // data and the stored record, but storeRecords() adds
        // the missing id to the record variable.
        assertEquals(record, resultRecord);
    }

    @Test
    public void storeAndQueryDataFromPeriods() throws Exception {
        storeFullOperationalDataRecords(1, 1474968960L);
        storeFullOperationalDataRecords(1, 1474968980L);

        OperationalDataRecords result = queryRecords(234, 123);
        assertTrue(result.getRecords().isEmpty());

        result = queryRecords(1474968960L, 1474968980L);
        assertEquals(2, result.size());

        for (OperationalDataRecord rec : result.getRecords()) {
            assertTrue(rec.getMonitoringDataTs() >= 1474968960
                    && rec.getMonitoringDataTs() <= 1474968980);
        }

        result = queryRecords(1474968970L, 1474968990L);
        assertEquals(1, result.size());
        assertNotNull(result.getRecords().get(0).getMessageId());
        assertEquals(1474968980L, result.getRecords().get(0)
                .getMonitoringDataTs().longValue());

        result = queryRecords(1474968950L, 1474968970L);
        assertEquals(1, result.size());
        assertEquals(1474968960L, result.getRecords().get(0)
                .getMonitoringDataTs().longValue());
    }

    @Test
    public void storeAndQueryDataCausingOverflow() throws Exception {
        storeFullOperationalDataRecords(8, 1474968980L);
        storeFullOperationalDataRecords(17, 1474968981L);

        // Less than max records.
        OperationalDataRecordManager.setMaxRecordsInPayload(10);
        OperationalDataRecords result = queryRecords(1474968980L, 1474968980L);
        assertEquals(8, result.size());
        assertNull(result.getNextRecordsFrom());

        // Max records, no overflow indication since there are no records left.
        OperationalDataRecordManager.setMaxRecordsInPayload(8);
        result = queryRecords(1474968980L, 1474968980L);
        assertEquals(8, result.size());
        assertNull(result.getNextRecordsFrom());

        // Additional records, no overflow indication since the last record
        // timestamp equals to the timestamp recordsTo.
        OperationalDataRecordManager.setMaxRecordsInPayload(5);
        result = queryRecords(1474968980L, 1474968980L);
        assertEquals(8, result.size());
        assertNull(result.getNextRecordsFrom());

        // Additional records, no overflow indication since the overflowing
        // records are from the same second than the last ones that fit
        // in the limit.
        OperationalDataRecordManager.setMaxRecordsInPayload(10);
        result = queryRecords(1474968980L, 1474968990L);
        assertEquals(25, result.size());
        assertNull(result.getNextRecordsFrom());

        storeFullOperationalDataRecords(1, 1474968985L);

        // Max records, overflow indication since there are records left
        // than were stored later than the records that fit into the limit.
        OperationalDataRecordManager.setMaxRecordsInPayload(8);
        result = queryRecords(1474968960L, 1474968990L);
        assertEquals(8, result.size());
        assertNotNull(result.getNextRecordsFrom());
        assertEquals(1474968981, result.getNextRecordsFrom().longValue());

        // Additional records, overflow indication since there is 1 record left
        // that was stored later than the last record that fits into the limit.
        OperationalDataRecordManager.setMaxRecordsInPayload(10);
        result = queryRecords(1474968960L, 1474968990L);
        assertEquals(25, result.size());
        assertNotNull(result.getNextRecordsFrom());
        assertEquals(1474968982L, result.getNextRecordsFrom().longValue());

        // Additional records, overflow indication since there is 1 record left
        // that was stored later than the last record that fits into the limit.
        OperationalDataRecordManager.setMaxRecordsInPayload(10);
        result = queryRecords(1474968981L, 1474968990L);
        assertEquals(17, result.size());
        assertNotNull(result.getNextRecordsFrom());
        assertEquals(1474968982L, result.getNextRecordsFrom().longValue());
    }

    @Test
    public void storeAndQueryDataFilteringByOutputFields() throws Exception {
        ClientId client = ClientId.Conf.create(
                "XTEE-CI-XM", "GOV", "00000001", "System1");

        storeFullOperationalDataRecords(1, 1474968960L);

        // Request by regular client, return the data
        // with securityServerInternalId as NULL.
        OperationalDataRecords result = queryRecords(1474968960L,
                1474968980L, client, null, Sets.newHashSet("requestInTs",
                        "securityServerInternalIp", "securityServerType"));

        assertEquals(1, result.size());

        OperationalDataRecord record = result.getRecords().get(0);

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
        result = queryRecords(1474968960L, 1474968980L, null, null,
                Sets.newHashSet("requestInTs",
                        "securityServerInternalIp", "securityServerType"));

        assertEquals(1, result.size());

        record = result.getRecords().get(0);

        assertEquals("192.168.3.250", record.getSecurityServerInternalIp());

        // The output spec has a single field that is null in DB.
        result = queryRecords(1474968960L, 1474968980L, client, null,
                Sets.newHashSet("faultCode"));
        assertEquals(1, result.size());

        final OperationalDataRecord r = result.getRecords().get(0);

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

        storeFullOperationalDataRecord(1474968960L, client, serviceProvider);
        storeFullOperationalDataRecord(1474968970L, client, serviceProvider);
        storeFullOperationalDataRecord(1474968980L, client, client);
        storeFullOperationalDataRecord(1474968980L, serviceProvider, client);
        storeFullOperationalDataRecord(1474968980L, serviceProvider,
                serviceProvider);
        storeFullOperationalDataRecord(1474968990L, client, member);
        storeFullOperationalDataRecord(1474968991L, member, member);
        storeFullOperationalDataRecord(1474968992L, member, client);

        // Known client
        OperationalDataRecords result = queryRecords(1474968960L, 1474968980L,
                client);
        assertEquals(4, result.size());

        // Unknown client
        result = queryRecords(1474968960L, 1474968980L, unknownClient);
        assertEquals(0, result.size());

        // Known client, known service provider
        result = queryRecords(1474968960L, 1474968980L, client, serviceProvider,
                new HashSet<>());
        assertEquals(2, result.size());

        // Known client, unknown service provider
        result = queryRecords(1474968960L, 1474968980L, client,
                unknownServiceProvider, new HashSet<>());
        assertEquals(0, result.size());

        // Unknown client, known service provider
        result = queryRecords(1474968960L, 1474968980L, unknownClient,
                serviceProvider, new HashSet<>());
        assertEquals(0, result.size());

        // Unknown client, unknown service provider
        result = queryRecords(1474968960L, 1474968980L, unknownClient,
                unknownServiceProvider, new HashSet<>());
        assertEquals(0, result.size());

        // Same client and service provider
        result = queryRecords(1474968960L, 1474968980L, client, client,
                new HashSet<>());
        assertEquals(2, result.size());

        // Known service provider
        result = queryRecords(1474968960L, 1474968980L, null, serviceProvider,
                new HashSet<>());
        assertEquals(3, result.size());

        // Unknown service provider
        result = queryRecords(1474968960L, 1474968980L, null,
                unknownServiceProvider, new HashSet<>());
        assertEquals(0, result.size());

        // Known member
        result = queryRecords(1474968990L, 1474968993L, member);
        assertEquals(3, result.size());

        // Known client, known service provider (member)
        result = queryRecords(1474968990L, 1474968993L, client, member,
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

        storeFullOperationalDataRecord(1474968960L, client, serviceProvider);
        storeFullOperationalDataRecord(1474968961L, client, serviceProvider);
        storeFullOperationalDataRecord(1474968962L, client, serviceProvider);
        storeFullOperationalDataRecord(1474968963L, client, serviceProvider);
        storeFullOperationalDataRecord(1474968964L, client, serviceProvider);
        storeFullOperationalDataRecord(1474968960L, serviceProvider, client);
        storeFullOperationalDataRecord(1474968961L, serviceProvider, client);
        storeFullOperationalDataRecord(1474968962L, serviceProvider, client);
        storeFullOperationalDataRecord(1474968963L, serviceProvider, client);
        storeFullOperationalDataRecord(1474968964L, serviceProvider, client);


        // Less than max records.
        OperationalDataRecordManager.setMaxRecordsInPayload(10);
        OperationalDataRecords result = queryRecords(1474968960L, 1474968980L);
        assertEquals(10, result.size());
        assertNull(result.getNextRecordsFrom());

        // Known client
        result = queryRecords(1474968960L, 1474968980L,
                client);
        assertEquals(10, result.size());

        // Known client, known service provider
        result = queryRecords(1474968960L, 1474968980L, client, serviceProvider,
                new HashSet<>());
        assertEquals(5, result.size());

        // Known client, known service provider
        result = queryRecords(1474968960L, 1474968980L, serviceProvider, client,
                new HashSet<>());
        assertEquals(5, result.size());

        // Result has more records than max
        // Filter by client and service provider
        OperationalDataRecordManager.setMaxRecordsInPayload(4);
        result = queryRecords(1474968960L, 1474968980L, client, serviceProvider,
                new HashSet<>());
        assertEquals(4, result.size());
        assertNotNull(result.getNextRecordsFrom());
        result = queryRecords(result.getNextRecordsFrom(), 1474968980L, client, serviceProvider,
                new HashSet<>());
        assertEquals(1, result.size());
    }

    @Test
    public void cleanupLogRecords() throws Exception {
        storeFullOperationalDataRecords(1, 1474968970L);
        storeFullOperationalDataRecords(1, 1474968980L);

        OperationalDataRecordCleaner.cleanRecords(Instant.ofEpochMilli(1474968975000L));

        OperationalDataRecords result = queryRecords(1474968960L, 1474968980L);

        assertEquals(1, result.size());
    }

    @Test
    public void stringTruncation() throws Exception {
        OperationalDataRecord record = OBJECT_READER.readValue(
                formatFullOperationalDataAsJson(), OperationalDataRecord.class);

        record.setMessageIssue(LONG_STRING);
        record.setFaultString(LONG_STRING);

        // test truncate on save
        storeRecords(Collections.singletonList(record), 1474968965L);

        OperationalDataRecords result = queryAllRecords();

        assertEquals(1, result.size());

        OperationalDataRecord resultRecord = result.getRecords().get(0);

        assertEquals(LONG_STRING.substring(0, 255),
                resultRecord.getMessageIssue());
        assertEquals(LONG_STRING, resultRecord.getFaultString());

        // test truncate on update
        resultRecord.setMessageIssue("2" + LONG_STRING);

        doInTransaction(session -> {
            session.saveOrUpdate(resultRecord);
            return null;
        });

        result = queryAllRecords();
        OperationalDataRecord updatedResultRecord = result.getRecords().get(0);

        assertEquals(("2" + LONG_STRING).substring(0, 255),
                updatedResultRecord.getMessageIssue());
    }
}
