package ee.cyber.sdsb.asyncdb;

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

import ee.cyber.sdsb.asyncdb.messagequeue.RequestInfo;
import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.ServiceId;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

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

    @Before
    public void setUp() {
        System.setProperty(SystemProperties.LOG_PATH, ASYNC_LOG_DIR);
    }

    @Test
    public void shouldWriteCorrectlyToAsyncLog() throws Exception {
        // Initialize
        String firstLastSendResult = "OK";
        int firstFirstRequestSendCount = 0;

        Date firstReceivedTime = new Date(1292509011);

        ClientId kirvetehas =
                ClientId.create("EE", "tankist", "kirvetehas");
        ServiceId tehasSalajane = ServiceId.create("EE", "tankist", "tehas",
                null, "salajane");

        RequestInfo firstRequest = new RequestInfo(0,
                "d41d8cd98f00b204e9800998ecf8427e", firstReceivedTime, null,
                kirvetehas, "EE27001010001", tehasSalajane);

        AsyncLogWriter firstLogWriter = new AsyncLogWriterImpl(
                tehasSalajane.getClientId());

        String secondLastSendResult = "NOK";
        int secondFirstRequestSendCount = 7;

        Date secondReceivedTime = new Date(1292509311);
        Date secondRemovedTime = new Date(1292509321);

        ClientId luuavabrik =
                ClientId.create("EE", "tankist", "luuavabrik");
        ServiceId vabrikSaladus = ServiceId.create(
                "EE", "tankist", "vabrik", null, "saladus");

        RequestInfo secondRequest = new RequestInfo(1,
                "tab\tnewline\nbackslash\\asdfs", secondReceivedTime,
                secondRemovedTime, luuavabrik, "EE27001010002",
                vabrikSaladus);

        AsyncLogWriter secondLogWriter = new AsyncLogWriterImpl(
                vabrikSaladus.getClientId());

        // Write
        firstLogWriter.appendToLog(firstRequest, firstLastSendResult,
                firstFirstRequestSendCount);

        secondLogWriter.appendToLog(secondRequest, secondLastSendResult,
                secondFirstRequestSendCount);

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
        assertEquals("EE/tankist/tehas", firstLineData[FIELD_PROVIDER_NAME]);
        assertEquals(kirvetehas.toString(), firstLineData[FIELD_SENDER]);
        assertEquals("EE27001010001", firstLineData[FIELD_USER]);
        assertEquals(tehasSalajane.toString(), firstLineData[FIELD_SERVICE]);
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
        assertEquals("EE/tankist/vabrik", secondLineData[FIELD_PROVIDER_NAME]);
        assertEquals(luuavabrik.toString(), secondLineData[FIELD_SENDER]);
        assertEquals("EE27001010002", secondLineData[FIELD_USER]);
        assertEquals(vabrikSaladus.toString(), secondLineData[FIELD_SERVICE]);

        String expectedSecondId = StringEscapeUtils
                .escapeJava("tab\tnewline\nbackslash\\asdfs");
        assertEquals(expectedSecondId, secondLineData[FIELD_ID]);
    }

    @After
    public void cleanUp() {
        FileUtils.deleteQuietly(new File(ASYNC_LOG_PATH));
    }
}

// Data used:

// First request

// req.receivedTime: 1292509011
// req.removedTime: 0
// = lastSendResult: OK
// =firstRequestSendCount: 0
// = queue.providerName: tehas
// req.sender: kirvetehas
// req.user: EE27001010001
// req.service: tehas.salajane
// req.id: d41d8cd98f00b204e9800998ecf8427e

// Second request

// req.receivedTime: 1292509311
// req.removedTime: 1292509321
// = lastSendResult: NOK
// =firstRequestSendCount: 7
// = queue.providerName: vabrik
// req.sender: luuavabrik
// req.user: EE27001010002
// req.service: vabrik.saladus
// req.id: tab\tnewline\nbackslash\\asdfs
