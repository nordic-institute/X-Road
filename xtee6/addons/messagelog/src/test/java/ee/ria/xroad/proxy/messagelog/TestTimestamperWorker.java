package ee.ria.xroad.proxy.messagelog;

import java.io.InputStream;
import java.util.List;

import org.bouncycastle.tsp.TimeStampRequest;
import org.bouncycastle.tsp.TimeStampResponse;
import org.bouncycastle.tsp.TimeStampToken;

import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.signature.TimestampVerifier;

class TestTimestamperWorker extends TimestamperWorker {

    private static volatile Boolean shouldFail;

    public TestTimestamperWorker(List<String> tspUrls) {
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
            protected InputStream makeTsRequest(TimeStampRequest req,
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
            protected InputStream makeTsRequest(TimeStampRequest req,
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
