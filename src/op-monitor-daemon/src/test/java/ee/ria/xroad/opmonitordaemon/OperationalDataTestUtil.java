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

import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.opmonitoring.OpMonitoringData;
import ee.ria.xroad.common.util.JsonUtils;

import com.fasterxml.jackson.databind.ObjectReader;
import org.hibernate.query.Query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static ee.ria.xroad.opmonitordaemon.OpMonitorDaemonDatabaseCtx.doInTransaction;
import static ee.ria.xroad.opmonitordaemon.OperationalDataRecordManager.storeRecords;

// Utilities for the various levels of tests against
// OperationalMonitoringRecord that use the HSQLDB in-memory database.
final class OperationalDataTestUtil {
    static final ObjectReader OBJECT_READER = JsonUtils.getObjectReader();

    private OperationalDataTestUtil() { }

    static void prepareDatabase() throws Exception {
        System.setProperty(SystemProperties.DATABASE_PROPERTIES,
                "src/test/resources/hibernate.properties");
        doInTransaction(session -> {
            Query q = session.createNativeQuery(
                    // Completely wipe out the database. Assuming that HSQLDB
                    // is used for testing.
                    "TRUNCATE SCHEMA public AND COMMIT");
            q.executeUpdate();

            return null;
        });
    }

    static OperationalDataRecord fillMinimalOperationalData() {
        OperationalDataRecord rec = new OperationalDataRecord();
        rec.setMonitoringDataTs(1474381269L);
        rec.setSecurityServerInternalIp("192.168.56.101");
        rec.setSecurityServerType(OpMonitoringData.SecurityServerType.CLIENT.getTypeString());
        rec.setRequestInTs(14743812670000L);
        rec.setResponseOutTs(14743812680000L);
        rec.setSucceeded(true);

        return rec;
    }

    static String formatInvalidOperationalDataAsJson()  {
        return new StringBuilder()
                .append("{\"clientMemberCode\":\"00000001\",")
                .append("\"serviceXRoadInstance\":\"XTEE-CI-XM\",")
                .append("\"clientSubsystemCode\":\"System1\",")
                .append("\"monitoringDataTs\":1474968979,")
                .append("\"serviceCode\":\"xroadGetRandom\",")
                .append("\"messageProtocolVersion\":\"4.0\",")
                .append("\"clientXRoadInstance\":\"XTEE-CI-XM\",")
                .append("\"clientMemberClass\":\"GOV\",")
                .append("\"serviceMemberCode\":\"00000000\",")
                .append("\"securityServerType\":\"INVALID_SERVER_TYPE\",")
                .append("\"securityServerInternalIp\":\"192.168.3.250\",")
                .append("\"serviceMemberClass\":\"GOV\",")
                .append("\"requestInTs\":14749689780000,")
                .append("\"responseOutTs\":14749689790000,")
                .append("\"serviceType\":\"WSDL\",")
                .append("\"succeeded\":false,")
                .append("\"statusCode\":400}")
                .toString();
    }

    static String formatFullOperationalDataAsJson() {
        return new StringBuilder()
                .append("{\"clientMemberCode\":\"00000001\",")
                .append("\"serviceXRoadInstance\":\"XTEE-CI-XM\",")
                .append("\"clientSubsystemCode\":\"System1\",")
                .append("\"monitoringDataTs\":1474968979,")
                .append("\"serviceCode\":\"xroadGetRandom\",")
                .append("\"messageProtocolVersion\":\"4.0\",")
                .append("\"messageId\":")
                .append("\"c60b7e66-1dc8-4203-a3c1-3235661f6a84\",")
                .append("\"clientXRoadInstance\":\"XTEE-CI-XM\",")
                .append("\"messageUserId\":\"EE37702211230\",")
                .append("\"clientMemberClass\":\"GOV\",")
                .append("\"serviceMemberCode\":\"00000000\",")
                .append("\"securityServerType\":\"Client\",")
                .append("\"securityServerInternalIp\":\"192.168.3.250\",")
                .append("\"serviceVersion\":\"v1\",")
                .append("\"serviceMemberClass\":\"GOV\",")
                .append("\"requestInTs\":14749689780000,")
                .append("\"serviceSubsystemCode\":\"Center\",")
                .append("\"responseOutTs\":14749689790000,")
                .append("\"serviceType\":\"WSDL\",")
                .append("\"succeeded\":true,")
                .append("\"statusCode\":200}")
                .toString();
    }

    static void storeFullOperationalDataRecords(int count,
            long monitoringDataTs) throws Exception {
        List<OperationalDataRecord> records = new ArrayList<>();
        OperationalDataRecord record;

        for (int i = 0; i < count; i++) {
            record = OBJECT_READER.readValue(formatFullOperationalDataAsJson(),
                    OperationalDataRecord.class);
            record.setMonitoringDataTs(monitoringDataTs);

            records.add(record);
        }

        storeRecords(records, monitoringDataTs);
    }

    static void storeFullOperationalDataRecord(long monitoringDataTs,
            ClientId client, ClientId serviceProvider) throws Exception {
        OperationalDataRecord record = OBJECT_READER.readValue(
                formatFullOperationalDataAsJson(), OperationalDataRecord.class);

        record.setMonitoringDataTs(monitoringDataTs);

        record.setClientXRoadInstance(client.getXRoadInstance());
        record.setClientMemberClass(client.getMemberClass());
        record.setClientMemberCode(client.getMemberCode());
        record.setClientSubsystemCode(client.getSubsystemCode());

        record.setServiceXRoadInstance(serviceProvider.getXRoadInstance());
        record.setServiceMemberClass(serviceProvider.getMemberClass());
        record.setServiceMemberCode(serviceProvider.getMemberCode());
        record.setServiceSubsystemCode(serviceProvider.getSubsystemCode());

        storeRecords(Collections.singletonList(record), monitoringDataTs);
    }
}
