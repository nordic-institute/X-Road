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

import ee.ria.xroad.common.opmonitoring.StoreOpMonitoringDataResponse;
import ee.ria.xroad.common.util.JsonUtils;

import com.fasterxml.jackson.databind.ObjectWriter;
import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static ee.ria.xroad.opmonitordaemon.OperationalDataTestUtil.OBJECT_READER;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Tests operational data records payload.
 */
public class OperationalDataRecordsTest {

    private static final ObjectWriter OBJECT_WRITER = JsonUtils.getObjectWriter();
    private static final String RECORDS_JSON = "{\"records\":["
            + "{\"serviceXRoadInstance\":\"CS\","
            + "\"serviceType\":\"REST\","
            + "\"clientSubsystemCode\":\"Client\","
            + "\"serviceCode\":\"trains\","
            + "\"serviceSecurityServerAddress\":\"ss1\","
            + "\"xRequestId\":\"8a2f2e10-8ffb-40c3-b446-aebd23fd9248\","
            + "\"requestAttachmentCount\":0,"
            + "\"requestOutTs\":1619523069332,"
            + "\"serviceSubsystemCode\":\"Server\","
            + "\"responseAttachmentCount\":0,"
            + "\"clientMemberCode\":\"1111\","
            + "\"responseInTs\":1619523069691,"
            + "\"messageProtocolVersion\":\"1\","
            + "\"messageId\":\"CS-20e25621-059f-45cd-bd65-323424d071b3\","
            + "\"responseSize\":59089,"
            + "\"clientXRoadInstance\":\"CS\","
            + "\"clientMemberClass\":\"ORG\","
            + "\"serviceMemberCode\":\"1111\","
            + "\"securityServerType\":\"Producer\","
            + "\"securityServerInternalIp\":\"172.19.0.2\","
            + "\"serviceMemberClass\":\"ORG\","
            + "\"requestInTs\":1619523069297,"
            + "\"clientSecurityServerAddress\":\"ss1\","
            + "\"requestSize\":344,"
            + "\"responseOutTs\":1619523069721,"
            + "\"succeeded\":true,"
            + "\"messageUserId\": \"xrd\","
            + "\"messageIssue\": \"issue\","
            + "\"serviceVersion\": \"v1\","
            + "\"statusCode\":200}]}";

    /**
     * Test empty records payload.
     * @throws Exception if an error occurs.
     */
    @Test
    public void emptyRecordsPayload() throws Exception {
        List<OperationalDataRecord> recordList = new ArrayList<>();
        OperationalDataRecords records = new OperationalDataRecords(recordList);

        recordList.add(new OperationalDataRecord());
        recordList.add(new OperationalDataRecord());

        assertEquals("{\"records\":[{},{}]}", records.getPayload(OBJECT_WRITER));
    }

    @Test
    public void deserializeErrorResponse() throws IOException {
        String errorJson = "{\"errorMessage\": \"Error Message\"}";
        //String json = "{\"status\":\"Error\", \"errorMessage\": \"Error Message\"}";
        StoreOpMonitoringDataResponse response = OBJECT_READER
                .readValue(errorJson, StoreOpMonitoringDataResponse.class);
        assertEquals("Error", response.getStatus());
        assertNotNull(response.getErrorMessage());
    }

    @Test
    public void deserializeOkResponse() throws IOException {
        String okJson = "{\"status\": \"OK\"}";
        StoreOpMonitoringDataResponse response = OBJECT_READER
                .readValue(okJson, StoreOpMonitoringDataResponse.class);
        assertEquals("OK", response.getStatus());
        assertNull(response.getErrorMessage());
    }

    /**
     * Test that Jackson deserializes the record correctly
     * @throws IOException if deserializing fails
     */
    @Test
    public void deserializeRecords() throws IOException {
        OperationalDataRecords records = OBJECT_READER.readValue(RECORDS_JSON, OperationalDataRecords.class);
        OperationalDataRecord record = records.getRecords().get(0);

        assertEquals("CS", record.getServiceXRoadInstance());
        assertEquals("REST", record.getServiceType());
        assertEquals("Client", record.getClientSubsystemCode());
        assertEquals("trains", record.getServiceCode());
        assertEquals("ss1", record.getServiceSecurityServerAddress());
        assertEquals("8a2f2e10-8ffb-40c3-b446-aebd23fd9248", record.getXRequestId());
        assertEquals(0, record.getRequestAttachmentCount().intValue());
        assertEquals(1619523069332L, record.getRequestOutTs().longValue());
        assertEquals("Server", record.getServiceSubsystemCode());
        assertEquals(0, record.getResponseAttachmentCount().intValue());
        assertEquals("1111", record.getClientMemberCode());
        assertEquals(1619523069691L, record.getResponseInTs().longValue());
        assertEquals("1", record.getMessageProtocolVersion());
        assertEquals("CS-20e25621-059f-45cd-bd65-323424d071b3", record.getMessageId());
        assertEquals(59089L, record.getResponseSize().longValue());
        assertEquals("CS", record.getClientXRoadInstance());
        assertEquals("ORG", record.getClientMemberClass());
        assertEquals("1111", record.getServiceMemberCode());
        assertEquals("Producer", record.getSecurityServerType());
        assertEquals("172.19.0.2", record.getSecurityServerInternalIp());
        assertEquals("ORG", record.getServiceMemberClass());
        assertEquals(1619523069297L, record.getRequestInTs().longValue());
        assertEquals("ss1", record.getClientSecurityServerAddress());
        assertEquals(344L, record.getRequestSize().longValue());
        assertEquals(1619523069721L, record.getResponseOutTs().longValue());
        assertEquals("issue", record.getMessageIssue());
        assertEquals("xrd", record.getMessageUserId());
        assertEquals("v1", record.getServiceVersion());
        assertEquals(200, record.getStatusCode().intValue());
        assertTrue(record.getSucceeded());
    }
}
