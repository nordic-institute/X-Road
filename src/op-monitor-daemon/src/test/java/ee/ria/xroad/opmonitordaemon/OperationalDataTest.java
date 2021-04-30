/**
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

import com.fasterxml.jackson.databind.JsonMappingException;
import org.hibernate.PropertyValueException;
import org.hibernate.Session;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.Environment;
import org.hibernate.internal.util.config.ConfigurationHelper;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.io.IOException;

import static ee.ria.xroad.opmonitordaemon.OperationalDataTestUtil.OBJECT_READER;
import static ee.ria.xroad.opmonitordaemon.OperationalDataTestUtil.fillMinimalOperationalData;
import static ee.ria.xroad.opmonitordaemon.OperationalDataTestUtil.formatInvalidOperationalDataAsJson;
import static org.junit.Assert.assertEquals;

/**
 * Test cases related to operations with the operational monitoring database
 * at the level of the OperationalDataRecord class.
 */
public class OperationalDataTest extends BaseTestUsingDB {

    private Session session;

    @Rule
    public final ExpectedException expectedException = ExpectedException.none();

    /**
     * Begins a transaction.
     */
    @Before
    public void beginTransaction() {
        session = OpMonitorDaemonDatabaseCtx.get().beginTransaction();
    }

    /**
     * Rolls back the transaction after each test so we always start with an
     * empty database.
     */
    @After
    public void rollbackTransaction() {
        OpMonitorDaemonDatabaseCtx.get().rollbackTransaction();
    }

    @SuppressWarnings("squid:S2699")
    @Test
    public void storeOperationalData() {
        session.save(fillMinimalOperationalData());
    }

    @Test
    public void storeEmptyNonNullFields() {
        OperationalDataRecord rec = new OperationalDataRecord();
        // Note that monitoringDataTs will be automatically set to 0 (due to
        // indexing?) but the rest of the fields will be sent as NULL.

        expectedException.expect(PropertyValueException.class);
        session.save(rec);
    }

    @Test
    public void storeNegativeTimestamp() {
        OperationalDataRecord rec = new OperationalDataRecord();
        rec.setRequestInTs(1L);
        // Assuming the rest of the similar constraints work as expected.

        expectedException.expect(PropertyValueException.class);
        session.save(rec);
    }

    @Test
    public void useInvalidServerType() {
        OperationalDataRecord rec = new OperationalDataRecord();

        expectedException.expect(RuntimeException.class);
        rec.setSecurityServerType("INVALID_SERVER_TYPE");
    }

    @Test
    public void convertFromOutdatedJson() throws IOException {
        String jsonRec = formatInvalidOperationalDataAsJson();

        expectedException.expect(JsonMappingException.class);
        expectedException.expectMessage("Invalid value of securityServerType");

        OBJECT_READER.readValue(jsonRec, OperationalDataRecord.class);
    }

    @Test
    public void insertBulkData() {
        // Delete all the previous records and check if the batch_size
        // configuration parameter does not screw anything up.
        deleteAll();

        Configuration conf = new Configuration();
        int configuredBatchSize = ConfigurationHelper.getInt(
                Environment.STATEMENT_BATCH_SIZE, conf.getProperties(), -1);

        // Save the exact number of records that should go into one batch.
        // Flush to empty the internal cache of Hibernate.
        for (int i = 0; i < configuredBatchSize; i++) {
            OperationalDataRecord rec = fillMinimalOperationalData();
            session.save(rec);

            if ((i + 1) % configuredBatchSize == 0) {
                session.flush();
                session.clear();
            }
        }
        assertEquals(selectCount(), configuredBatchSize);

        // Save two batches and some more.
        for (int i = 0; i < 2 * configuredBatchSize + 4; i++) {
            OperationalDataRecord rec = fillMinimalOperationalData();
            session.save(rec);

            if ((i + 1) % configuredBatchSize == 0) {
                session.flush();
                session.clear();
            }
        }

        // Check that the expected number of records has been inserted.
        assertEquals(selectCount(), 3 * configuredBatchSize + 4);
    }

    private long selectCount() {
        return (Long) session.createQuery(
                "select count(*) from OperationalDataRecord")
                .uniqueResult();
    }

    private void deleteAll() {
        session.createQuery("delete from OperationalDataRecord").executeUpdate();
    }

}
