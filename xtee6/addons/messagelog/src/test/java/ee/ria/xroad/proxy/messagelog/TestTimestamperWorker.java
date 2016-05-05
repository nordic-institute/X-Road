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

import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.signature.TimestampVerifier;
import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampResponse;
import org.bouncycastle.tsp.TimeStampToken;

import java.util.List;

class TestTimestamperWorker extends TimestamperWorker {

    private static volatile Boolean shouldFail;

    TestTimestamperWorker(List<String> tspUrls) {
        super(tspUrls);
    }

    public static void failNextTimestamping(boolean failureExpected) {
        TestTimestamperWorker.shouldFail = failureExpected;
    }

    @Override
    protected AbstractTimestampRequest createSingleTimestampRequest(
            Long logRecord) {
        return new SingleTimestampRequest(logRecord) {
            @Override
            protected AbstractTimestampRequest.TsRequest makeTsRequest(TimeStampRequest req,
                    List<String> tspUrls) throws Exception {
                synchronized (shouldFail) {
                    if (shouldFail) {
                        shouldFail = false;
                        throw new RuntimeException("time-stamping failed");
                    }
                }

                return DummyTSP.makeRequest(req);
            }

            @Override
            protected void verify(TimeStampRequest request,
                    TimeStampResponse response) throws Exception {
                // do not validate against request

                TimeStampToken token = response.getTimeStampToken();
                TimestampVerifier.verify(token,
                        GlobalConf.getTspCertificates());
            }
        };
    }

    @Override
    protected AbstractTimestampRequest createBatchTimestampRequest(
            Long[] logRecords, String[] signatureHashes) {
        return new BatchTimestampRequest(logRecords, signatureHashes) {
            @Override
            protected AbstractTimestampRequest.TsRequest makeTsRequest(TimeStampRequest req,
                    List<String> tspUrls) throws Exception {
                synchronized (shouldFail) {
                    if (shouldFail) {
                        shouldFail = false;
                        throw new RuntimeException("time-stamping failed");
                    }
                }

                return DummyTSP.makeRequest(req);
            }

            @Override
            protected void verify(TimeStampRequest request,
                    TimeStampResponse response) throws Exception {
                // do not validate against request

                TimeStampToken token = response.getTimeStampToken();
                TimestampVerifier.verify(token,
                        GlobalConf.getTspCertificates());
            }
        };
    }
}
