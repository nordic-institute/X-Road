package ee.ria.xroad.proxy.testsuite.testcases;

import ee.ria.xroad.common.conf.globalconf.GlobalConf;
import ee.ria.xroad.common.conf.serverconf.ServerConf;
import ee.ria.xroad.common.identifier.CentralServiceId;
import ee.ria.xroad.common.identifier.ServiceId;
import ee.ria.xroad.proxy.conf.KeyConf;
import ee.ria.xroad.proxy.testsuite.Message;
import ee.ria.xroad.proxy.testsuite.MessageTestCase;
import ee.ria.xroad.proxy.testsuite.TestGlobalConf;
import ee.ria.xroad.proxy.testsuite.TestKeyConf;
import ee.ria.xroad.proxy.testsuite.TestServerConf;

/**
 * Sends message with central service.
 */
public class CentralServiceMessage extends MessageTestCase {

    /**
     * Constructs the test case.
     */
    public CentralServiceMessage() {
        requestFileName = "simple-centralservice.query";
        responseFile = "simple-centralservice.answer";
    }

    @Override
    protected void startUp() throws Exception {
        KeyConf.reload(new TestKeyConf());
        ServerConf.reload(new TestServerConf());

        GlobalConf.reload(new TestGlobalConf() {
            @Override
            public ServiceId getServiceId(CentralServiceId serviceId) {
                return ServiceId.create("EE", "BUSINESS", "producer", null,
                        serviceId.getServiceCode(), null);
            }
        });
    }

    @Override
    protected void validateNormalResponse(Message receivedResponse)
            throws Exception {
        // Normal response, nothing more to check here.
    }

    @Override
    protected void onReceiveRequest(Message receivedRequest) throws Exception {
        // Message inconsistency at this point is expected, since client
        // proxy will modify the request message
    }

}
