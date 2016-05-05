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

import java.io.IOException;
import java.util.List;

import org.junit.Before;
import org.junit.Test;

import ee.ria.xroad.asyncdb.messagequeue.MessageQueue;
import ee.ria.xroad.asyncdb.messagequeue.MessageQueueImpl;
import ee.ria.xroad.asyncdb.messagequeue.QueueInfo;
import ee.ria.xroad.asyncdb.messagequeue.RequestInfo;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

import static ee.ria.xroad.asyncdb.AsyncDBTestUtil.getDate;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Tests are intended for read-only operations.
 *
 * Data under 'src/test/resources/db' is used for tests.
 */
public class MessageQueueBehavior {
    private static final ClientId PROVIDER1 = ClientId.create("EE",
            "GOV", "provider1");
    private static final ClientId PROVIDER2 = ClientId.create("EE",
            "GOV", "provider2");

    private MessageQueue queue1;
    private MessageQueue queue2;

    /**
     * Sets up fixtures for testing message queue.
     *
     * @throws Exception - when setting up fixtures fails.
     */
    @Before
    public void setUp() throws Exception {
        // Let us do experiments in the sandbox
        System.setProperty(SystemProperties.ASYNC_DB_PATH,
                "src/test/resources/db");

        AsyncLogWriter dummyLogWriter = new AsyncLogWriter(null) {

            @Override
            public void appendToLog(RequestInfo requestInfo,
                    String lastSendResult,
                    int firstRequestSendCount) throws IOException {
                // Do nothing
            }
        };

        queue1 = new MessageQueueImpl(PROVIDER1,
                dummyLogWriter);
        queue2 = new MessageQueueImpl(PROVIDER2,
                dummyLogWriter);
    }

    /**
     * Tests getting provider metadata from queue.
     *
     * @throws Exception - when getting provider metadata fails.
     */
    @Test
    public void shouldGetProviderMetadataCorrectly() throws Exception {
        QueueInfo queueInfo2 = queue2.getQueueInfo();
        assertEquals(PROVIDER2, queueInfo2.getName());
        assertEquals(2, queueInfo2.getRequestCount());
        assertEquals(0, queueInfo2.getFirstRequestNo());
        assertEquals(getDate("2012-03-16 13:42.14+0000"),
                queueInfo2.getLastSentTime());
        assertEquals(0, queueInfo2.getFirstRequestSendCount());
        assertEquals("", queueInfo2.getLastSuccessId());
        assertNull(queueInfo2.getLastSuccessTime());
        assertEquals("", queueInfo2.getLastSendResult());

        QueueInfo queueInfo1 = queue1.getQueueInfo();
        assertEquals(PROVIDER1, queueInfo1.getName());
        assertEquals(1, queueInfo1.getRequestCount());
        assertEquals(2, queueInfo1.getFirstRequestNo());
        assertEquals(getDate("2013-04-11 13:22.11+0000"),
                queueInfo1.getLastSentTime());
        assertEquals(4, queueInfo1.getFirstRequestSendCount());
        assertEquals("ID111", queueInfo1.getLastSuccessId());
        assertEquals(getDate("2013-04-02 12:33.11+0000"),
                queueInfo1.getLastSuccessTime());
        assertEquals("tulemus", queueInfo1.getLastSendResult());
    }

    /**
     * Tests getting requests of provider.
     *
     * @throws Exception - when getting provider requests fails.
     */
    @Test
    public void shouldGetRequestsOfProviderCorrectly() throws Exception {
        List<RequestInfo> requests = queue2.getRequests();
        assertEquals(2, requests.size());

        RequestInfo request1 = requests.get(0);
        assertEquals(0, request1.getOrderNo());
        assertEquals("id0", request1.getId());
        assertEquals(getDate("2013-02-01 22:00.00+0000"),
                request1.getReceivedTime());
        assertNull(request1.getRemovedTime());

        ClientId expectedSender1 =
                ClientId.create("EE", "GOV", "sender1");
        assertEquals(expectedSender1, request1.getSender());
        assertEquals("222", request1.getUser());

        ServiceId service1 = ServiceId.create(
                "EE", "GOV", "sender1", null, "service1");
        assertEquals(service1, request1.getService());

        RequestInfo request2 = requests.get(1);
        assertEquals(1, request2.getOrderNo());
        assertEquals("id1", request2.getId());
        assertEquals(getDate("2013-03-03 10:30.11+0000"),
                request2.getReceivedTime());
        assertEquals(getDate("2013-03-15 12:05.22+0000"),
                request2.getRemovedTime());

        ClientId expectedSender2 =
                ClientId.create("EE", "GOV", "sender2");

        assertEquals(expectedSender2, request2.getSender());
        assertEquals("333", request2.getUser());

        ServiceId service2 = ServiceId.create(
                "EE", "GOV", "sender1", null, "service2");
        assertEquals(service2, request2.getService());
    }
}
