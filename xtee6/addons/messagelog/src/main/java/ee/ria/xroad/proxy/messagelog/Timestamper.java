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

import java.io.Serializable;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;
import akka.actor.ActorRef;
import akka.actor.Props;
import akka.actor.UntypedActor;

import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.messagelog.MessageRecord;

/**
 * Timestamper is responsible for routing timestamping tasks to the
 * timestamp worker.
 */
@Slf4j
public class Timestamper extends UntypedActor {


    @Data
    @RequiredArgsConstructor
    @ToString(exclude = "signatureHashes")
    static final class TimestampTask implements Serializable {
        private final Long[] messageRecords;
        private final String[] signatureHashes;

        TimestampTask(MessageRecord messageRecord) {
            this.messageRecords = new Long[] {messageRecord.getId()};
            this.signatureHashes =
                    new String[] {messageRecord.getSignatureHash()};
        }
    }

    interface TimestampResult { }

    @Data
    @ToString(exclude = { "timestampDer", "hashChains" })
    static final class TimestampSucceeded implements TimestampResult, Serializable {
        private final Long[] messageRecords;
        private final byte[] timestampDer;
        private final String hashChainResult;
        private final String[] hashChains;
        private final String url;
    }

    @Data
    static final class TimestampFailed implements TimestampResult, Serializable {
        private final Long[] messageRecords;
        private final Exception cause;
    }

    @Override
    public void onReceive(Object message) throws Exception {
        log.trace("onReceive({})", message.getClass());

        if (message instanceof TimestampTask) {
            handleTimestampTask((TimestampTask) message);
        } else {
            unhandled(message);
        }
    }

    protected Class<? extends TimestamperWorker> getWorkerImpl() {
        return TimestamperWorker.class;
    }

    private void handleTimestampTask(TimestampTask message) {
        if (!GlobalConf.isValid()) {
            return;
        }

        // Spawn a new temporary child actor that will do the actual
        // time stamping, which is probably lengthy process.
        ActorRef worker = getContext().actorOf(
                Props.create(getWorkerImpl(), ServerConf.getTspUrl()));
        worker.tell(message, getSender());
    }
}
