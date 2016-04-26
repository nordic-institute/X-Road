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

import java.text.ParseException;
import java.util.Date;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.ria.xroad.asyncdb.messagequeue.CorruptQueueException;
import ee.ria.xroad.asyncdb.messagequeue.RequestInfo;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.common.identifier.ServiceId;

import static ee.ria.xroad.asyncdb.AsyncDBTestUtil.getDate;
import static ee.ria.xroad.asyncdb.AsyncDBTestUtil.getFirstSoapRequest;
import static org.junit.Assert.*;

/**
 * Tests for request info.
 */
public class RequestInfoBehavior {
    private static final Logger LOG = LoggerFactory
            .getLogger(RequestInfoBehavior.class);

    private static final long TIME_DELTA = 500; // ms

    private ClientId sender = ClientId.create("EE", "GOV", "sender");
    private ServiceId service = ServiceId.create(
            "EE", "GOV", "sender", null, "service");

    /**
     * Tests creation of request info.
     */
    @Test
    public void shouldGiveInitialDataBasedOnSoapRequest() {
        RequestInfo requestInfo = RequestInfo.getNew(0, getFirstSoapRequest());

        ClientId expectedSender = ClientId.create("EE", "BUSINESS",
                "clientmember");
        ServiceId expectedService = ServiceId.create("EE", "BUSINESS",
                "servicemember", null, "sendSomeAsyncStuff");

        assertEquals("1234567890", requestInfo.getId());
        assertEquals(new Date().getTime(),
                requestInfo.getReceivedTime().getTime(), TIME_DELTA);
        assertNull(requestInfo.getRemovedTime());
        assertEquals(expectedSender, requestInfo.getSender());
        assertEquals("EE37702211234", requestInfo.getUser());
        assertEquals(expectedService, requestInfo.getService());
    }

    /**
     * Tests if request info is marked sending properly.
     *
     * @throws ParseException - when date cannot be parsed. Should not happen.
     */
    @Test
    public void shouldMarkSendingProperly() throws ParseException {
        Date receivedTime = getDate("2013-04-18 11:22.33+0000");

        RequestInfo initial = new RequestInfo(0, "id",
                receivedTime, null, sender,
                "user", service);

        RequestInfo markedSending = RequestInfo.markSending(initial);

        assertEquals(0, markedSending.getOrderNo());
        assertEquals("id", markedSending.getId());
        assertEquals(receivedTime, markedSending.getReceivedTime());
        assertNull(markedSending.getRemovedTime());
        assertEquals(sender, markedSending.getSender());
        assertEquals("user", markedSending.getUser());
        assertEquals(service, markedSending.getService());
        assertTrue(markedSending.isSending());
    }

    /**
     * Tests clearing sending flag from request.
     *
     * @throws ParseException - when date cannot be parsed. Should not happen.
     */
    @Test
    public void shouldUnmarkSendingProperly() throws ParseException {
        Date receivedTime = getDate("2013-04-18 11:22.33+0000");
        RequestInfo initial = new RequestInfo(0, "id",
                receivedTime, null, sender,
                "user", service);

        RequestInfo markedSending = RequestInfo.markSending(initial);
        RequestInfo unmarkedSending = RequestInfo.unmarkSending(markedSending);

        assertEquals(0, unmarkedSending.getOrderNo());
        assertEquals("id", unmarkedSending.getId());
        assertEquals(receivedTime, unmarkedSending.getReceivedTime());
        assertNull(unmarkedSending.getRemovedTime());
        assertEquals(sender, unmarkedSending.getSender());
        assertEquals("user", unmarkedSending.getUser());
        assertEquals(service, unmarkedSending.getService());
        assertFalse(unmarkedSending.isSending());
    }

    /**
     * Tests handling removal of request.
     *
     * @throws ParseException - when date cannot be parsed. Should not happen.
     */
    @Test
    public void shouldMarkRequestAsRemoved() throws ParseException {
        Date receivedTime = getDate("2013-04-18 11:22.33+0000");
        RequestInfo initial = new RequestInfo(0, "id",
                receivedTime, null, sender,
                "user", service);

        RequestInfo markedAsRemoved = RequestInfo.markAsRemoved(initial);

        assertEquals(0, markedAsRemoved.getOrderNo());
        assertEquals("id", markedAsRemoved.getId());
        assertEquals(receivedTime, markedAsRemoved.getReceivedTime());
        assertNotNull(markedAsRemoved.getRemovedTime());
        assertEquals(sender, markedAsRemoved.getSender());
        assertEquals("user", markedAsRemoved.getUser());
        assertEquals(service, markedAsRemoved.getService());
        assertFalse(markedAsRemoved.isSending());
    }

    /**
     * Tests restoring of request.
     *
     * @throws ParseException - when date cannot be parsed. Should not happen.
     */
    @Test
    public void shouldRestoreRequest() throws ParseException {
        Date receivedTime = getDate("2013-04-18 11:22.33+0000");
        RequestInfo initial = new RequestInfo(0, "id",
                receivedTime, new Date(), sender,
                "user", service);

        RequestInfo markedAsRemoved = RequestInfo.restore(initial);

        assertEquals(0, markedAsRemoved.getOrderNo());
        assertEquals("id", markedAsRemoved.getId());
        assertEquals(receivedTime, markedAsRemoved.getReceivedTime());
        assertNull(markedAsRemoved.getRemovedTime());
        assertEquals(sender, markedAsRemoved.getSender());
        assertEquals("user", markedAsRemoved.getUser());
        assertEquals(service, markedAsRemoved.getService());
        assertFalse(markedAsRemoved.isSending());
    }

    /**
     * Tests transformations of Request<->JSON.
     *
     * @throws CorruptQueueException - when JSON malformed. Should not happen.
     */
    @Test
    public void shouldTurnRequestToAndFromJson() throws CorruptQueueException {
        RequestInfo requestInfo = new RequestInfo(0, "id", new Date(),
                null,  sender, "user", service);

        String json = requestInfo.toJson();
        LOG.debug("Request turned into JSON: '{}'", json);

        RequestInfo readBack = RequestInfo.fromJson(json);
        LOG.debug("Request info read back from JSON: '{}'", readBack);
    }

    /**
     * Tests reading in marked sending attribute from JSON.
     *
     * @throws CorruptQueueException - when JSON is malformed.
     */
    @Test
    public void shouldReadMarkedSendingAttributeFromJsonCorrectly()
            throws CorruptQueueException {
        String json = "{\"orderNo\":0,"
                + "\"id\":\"1234567890\","
                + "\"receivedTime\":1368442452094,"
                + "\"sender\":{"
                    + "\"memberClass\":\"BUSINESS\","
                    + "\"memberCode\":\"clientmember\","
                    + "\"type\":\"MEMBER\","
                    + "\"xRoadInstance\":\"EE\"},"
                + "\"user\":\"EE37702211234\","
                + "\"service\":{\"memberClass\":\"BUSINESS\","
                    + "\"memberCode\":\"servicemember\","
                    + "\"serviceCode\":\"sendSomeAsyncStuff\","
                    + "\"type\":\"SERVICE\","
                    + "\"xRoadInstance\":\"EE\"}"
                + ",\"sending\":true}";

        RequestInfo request = RequestInfo.fromJson(json);
        assertTrue(request.isSending());
    }

    /**
     * Tests handling malformed JSON describing message queue.
     *
     * @throws CorruptQueueException - indicator of success here.
     */
    @Test(expected = CorruptQueueException.class)
    public void shouldThrowCorruptQueueExceptionWhenReadMalformedJson()
            throws CorruptQueueException {
        // Given
        String malformedJson = "";

        // When/then
        RequestInfo.fromJson(malformedJson);
    }
}
