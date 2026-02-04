/*
 * The MIT License
 *
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
package org.niis.xroad.proxy.core.addon.messagelog;

import lombok.SneakyThrows;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampResponse;
import org.bouncycastle.tsp.TimeStampToken;
import org.niis.xroad.globalconf.GlobalConfProvider;
import org.niis.xroad.globalconf.impl.signature.TimestampVerifier;
import org.niis.xroad.proxy.core.configuration.ProxyMessageLogProperties;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.niis.xroad.proxy.core.addon.messagelog.TimestamperUtil.getTimestampResponse;

class TestTimestamperWorker extends TimestamperWorker {
    private static final Lock FALSE = new Lock(false);
    private static final Lock TRUE = new Lock(true);
    private static volatile boolean shouldFail;

    TestTimestamperWorker(GlobalConfProvider globalConfProvider, LogRecordManager logRecordManager,
                          ProxyMessageLogProperties messageLogProperties, List<String> tspUrls) {
        super(globalConfProvider, logRecordManager, messageLogProperties, tspUrls);
    }

    public static void failNextTimestamping(boolean failureExpected) {
        TestTimestamperWorker.shouldFail = failureExpected;
    }

    static Lock lock() {
        return shouldFail ? TRUE : FALSE;
    }

    @Override
    protected AbstractTimestampRequest createSingleTimestampRequest(Long logRecord) {
        return new SingleTimestampRequest(logRecordManager, messageLogProperties, globalConfProvider, logRecord) {
            @Override
            @SneakyThrows
            protected Timestamper.TimestampResult makeTsRequest(TimeStampRequest tsRequest, List<String> tspUrls) {
                Map<String, Exception> errorsByUrl = new HashMap<>();
                synchronized (lock()) {
                    if (shouldFail) {
                        shouldFail = false;

                        throw new RuntimeException("time-stamping failed");
                    }
                }
                TsRequest req = DummyTSP.makeRequest(tsRequest);

                TimeStampResponse tsResponse = getTimestampResponse(req.getInputStream());
                verify(tsRequest, tsResponse);

                Timestamper.TimestampResult result = result(tsResponse, req.getUrl());
                result.setErrorsByUrl(errorsByUrl);
                return result;
            }

            @Override
            protected void verify(TimeStampRequest request, TimeStampResponse response) {
                // do not validate against request

                TimeStampToken token = response.getTimeStampToken();
                TimestampVerifier.verify(token, globalConfProvider.getTspCertificates());
            }
        };
    }

    @Override
    protected AbstractTimestampRequest createBatchTimestampRequest(Long[] logRecords, String[] signatureHashes) {
        return new BatchTimestampRequest(globalConfProvider, messageLogProperties, logRecords, signatureHashes) {
            @Override
            @SneakyThrows
            protected Timestamper.TimestampResult makeTsRequest(TimeStampRequest tsRequest, List<String> tspUrls) {
                Map<String, Exception> errorsByUrl = new HashMap<>();
                synchronized (lock()) {
                    if (shouldFail) {
                        shouldFail = false;

                        throw new RuntimeException("time-stamping failed");
                    }
                }
                TsRequest req = DummyTSP.makeRequest(tsRequest);

                TimeStampResponse tsResponse = getTimestampResponse(req.getInputStream());
                verify(tsRequest, tsResponse);

                Timestamper.TimestampResult result = result(tsResponse, req.getUrl());
                result.setErrorsByUrl(errorsByUrl);
                return result;
            }

            @Override
            protected void verify(TimeStampRequest request, TimeStampResponse response) {
                // do not validate against request

                TimeStampToken token = response.getTimeStampToken();
                TimestampVerifier.verify(token, globalConfProvider.getTspCertificates());
            }
        };
    }

    record Lock(boolean value) {
    }
}
