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
package ee.ria.xroad.proxy.messagelog;

import akka.actor.ActorRef;
import akka.actor.UntypedActor;
import ee.ria.xroad.proxy.messagelog.Timestamper.TimestampTask;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.Arrays;
import java.util.List;

/**
 * Timestamper worker is responsible for creating timestamps.
 */
@Slf4j
@RequiredArgsConstructor
public class TimestamperWorker extends UntypedActor {

    private final List<String> tspUrls;

    @Override
    public void onReceive(Object message) throws Exception {
        log.trace("onReceive({})", message.getClass());

        if (message instanceof TimestampTask) {
            try {
                handleTimestampTask((TimestampTask) message);
            } catch (Exception e) {
                handleFailure((TimestampTask) message, e);
            } finally {
                getContext().stop(getSelf());
            }
        } else {
            unhandled(message);
        }
    }

    private void handleFailure(TimestampTask message, Exception e) {
        log.error("Timestamper failed for message records {}: {}",
                Arrays.toString(message.getMessageRecords()), e.getMessage());

        getSender().tell(new Timestamper.TimestampFailed(
                message.getMessageRecords(), e), ActorRef.noSender());
    }

    private void handleTimestampTask(TimestampTask message) throws Exception {
        if (tspUrls.isEmpty()) {
            throw new RuntimeException(
                    "Cannot time-stamp, no TSP URLs configured");
        }

        Long[] logRecords = message.getMessageRecords();
        if (logRecords == null || logRecords.length == 0) {
            throw new RuntimeException(
                    "Cannot time-stamp, no log records specified");
        }

        String[] signatureHashes = message.getSignatureHashes();
        if (signatureHashes == null
                || logRecords.length != signatureHashes.length) {
            throw new RuntimeException(
                    "Cannot time-stamp, no signature hashes specified");
        }

        long start = System.currentTimeMillis();

        AbstractTimestampRequest tsRequest =
                createTimestampRequest(logRecords, signatureHashes);

        Timestamper.TimestampResult result = tsRequest.execute(tspUrls);

        log.info("Timestamped {} message records in {} ms",
                message.getMessageRecords().length,
                (System.currentTimeMillis() - start));

        getSender().tell(result, ActorRef.noSender());
    }

    private AbstractTimestampRequest createTimestampRequest(Long[] logRecords,
            String[] signatureHashes) throws Exception {
        if (logRecords.length == 1) {
            log.debug("Creating regular time-stamp");

            return createSingleTimestampRequest(logRecords[0]);
        } else {
            log.debug("Creating batch time-stamp for {} hashes",
                    signatureHashes.length);

            return createBatchTimestampRequest(logRecords, signatureHashes);
        }
    }

    protected AbstractTimestampRequest createSingleTimestampRequest(
            Long logRecord) {
        return new SingleTimestampRequest(logRecord);
    }

    protected AbstractTimestampRequest createBatchTimestampRequest(
            Long[] logRecords, String[] signatureHashes) {
        return new BatchTimestampRequest(logRecords, signatureHashes);
    }

}
