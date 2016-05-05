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
package ee.ria.xroad.asyncdb;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import ee.ria.xroad.asyncdb.messagequeue.RequestInfo;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Tests basic functionality of async log writer.
 */
public class AsyncLogWriterBehavior {
    private static final String ASYNC_LOG_DIR = "build";
    private static final String ASYNC_LOG_PATH = ASYNC_LOG_DIR + File.separator
            + AsyncLogWriter.ASYNC_LOG_FILENAME;

    // Fields in log file:
    private static final int FIELD_LOGGING_TIME = 0;
    private static final int FIELD_RECEIVED_TIME = 1;
    private static final int FIELD_REMOVED_TIME = 2;
    private static final int FIELD_SENDING_RESULT = 3;
    private static final int FIELD_FIRST_REQUEST_SEND_COUNT = 4;
    private static final int FIELD_PROVIDER_NAME = 5;
    private static final int FIELD_SENDER = 6;
    private static final int FIELD_USER = 7;
    private static final int FIELD_SERVICE = 8;
    private static final int FIELD_ID = 9;

    /**
     * Setting up fixtures for testing async log writer
     */
    @Before
    public void setUp() {
        System.setProperty(SystemProperties.LOG_PATH, ASYNC_LOG_DIR);
    }

    /**
     * Tests whether the entry is correctly written into async log.
     *
     * @throws Exception - when writing entry into async log fails.
     */
    @Test
    public void shouldWriteCorrectlyToAsyncLog() throws Exception {
        // Initialize
        String lastSendResult1 = "OK";
        int requestSendCount1 = 0;

        Date receivedTime1 = new Date(1292509011);

        ClientId client1 =
                ClientId.create("EE", "GOV", "client1");
        ServiceId service1 = ServiceId.create("EE", "GOV", "client1",
                null, "service1");

        RequestInfo request1 = new RequestInfo(0,
                "d41d8cd98f00b204e9800998ecf8427e", receivedTime1, null,
                client1, "EE27001010001", service1);

        AsyncLogWriter logWriter1 = new AsyncLogWriter(
                service1.getClientId());

        String lastSendResult2 = "NOK";
        int requestSendCount2 = 7;

        Date receivedTime2 = new Date(1292509311);
        Date removedTime2 = new Date(1292509321);

        ClientId client2 =
                ClientId.create("EE", "GOV", "client2");
        ServiceId service2 = ServiceId.create(
                "EE", "GOV", "client2", null, "service2");

        RequestInfo request2 = new RequestInfo(1,
                "tab\tnewline\nbackslash\\asdfs", receivedTime2,
                removedTime2, client2, "EE27001010002",
                service2);

        AsyncLogWriter logWriter2 = new AsyncLogWriter(
                service2.getClientId());

        // Write
        logWriter1.appendToLog(request1, lastSendResult1,
                requestSendCount1);

        logWriter2.appendToLog(request2, lastSendResult2,
                requestSendCount2);

        // Validate
        List<String> logFileLines = FileUtils
                .readLines(new File(ASYNC_LOG_PATH), StandardCharsets.UTF_8);

        assertEquals(2, logFileLines.size());

        String[] firstLineData = logFileLines.get(0)
                .split(Character.toString(AsyncLogWriter.FIELD_SEPARATOR));

        assertEquals(AsyncDBTestUtil.LOG_FILE_FIELDS, firstLineData.length);

        assertTrue(StringUtils.isNotBlank(firstLineData[FIELD_LOGGING_TIME]));
        assertEquals("1292509", firstLineData[FIELD_RECEIVED_TIME]);
        assertEquals("0", firstLineData[FIELD_REMOVED_TIME]);
        assertEquals("OK", firstLineData[FIELD_SENDING_RESULT]);
        assertEquals("0", firstLineData[FIELD_FIRST_REQUEST_SEND_COUNT]);
        assertEquals("EE/GOV/client1", firstLineData[FIELD_PROVIDER_NAME]);
        assertEquals(client1.toString(), firstLineData[FIELD_SENDER]);
        assertEquals("EE27001010001", firstLineData[FIELD_USER]);
        assertEquals(service1.toString(), firstLineData[FIELD_SERVICE]);
        assertEquals("d41d8cd98f00b204e9800998ecf8427e",
                firstLineData[FIELD_ID]);

        String[] secondLineData = logFileLines.get(1)
                .split(Character.toString(AsyncLogWriter.FIELD_SEPARATOR));

        assertEquals(AsyncDBTestUtil.LOG_FILE_FIELDS, secondLineData.length);

        assertTrue(StringUtils.isNotBlank(secondLineData[FIELD_LOGGING_TIME]));
        assertEquals("1292509", secondLineData[FIELD_RECEIVED_TIME]);
        assertEquals("1292509", secondLineData[FIELD_REMOVED_TIME]);
        assertEquals("NOK", secondLineData[FIELD_SENDING_RESULT]);
        assertEquals("7", secondLineData[FIELD_FIRST_REQUEST_SEND_COUNT]);
        assertEquals("EE/GOV/client2", secondLineData[FIELD_PROVIDER_NAME]);
        assertEquals(client2.toString(), secondLineData[FIELD_SENDER]);
        assertEquals("EE27001010002", secondLineData[FIELD_USER]);
        assertEquals(service2.toString(), secondLineData[FIELD_SERVICE]);

        String expectedSecondId = StringEscapeUtils
                .escapeJava("tab\tnewline\nbackslash\\asdfs");
        assertEquals(expectedSecondId, secondLineData[FIELD_ID]);
    }

    /**
     * Removes testing artifacts.
     */
    @After
    public void cleanUp() {
        FileUtils.deleteQuietly(new File(ASYNC_LOG_PATH));
    }
}
