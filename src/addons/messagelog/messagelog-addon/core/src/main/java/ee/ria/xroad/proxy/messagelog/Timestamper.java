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
package ee.ria.xroad.proxy.messagelog;

import ee.ria.xroad.common.CodedException;
import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.messagelog.MessageRecord;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;

import static ee.ria.xroad.common.ErrorCodes.X_OUTDATED_GLOBALCONF;

/**
 * Timestamper is responsible for routing timestamping tasks to the timestamp worker.
 */
@Slf4j
public class Timestamper {

    @Data
    @RequiredArgsConstructor
    @ToString(exclude = "signatureHashes")
    static final class TimestampTask implements Serializable {
        private final Long[] messageRecords;
        private final String[] signatureHashes;

        TimestampTask(MessageRecord messageRecord) {
            this.messageRecords = new Long[]{messageRecord.getId()};
            this.signatureHashes = new String[]{messageRecord.getSignatureHash()};
        }
    }

    interface TimestampResult {
    }

    @Data
    @ToString(exclude = {"timestampDer", "hashChains"})
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

    protected TimestamperWorker getWorkerImpl() {
        return new TimestamperWorker(ServerConf.getTspUrl());
    }

    public TimestampResult handleTimestampTask(TimestampTask message) {
        if (!GlobalConf.isValid()) {
            return new TimestampFailed(message.getMessageRecords(),
                    new CodedException(X_OUTDATED_GLOBALCONF, "Global configuration is not valid"));
        }

        return getWorkerImpl().timestamp(message);
    }
}
