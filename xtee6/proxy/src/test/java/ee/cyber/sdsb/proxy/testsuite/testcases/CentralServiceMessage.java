package ee.cyber.sdsb.proxy.testsuite.testcases;

import ee.cyber.sdsb.common.conf.GlobalConf;
import ee.cyber.sdsb.common.identifier.CentralServiceId;
import ee.cyber.sdsb.common.identifier.ServiceId;
import ee.cyber.sdsb.proxy.conf.KeyConf;
import ee.cyber.sdsb.proxy.conf.ServerConf;
import ee.cyber.sdsb.proxy.testsuite.Message;
import ee.cyber.sdsb.proxy.testsuite.MessageTestCase;
import ee.cyber.sdsb.proxy.testsuite.TestGlobalConf;
import ee.cyber.sdsb.proxy.testsuite.TestKeyConf;
import ee.cyber.sdsb.proxy.testsuite.TestServerConf;

/**
 * Sends message with central service.
 */
public class CentralServiceMessage extends MessageTestCase {

    public CentralServiceMessage() {
        requestFileName = "simple-centralservice.query";
        responseFileName = "simple-centralservice.answer";
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
}
