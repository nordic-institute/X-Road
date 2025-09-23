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
package org.niis.xroad.proxy.core.messagelog;

import jakarta.xml.bind.JAXBException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.xml.security.signature.XMLSignatureException;
import org.niis.xroad.common.core.exception.XrdRuntimeException;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.proxy.core.messagelog.Timestamper.TimestampTask;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.niis.xroad.common.core.exception.ErrorCode.NO_LOG_RECORDS_SPECIFIED;
import static org.niis.xroad.common.core.exception.ErrorCode.NO_SIGNATURE_HASHES_SPECIFIED;
import static org.niis.xroad.common.core.exception.ErrorCode.NO_TIMESTAMPING_PROVIDER_FOUND;

/**
 * Timestamper worker is responsible for creating timestamps.
 */
@Slf4j
@RequiredArgsConstructor
public class TimestamperWorker {
    private final GlobalConfProvider globalConfProvider;
    private final List<String> tspUrls;

    public Timestamper.TimestampResult timestamp(TimestampTask message) {
        log.trace("timestamp({})", message.getClass());
        try {
            return handleTimestampTask(message);
        } catch (Exception e) {
            return handleFailure(message, e);
        }
    }

    private Timestamper.TimestampResult handleFailure(TimestampTask message, Exception e) {
        log.error("Timestamper failed for message records {}: {}",
                Arrays.toString(message.getMessageRecords()), e.getMessage());

        Timestamper.TimestampFailed timestampFailed = new Timestamper.TimestampFailed(message.getMessageRecords(), e);
        Map<String, Exception> errorsByUrl = new HashMap<>();
        for (String tspUrl : tspUrls) {
            errorsByUrl.put(tspUrl, e);
        }
        timestampFailed.setErrorsByUrl(errorsByUrl);
        return timestampFailed;
    }

    private Timestamper.TimestampResult handleTimestampTask(TimestampTask timestampTask)
            throws JAXBException, IOException, XMLSignatureException {
        if (tspUrls.isEmpty()) {
            throw XrdRuntimeException.systemException(NO_TIMESTAMPING_PROVIDER_FOUND)
                    .details("Cannot time-stamp, no TSP URLs configured")
                    .build();
        }

        Long[] logRecords = timestampTask.getMessageRecords();
        if (logRecords == null || logRecords.length == 0) {
            throw XrdRuntimeException.systemException(NO_LOG_RECORDS_SPECIFIED)
                    .details("Cannot time-stamp, no log records specified")
                    .build();
        }

        String[] signatureHashes = timestampTask.getSignatureHashes();
        if (signatureHashes == null
                || logRecords.length != signatureHashes.length) {
            throw XrdRuntimeException.systemException(NO_SIGNATURE_HASHES_SPECIFIED)
                    .details("Cannot time-stamp, no signature hashes specified")
                    .build();
        }

        long start = System.currentTimeMillis();

        AbstractTimestampRequest tsRequest = createTimestampRequest(logRecords, signatureHashes);

        Timestamper.TimestampResult result = tsRequest.execute(tspUrls);

        log.info("Timestamped {} timestampTask records in {} ms",
                timestampTask.getMessageRecords().length,
                (System.currentTimeMillis() - start));

        return result;
    }

    private AbstractTimestampRequest createTimestampRequest(Long[] logRecords,
                                                            String[] signatureHashes) {
        if (logRecords.length == 1) {
            log.debug("Creating regular time-stamp");

            return createSingleTimestampRequest(logRecords[0]);
        } else {
            log.debug("Creating batch time-stamp for {} hashes",
                    signatureHashes.length);

            return createBatchTimestampRequest(logRecords, signatureHashes);
        }
    }

    protected AbstractTimestampRequest createSingleTimestampRequest(Long logRecord) {
        return new SingleTimestampRequest(globalConfProvider, logRecord);
    }

    protected AbstractTimestampRequest createBatchTimestampRequest(Long[] logRecords, String[] signatureHashes) {
        return new BatchTimestampRequest(globalConfProvider, logRecords, signatureHashes);
    }

}
