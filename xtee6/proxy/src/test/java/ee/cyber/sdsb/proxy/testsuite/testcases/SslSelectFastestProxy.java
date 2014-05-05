package ee.cyber.sdsb.proxy.testsuite.testcases;

import java.util.Arrays;
import java.util.Collection;

import ee.cyber.sdsb.common.conf.GlobalConf;
import ee.cyber.sdsb.common.identifier.ClientId;
import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.SslMessageTestCase;
import ee.cyber.sdsb.proxy.testsuite.TestGlobalConf;

/**
 * This actually tests, if the only responsive hostname is selected from the
 * given hostnames.
 */
public class SslSelectFastestProxy extends SslMessageTestCase {
    public SslSelectFastestProxy() {
        requestFileName = "getstate.query";
        responseFileName = "getstate.answer";
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
