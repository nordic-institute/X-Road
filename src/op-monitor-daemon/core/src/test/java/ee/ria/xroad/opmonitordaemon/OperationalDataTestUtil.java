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

import ee.ria.xroad.common.db.DatabaseCtxV2;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.opmonitoring.OpMonitoringData;
import ee.ria.xroad.common.util.JsonUtils;

import com.fasterxml.jackson.databind.ObjectReader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Utilities for the various levels of tests against
// OperationalMonitoringRecord that use the HSQLDB in-memory database.
final class OperationalDataTestUtil {
    static final ObjectReader OBJECT_READER = JsonUtils.getObjectReader();

    private OperationalDataTestUtil() {
    }

    static void prepareDatabase(DatabaseCtxV2 databaseCtx) throws Exception {
        databaseCtx.doInTransaction(session -> {
            var q = session.createNativeMutationQuery(
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

    static String formatInvalidOperationalDataAsJson() {
        return "{\"clientMemberCode\":\"00000001\","
                + "\"serviceXRoadInstance\":\"XTEE-CI-XM\","
                + "\"clientSubsystemCode\":\"System1\","
                + "\"monitoringDataTs\":1474968979,"
                + "\"serviceCode\":\"xroadGetRandom\","
                + "\"messageProtocolVersion\":\"4.0\","
                + "\"clientXRoadInstance\":\"XTEE-CI-XM\","
                + "\"clientMemberClass\":\"GOV\","
                + "\"serviceMemberCode\":\"00000000\","
                + "\"securityServerType\":\"INVALID_SERVER_TYPE\","
                + "\"securityServerInternalIp\":\"192.168.3.250\","
                + "\"serviceMemberClass\":\"GOV\","
                + "\"requestInTs\":14749689780000,"
                + "\"responseOutTs\":14749689790000,"
                + "\"serviceType\":\"WSDL\","
                + "\"succeeded\":false,"
                + "\"statusCode\":400}";
    }

    static String formatFullOperationalDataAsJson() {
        return "{\"clientMemberCode\":\"00000001\","
                + "\"serviceXRoadInstance\":\"XTEE-CI-XM\","
                + "\"clientSubsystemCode\":\"System1\","
                + "\"monitoringDataTs\":1474968979,"
                + "\"serviceCode\":\"xroadGetRandom\","
                + "\"messageProtocolVersion\":\"4.0\","
                + "\"messageId\":"
                + "\"c60b7e66-1dc8-4203-a3c1-3235661f6a84\","
                + "\"clientXRoadInstance\":\"XTEE-CI-XM\","
                + "\"messageUserId\":\"EE37702211230\","
                + "\"clientMemberClass\":\"GOV\","
                + "\"serviceMemberCode\":\"00000000\","
                + "\"securityServerType\":\"Client\","
                + "\"securityServerInternalIp\":\"192.168.3.250\","
                + "\"serviceVersion\":\"v1\","
                + "\"serviceMemberClass\":\"GOV\","
                + "\"requestInTs\":14749689780000,"
                + "\"serviceSubsystemCode\":\"Center\","
                + "\"responseOutTs\":14749689790000,"
                + "\"serviceType\":\"WSDL\","
                + "\"succeeded\":true,"
                + "\"statusCode\":200}";
    }

    static void storeFullOperationalDataRecords(int count,
                                                long monitoringDataTs,
                                                OperationalDataRecordManager operationalDataRecordManager) throws Exception {
        List<OperationalDataRecord> records = new ArrayList<>();
        OperationalDataRecord record;

        for (int i = 0; i < count; i++) {
            record = OBJECT_READER.readValue(formatFullOperationalDataAsJson(),
                    OperationalDataRecord.class);
            record.setMonitoringDataTs(monitoringDataTs);

            records.add(record);
        }

        operationalDataRecordManager.storeRecords(records, monitoringDataTs);
    }

    static void storeFullOperationalDataRecord(long monitoringDataTs,
                                               ClientId client, ClientId serviceProvider,
                                               OperationalDataRecordManager operationalDataRecordManager) throws Exception {
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

        operationalDataRecordManager.storeRecords(Collections.singletonList(record), monitoringDataTs);
    }
}
