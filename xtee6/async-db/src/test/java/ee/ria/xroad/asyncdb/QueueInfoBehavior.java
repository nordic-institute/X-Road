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

import org.junit.Before;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ee.ria.xroad.asyncdb.messagequeue.CorruptQueueException;
import ee.ria.xroad.asyncdb.messagequeue.QueueInfo;
import ee.ria.xroad.asyncdb.messagequeue.QueueState;
import ee.ria.xroad.common.SystemProperties;
import ee.ria.xroad.common.identifier.ClientId;

import static org.junit.Assert.*;

/**
 * Tests for behavior of queue info.
 *
 * These tests depend on AsyncSenderConfTest.
 */
public class QueueInfoBehavior {
    private static final Logger LOG = LoggerFactory
            .getLogger(QueueInfoBehavior.class);

    private ClientId client = ClientId.create("EE", "GOV",
            AsyncDBTestUtil.getProviderName());

    /**
     * Sets up artifacts for testing queue info.
     *
     * @throws Exception - when setting up queue info artifacts fails.
     */
    @Before
    public void setUp() throws Exception {
        System.setProperty(SystemProperties.ASYNC_SENDER_CONFIGURATION_FILE,
                "src/test/resources/async-sender.properties");
    }

    /**
     * Testing creation of metadata when initial queue created.
     */
    @Test
    public void shouldGiveEmptyMetadataIfNewObjectCreated() {
        QueueInfo queueInfo = QueueInfo.getNew(client);

        assertEquals(0, queueInfo.getRequestCount());
        assertEquals(0, queueInfo.getFirstRequestNo());
        assertNull(queueInfo.getLastSentTime());
        assertEquals(0, queueInfo.getFirstRequestSendCount());
        assertEquals("", queueInfo.getLastSuccessId());
        assertNull(queueInfo.getLastSuccessTime());
        assertEquals("", queueInfo.getLastSendResult());
    }

    /**
     * Tests if next request number is zero when nothing in queue.
     */
    @Test
    public void shouldGiveCorrectNextRequestNoIfNoRequests() {
        QueueInfo metadata = new QueueInfo(
                client, new QueueState(0, 0, null, 0, null, null, null));

        assertEquals(0, metadata.getNextRequestNo());
    }

    /**
     * Tests if request number is handled correctly in normal situation.
     *
     * @throws Exception - thrown when getting request number fails.
     */
    @Test
    public void shouldGiveCorrectRequestNoIfRequestsPresent() throws Exception {

        QueueInfo queueInfo = new QueueInfo(
                client, new QueueState(4, 3, new Date(), 0, null, null, null));

        assertEquals(7, queueInfo.getNextRequestNo());
    }

    /**
     * Tests if next attempt is handled correctly.
     */
    @Test
    public void shouldGiveNextAttemptAlwaysAfterPrevious() {
        ClientId xts2Client = ClientId.create("EE", "GOV", "XTS2CLIENT");
        QueueInfo qInit = new QueueInfo(xts2Client,
                new QueueState(2000, 0, new Date(), 267808, null, null, "Jah"));

        assertTrue(qInit.getNextAttempt().getTime() > new Date().getTime());
    }

    /**
     * Tests handling request count.
     */
    @Test
    public void shouldIncreaseRequestCountWhenAddingRequest() {
        QueueInfo initial = new QueueInfo(
                client, new QueueState(0, 0, null, 0, null, null, null));
        QueueInfo finalProvider = QueueInfo.addRequest(initial);
        assertEquals(1, finalProvider.getRequestCount());
    }

    /**
     * Tests creating next attempt.
     *
     * @throws ParseException - when parsing next attempt fails.
     * Should not happen.
     */
    @Test
    public void shouldGiveNextAttemptCorrectly() throws ParseException {
        QueueInfo firstQueueInfo = getProviderWithSpecifiedFirstRequestSendCount(0);
        QueueInfo secondQueueInfo = getProviderWithSpecifiedFirstRequestSendCount(1);
        QueueInfo thirdQueueInfo = getProviderWithSpecifiedFirstRequestSendCount(3);
        QueueInfo forthQueueInfo = getProviderWithSpecifiedFirstRequestSendCount(5);

        // This test takes default values of 'basedelay' and 'maxdelay' into
        // consideration.
        Date firstExpectedNextAttempt = AsyncDBTestUtil
                .getDate("2012-04-17 11:00.00+0000");
        Date secondExpectedNextAttempt = AsyncDBTestUtil
                .getDate("2012-04-17 11:05.00+0000");
        Date thirdExpectedNextAttempt = AsyncDBTestUtil
                .getDate("2012-04-17 11:20.00+0000");
        Date forthExpectedNextAttempt = AsyncDBTestUtil
                .getDate("2012-04-17 11:30.00+0000");

        assertEquals(firstExpectedNextAttempt, firstQueueInfo.getNextAttempt());
        assertEquals(secondExpectedNextAttempt, secondQueueInfo.getNextAttempt());
        assertEquals(thirdExpectedNextAttempt, thirdQueueInfo.getNextAttempt());
        assertEquals(forthExpectedNextAttempt, forthQueueInfo.getNextAttempt());

        QueueInfo queueInfoWithoutLastSentTime = new QueueInfo(
                client, new QueueState(4, 3, null, 0, null, null, null));
        assertNotNull(queueInfoWithoutLastSentTime.getNextAttempt());

        QueueInfo queueInfoWithoutRequests = new QueueInfo(
                client, new QueueState(0, 0, new Date(), 0, null, null, null));
        assertNull(queueInfoWithoutRequests.getNextAttempt());
    }

    /**
     * Tests handling removal of first request.
     */
    @Test
    public void shouldRemoveFirstRequestProperly() {
        QueueInfo initial = new QueueInfo(
                client, new QueueState(2, 1, null, 7, null, null, null));

        String id = "456";
        String lastSendResult = "GREAT SUCCESS!";
        QueueInfo result = QueueInfo.removeFirstRequest(initial, id,
                lastSendResult);

        assertEquals(1, result.getRequestCount());
        assertEquals(2, result.getFirstRequestNo());
        assertNotNull(result.getLastSentTime());
        assertEquals(0, result.getFirstRequestSendCount());
        assertEquals(id, result.getLastSuccessId());
        assertEquals(lastSendResult, result.getLastSendResult());
        assertNotNull(result.getLastSuccessTime());
    }

    /**
     * Tests handling removal of corrupt request.
     */
    @Test
    public void shouldRemoveCorruptRequest() {
        // Given
        QueueInfo initial = new QueueInfo(
                client, new QueueState(2, 0, null, 7, null, null, null));

        // When
        QueueInfo result = QueueInfo.removeCorruptRequest(initial);

        // Then
        assertEquals(1, result.getRequestCount());
        assertEquals(1, result.getFirstRequestNo());
    }

    /**
     * Tests marking first request as removed.
     *
     * In this case lastSendResult should not change.
     */
    @Test
    public void shouldRemoveFirstRequestMarkedAsRemoved() {
        String initialLastSendResult = "GREAT SUCCESS!";
        QueueInfo initial = new QueueInfo(
                client,
                new QueueState(
                        2, 1, null, 7, null, null, initialLastSendResult));

        String id = "456";
        QueueInfo result = QueueInfo.removeFirstRequest(initial, id);

        assertEquals(1, result.getRequestCount());
        assertEquals(2, result.getFirstRequestNo());
        assertNotNull(result.getLastSentTime());
        assertEquals(0, result.getFirstRequestSendCount());
        assertEquals(id, result.getLastSuccessId());
        assertEquals(initialLastSendResult, result.getLastSendResult());
        assertNotNull(result.getLastSuccessTime());
    }

    /**
     * Tests handling removal of all requests.
     */
    @Test
    public void shouldSetFirstRequestNoToZeroIfAllRequestsAreRemoved() {
        QueueInfo initial = new QueueInfo(
                client, new QueueState(1, 8, null, 7, null, null, null));

        String id = "456";
        String lastSendResult = "EPIC FAILURE";
        QueueInfo result = QueueInfo.removeFirstRequest(initial, id,
                lastSendResult);

        assertEquals(0, result.getRequestCount());
        assertEquals(0, result.getFirstRequestNo());
        assertNotNull(result.getLastSentTime());
        assertEquals(0, result.getFirstRequestSendCount());
        assertEquals(id, result.getLastSuccessId());
        assertEquals(lastSendResult, result.getLastSendResult());
        assertNotNull(result.getLastSuccessTime());
    }

    /**
     * Tests handling removal of request from empty queue.
     */
    @Test(expected = IllegalArgumentException.class)
    public void shouldThrowExceptionWhenTryingToRemoveRequestFromEmptyQueue() {
        QueueInfo initial = new QueueInfo(
                client, new QueueState(0, 0, null, 0, null, null, null));

        QueueInfo.removeFirstRequest(initial, "id", null);
    }

    /**
     * Tests handling request sending failure.
     */
    @Test
    public void shouldIncreaseRequestCountForFailedRequest() {
        QueueInfo initial = new QueueInfo(
                client, new QueueState(4, 3, null, 0, null, new Date(), null));

        QueueInfo result = QueueInfo.handleFailedRequest(initial,
                "lastSendResult");

        assertEquals(4, result.getRequestCount());
        assertEquals(3, result.getFirstRequestNo());
        assertNotNull(result.getLastSentTime());
        assertEquals(1, result.getFirstRequestSendCount());
        assertEquals("", result.getLastSuccessId());
        assertNotNull(result.getLastSuccessTime());
    }

    /**
     * Tests resetting send count.
     */
    @Test
    public void shouldResetSendCount() {
        QueueInfo initial = new QueueInfo(
                client,
                new QueueState(4, 3, new Date(), 7, null, new Date(), null));

        QueueInfo result = QueueInfo.resetSendCount(initial);

        assertEquals(4, result.getRequestCount());
        assertEquals(3, result.getFirstRequestNo());
        assertNotNull(result.getLastSentTime());
        assertEquals(0, result.getFirstRequestSendCount());
        assertEquals("", result.getLastSuccessId());
        assertNotNull(result.getLastSuccessTime());

    }

    /**
     * Tests handling object<->JSON transformation of queue info.
     *
     * @throws CorruptQueueException - when transformation of queue fails.
     */
    @Test
    public void shouldTurnQueueToAndFromJson() throws CorruptQueueException {
        QueueInfo queueInfo = new QueueInfo(
                client,
                new QueueState(1, 0, new Date(), 0, "lastSuccessId", new Date(),
                        "Asi toimis!"));

        String json = queueInfo.toJson();
        LOG.debug("Queue turned into JSON: '{}'", json);

        QueueInfo readBack = QueueInfo.fromJson(json);
        LOG.debug("Queue info read back from JSON: '{}'", readBack);
    }

    /**
     * Tests handling malformed JSON.
     *
     * @throws CorruptQueueException - indicator of success here.
     */
    @Test(expected = CorruptQueueException.class)
    public void shouldThrowCorruptQueueExceptionWhenReadMalformedJson()
            throws CorruptQueueException {
        // Given
        String malformedJson = "";

        // When/then
        QueueInfo.fromJson(malformedJson);
    }

    private QueueInfo getProviderWithSpecifiedFirstRequestSendCount(
            int firstRequestSendCount) throws ParseException {
        Date lastSentTime = AsyncDBTestUtil.getDate("2012-04-17 11:00.00+0000");
        return new QueueInfo(
                client,
                new QueueState(
                        4, 3, lastSentTime, firstRequestSendCount, null, null,
                        null));
    }
}
