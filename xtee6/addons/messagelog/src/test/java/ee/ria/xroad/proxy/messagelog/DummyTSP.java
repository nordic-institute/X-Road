package ee.ria.xroad.proxy.messagelog;

import java.io.FileInputStream;
import java.io.InputStream;

import org.bouncycastle.tsp.TimeStampRequest;

final class DummyTSP {

    private DummyTSP() {
    }

    static InputStream makeRequest(TimeStampRequest req) throws Exception {
        return new FileInputStream("src/test/resources/tsp.response");
    }

}
