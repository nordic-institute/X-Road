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
package org.niis.xroad.proxy.core.addon.opmonitoring;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.niis.xroad.opmonitor.api.OpMonitoringData;
import org.niis.xroad.opmonitor.api.OperationalDataRecordProto;

import java.time.Duration;
import java.util.List;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.niis.xroad.opmonitor.api.OpMonitoringData.SecurityServerType.PRODUCER;

class OpMonitoringDaemonSenderTest {
    private static final int DEFAULT_MAX_MESSAGE_SIZE = 3 * 1024 * 1024;

    private final RecordingOpMonitoringBuffer buffer = new RecordingOpMonitoringBuffer();
    private final FakeOperationalDataStoreClient client = new FakeOperationalDataStoreClient();
    private OpMonitoringDaemonSender sender;

    @AfterEach
    void tearDown() {
        sender.destroy();
    }

    @Test
    void batchIsStoredInOneCallAndReportedAsSent() {
        sender = new OpMonitoringDaemonSender(buffer, client, DEFAULT_MAX_MESSAGE_SIZE);
        var batch = records(3);

        sender.sendMessage(batch);

        awaitReady();
        assertThat(client.storedBatches()).containsExactly(protos(batch));
        assertThat(buffer.successes).containsExactly(3);
        assertThat(buffer.failures).isEmpty();
    }

    @Test
    void batchLargerThanByteCapIsSplitIntoCallsWithinTheCap() {
        var batch = records(10);
        int cap = FakeOperationalDataStoreClient.requestSize(protos(batch.subList(0, 3)));
        sender = new OpMonitoringDaemonSender(buffer, client, cap);

        sender.sendMessage(batch);

        awaitReady();
        assertThat(client.storedBatches()).hasSizeGreaterThan(1)
                .allSatisfy(call -> assertThat(FakeOperationalDataStoreClient.requestSize(call)).isLessThanOrEqualTo(cap));
        assertThat(client.storedRecords()).containsExactlyElementsOf(protos(batch));
        assertThat(buffer.successes).containsExactly(10);
    }

    @Test
    void ordinaryFailureReturnsOnlyUnconfirmedRecordsToTheBuffer() {
        var batch = records(9);
        int cap = FakeOperationalDataStoreClient.requestSize(protos(batch.subList(0, 3)));
        client.script(FakeOperationalDataStoreClient.Outcome.NORMAL, FakeOperationalDataStoreClient.Outcome.FAIL);
        sender = new OpMonitoringDaemonSender(buffer, client, cap);

        sender.sendMessage(batch);

        awaitReady();
        assertThat(buffer.successes).isEmpty();
        assertThat(buffer.failures).singleElement().isEqualTo(batch.subList(3, 9));
        assertThat(client.storedRecords()).containsExactlyElementsOf(protos(batch.subList(0, 3)));

        sender.sendMessage(buffer.failures.getFirst());

        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> buffer.successes.size() == 1);
        assertThat(client.storedRecords()).containsExactlyElementsOf(protos(batch));
    }

    @Test
    void responseLostAfterStoreIsRetriedSoTheChunkIsDeliveredAtLeastOnce() {
        var batch = records(2);
        client.script(FakeOperationalDataStoreClient.Outcome.STORE_THEN_FAIL);
        sender = new OpMonitoringDaemonSender(buffer, client, DEFAULT_MAX_MESSAGE_SIZE);

        sender.sendMessage(batch);

        awaitReady();
        assertThat(buffer.failures).singleElement().isEqualTo(batch);

        sender.sendMessage(buffer.failures.getFirst());

        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> buffer.successes.size() == 1);
        assertThat(client.storedRecords()).containsExactlyElementsOf(protos(List.of(batch.get(0), batch.get(1),
                batch.get(0), batch.get(1))));
    }

    @Test
    void tooLargeResponseIsRetriedInSmallerChunksUntilEverythingIsStored() {
        var batch = records(10);
        client.withMaxMessageSize(FakeOperationalDataStoreClient.requestSize(protos(batch.subList(0, 2))));
        sender = new OpMonitoringDaemonSender(buffer, client, DEFAULT_MAX_MESSAGE_SIZE);

        sender.sendMessage(batch);

        awaitReady();
        assertThat(client.storedRecords()).containsExactlyElementsOf(protos(batch));
        assertThat(buffer.successes).containsExactly(10);
        assertThat(buffer.failures).isEmpty();
    }

    @Test
    void recordsThatAreTooLargeEvenAloneAreDroppedAndNotRequeued() {
        var batch = records(3);
        client.withMaxMessageSize(1);
        sender = new OpMonitoringDaemonSender(buffer, client, DEFAULT_MAX_MESSAGE_SIZE);

        sender.sendMessage(batch);

        awaitReady();
        assertThat(client.storedRecords()).isEmpty();
        assertThat(buffer.failures).isEmpty();
        assertThat(buffer.successes).containsExactly(3);
    }

    @Test
    void recordRejectedAsTooLargeAloneIsDroppedAndTheRestOfTheBatchIsDelivered() {
        var batch = records(6);
        client.rejectAsTooLarge(record -> record.getMessageId().equals("message-2"));
        sender = new OpMonitoringDaemonSender(buffer, client, DEFAULT_MAX_MESSAGE_SIZE);

        sender.sendMessage(batch);

        awaitReady();
        assertThat(client.storedRecords()).containsExactlyElementsOf(
                protos(List.of(batch.get(0), batch.get(1), batch.get(3), batch.get(4), batch.get(5))));
        assertThat(buffer.failures).isEmpty();
        assertThat(buffer.successes).containsExactly(6);
    }

    @Test
    void ordinaryFailureAfterADroppedRecordReturnsOnlyTheUnsentRecords() {
        var batch = records(4);
        client.script(FakeOperationalDataStoreClient.Outcome.TOO_LARGE, FakeOperationalDataStoreClient.Outcome.TOO_LARGE,
                FakeOperationalDataStoreClient.Outcome.TOO_LARGE, FakeOperationalDataStoreClient.Outcome.NORMAL,
                FakeOperationalDataStoreClient.Outcome.FAIL);
        sender = new OpMonitoringDaemonSender(buffer, client, DEFAULT_MAX_MESSAGE_SIZE);

        sender.sendMessage(batch);

        awaitReady();
        assertThat(client.storedRecords()).containsExactlyElementsOf(protos(List.of(batch.get(1))));
        assertThat(buffer.failures).singleElement().isEqualTo(batch.subList(2, 4));
    }


    private static List<OperationalDataRecordProto> protos(List<OpMonitoringData> data) {
        return data.stream().map(OpMonitoringData::toProto).toList();
    }

    private void awaitReady() {
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> sender.isReady()
                && (!buffer.successes.isEmpty() || !buffer.failures.isEmpty()));
    }

    static List<OpMonitoringData> records(int count) {
        return IntStream.range(0, count).mapToObj(OpMonitoringDaemonSenderTest::record).toList();
    }

    static OpMonitoringData record(int index) {
        var data = new OpMonitoringData(PRODUCER, 1_700_000_000_000L + index);
        data.setSecurityServerInternalIp("10.0.0.1");
        data.setMessageId("message-" + index);
        data.setResponseOutTs(1_700_000_000_500L + index, false);
        return data;
    }
}
