package ee.cyber.sdsb.asyncdb;

import java.text.ParseException;
import java.util.Date;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.common.identifier.ServiceId;

import static org.junit.Assert.*;

public class RequestInfoBehavior {
    private static final Logger LOG = LoggerFactory
            .getLogger(RequestInfoBehavior.class);

    private static final long TIME_DELTA = 500; // ms

    private ClientId sender = ClientId.create("EE", "tankist", "sender");
    private ServiceId service = ServiceId.create(
            "EE", "tankist", "sender", null, "service");

    @Test
    public void shouldGiveInitialDataBasedOnSoapRequest() {
        RequestInfo requestInfo = RequestInfo.getNew(0, AsyncDBTestUtil
                .getFirstSoapRequest());

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

    @Test
    public void shouldMarkSendingProperly() throws ParseException {
        Date receivedTime = AsyncDBTestUtil.getDate("2013-04-18 11:22.33");

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

    @Test
    public void shouldUnmarkSendingProperly() throws ParseException {
        Date receivedTime = AsyncDBTestUtil.getDate("2013-04-18 11:22.33");
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

    @Test
    public void shouldMarkRequestAsRemoved() throws ParseException {
        Date receivedTime = AsyncDBTestUtil.getDate("2013-04-18 11:22.33");
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

    @Test
    public void shouldRestoreRequest() throws ParseException {
        Date receivedTime = AsyncDBTestUtil.getDate("2013-04-18 11:22.33");
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

    @Test
    public void shouldTurnRequestToAndFromJson() {
        RequestInfo requestInfo = new RequestInfo(0, "id", new Date(),
                null,  sender, "user", service);

        String json = requestInfo.toJson();
        LOG.debug("Request turned into JSON: '{}'", json);

        RequestInfo readBack = RequestInfo.fromJson(json);
        LOG.debug("Request info read back from JSON: '{}'", readBack);
    }

    @Test
    public void shouldReadMarkedSendingAttributeFromJsonCorrectly() {
        String json = "{\"orderNo\":0," +
                "\"id\":\"1234567890\"," +
                "\"receivedTime\":1368442452094," +
                "\"sender\":{" +
                    "\"memberClass\":\"BUSINESS\"," +
                    "\"memberCode\":\"clientmember\"," +
                    "\"type\":\"MEMBER\"," +
                    "\"sdsbInstance\":\"EE\"}," +
                "\"user\":\"EE37702211234\"," +
                "\"service\":{\"memberClass\":\"BUSINESS\"," +
                    "\"memberCode\":\"servicemember\"," +
                    "\"serviceCode\":\"sendSomeAsyncStuff\"," +
                    "\"type\":\"SERVICE\"," +
                    "\"sdsbInstance\":\"EE\"}" +
                ",\"sending\":true}";

        RequestInfo request = RequestInfo.fromJson(json);
        assertTrue(request.isSending());
    }
}
