package ee.ria.xroad.proxy.testsuite.testcases;

import java.util.Arrays;
import java.util.Collection;

import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.identifier.ClientId;
import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.SslMessageTestCase;
import ee.ria.xroad.proxy.testsuite.TestGlobalConf;

/**
 * This actually tests, if the only responsive hostname is selected from the
 * given hostnames.
 */
public class SslSelectFastestProxy extends SslMessageTestCase {

    /**
     * Constructs the test case.
     */
    public SslSelectFastestProxy() {
        requestFileName = "getstate.query";
        responseFile = "getstate.answer";
    }

    @Override
    protected void startUp() throws Exception {
        super.startUp();

        GlobalConf.reload(new TestGlobalConf() {
            @Override
            public Collection<String> getProviderAddress(ClientId provider) {
                return Arrays.asList("foobar", "localhost", "1.0.0.1");
            }
        });
    }

    @Override
    protected void validateNormalResponse(Message receivedResponse)
            throws Exception {
        // Normal response, nothing more to check here.
    }
}
