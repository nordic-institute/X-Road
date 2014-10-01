package ee.cyber.sdsb.asyncdb;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import ee.cyber.sdsb.asyncdb.messagequeue.MessageQueue;
import ee.cyber.sdsb.asyncdb.messagequeue.MessageQueueImpl;
import ee.cyber.sdsb.asyncdb.messagequeue.QueueInfo;
import ee.cyber.sdsb.asyncdb.messagequeue.RequestInfo;
import ee.cyber.sdsb.common.SystemProperties;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.ServiceId;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Tests are intended for read-only operations.
 *
 * Data under 'src/test/resources/db' is used for tests.
 */
public class MessageQueueBehavior {
    private static final ClientId PROVIDER_SEENEVABRIK = ClientId.create("EE",
            "klass", "seenevabrik");
    private static final ClientId PROVIDER_NAHATEHAS = ClientId.create("EE",
            "klass", "nahatehas");

    private MessageQueue queueSeenevabrik;
    private MessageQueue queueNahatehas;

    @Before
    public void setUp() throws Exception {
        // Let us do experiments in the sandbox
        System.setProperty(SystemProperties.ASYNC_DB_PATH,
                "src/test/resources/db");

        AsyncLogWriter dummyLogWriter = new AsyncLogWriter() {

            @Override
            public void appendToLog(RequestInfo requestInfo,
                    String lastSendResult,
                    int firstRequestSendCount) throws IOException {
                // Do nothing
            }
        };

        queueSeenevabrik = new MessageQueueImpl(PROVIDER_SEENEVABRIK,
                dummyLogWriter);
        queueNahatehas = new MessageQueueImpl(PROVIDER_NAHATEHAS,
                dummyLogWriter);
    }

    @Test
    public void shouldGetProviderMetadataCorrectly() throws Exception {
        QueueInfo nahatehas = queueNahatehas.getQueueInfo();
        assertEquals(PROVIDER_NAHATEHAS, nahatehas.getName());
        assertEquals(2, nahatehas.getRequestCount());
        assertEquals(0, nahatehas.getFirstRequestNo());
        assertEquals(getDate("2012-03-16 15:42.14"),
                nahatehas.getLastSentTime());
        assertEquals(0, nahatehas.getFirstRequestSendCount());
        assertEquals("", nahatehas.getLastSuccessId());
        assertNull(nahatehas.getLastSuccessTime());
        assertEquals("", nahatehas.getLastSendResult());

        QueueInfo seenevabrik = queueSeenevabrik.getQueueInfo();
        assertEquals(PROVIDER_SEENEVABRIK, seenevabrik.getName());
        assertEquals(1, seenevabrik.getRequestCount());
        assertEquals(2, seenevabrik.getFirstRequestNo());
        assertEquals(getDate("2013-04-11 16:22.11"),
                seenevabrik.getLastSentTime());
        assertEquals(4, seenevabrik.getFirstRequestSendCount());
        assertEquals("ID111", seenevabrik.getLastSuccessId());
        assertEquals(getDate("2013-04-02 15:33.11"),
                seenevabrik.getLastSuccessTime());
        assertEquals("tulemus", seenevabrik.getLastSendResult());
    }

    @Test
    public void shouldGetRequestsOfProviderCorrectly() throws Exception {
        List<RequestInfo> requests = queueNahatehas.getRequests();
        assertEquals(2, requests.size());

        RequestInfo firstRequest = requests.get(0);
        assertEquals(0, firstRequest.getOrderNo());
        assertEquals("id0", firstRequest.getId());
        assertEquals(getDate("2013-02-02 00:00.00"),
                firstRequest.getReceivedTime());
        assertNull(firstRequest.getRemovedTime());

        ClientId expectedSenderVladislav =
                ClientId.create("EE", "klass", "vladislav");
        assertEquals(expectedSenderVladislav, firstRequest.getSender());
        assertEquals("222", firstRequest.getUser());

        ServiceId serviceLasePeeneks = ServiceId.create(
                "EE", "klass", "nahatehas", null, "lasePeeneks");
        assertEquals(serviceLasePeeneks, firstRequest.getService());

        RequestInfo secondRequest = requests.get(1);
        assertEquals(1, secondRequest.getOrderNo());
        assertEquals("id1", secondRequest.getId());
        assertEquals(getDate("2013-03-03 12:30.11"),
                secondRequest.getReceivedTime());
        assertEquals(getDate("2013-03-15 14:05.22"),
                secondRequest.getRemovedTime());

        ClientId expectedSenderVitja =
                ClientId.create("EE", "klass", "vitja");

        assertEquals(expectedSenderVitja, secondRequest.getSender());
        assertEquals("333", secondRequest.getUser());

        ServiceId serviceSalajaneInfo = ServiceId.create(
                "EE", "klass", "nahatehas", null, "salajaneInfo");
        assertEquals(serviceSalajaneInfo, secondRequest.getService());
    }

    private static Date getDate(String dateAsString) throws ParseException {
        DateFormat sdf = new SimpleDateFormat(AsyncDBTestUtil.DATE_FORMAT);
        return sdf.parse(dateAsString);
    }
}
